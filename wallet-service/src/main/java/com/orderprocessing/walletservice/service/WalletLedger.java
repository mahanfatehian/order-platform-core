package com.orderprocessing.walletservice.service;

import com.orderprocessing.walletservice.exception.WalletException;
import com.orderprocessing.walletservice.model.EntryType;
import com.orderprocessing.walletservice.model.Hold;
import com.orderprocessing.walletservice.model.HoldStatus;
import com.orderprocessing.walletservice.model.LedgerEntry;
import com.orderprocessing.walletservice.model.ProcessedCommand;
import com.orderprocessing.walletservice.model.Wallet;
import com.orderprocessing.walletservice.repository.HoldRepository;
import com.orderprocessing.walletservice.repository.LedgerEntryRepository;
import com.orderprocessing.walletservice.repository.ProcessedCommandRepository;
import com.orderprocessing.walletservice.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletLedger {

    private final WalletRepository wallets;
    private final LedgerEntryRepository entries;
    private final HoldRepository holds;
    private final ProcessedCommandRepository commands;

    public WalletLedger(WalletRepository wallets, LedgerEntryRepository entries, HoldRepository holds,
                        ProcessedCommandRepository commands) {
        this.wallets = wallets;
        this.entries = entries;
        this.holds = holds;
        this.commands = commands;
    }

    @Transactional
    public LedgerEntry credit(UUID userId, long amount, String reason, String idempotencyKey,
                              String actorId, String actorRole, String correlationId) {
        requirePositive(amount);
        requireText(reason, "reason");
        requireText(idempotencyKey, "idempotencyKey");
        String hash = hash("CREDIT", userId, amount, reason);
        Optional<LedgerEntry> replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Wallet wallet = wallets.findByUserIdForUpdate(userId).orElseGet(() -> createWallet(userId));
        return post(wallet, EntryType.CREDIT, amount, idempotencyKey, hash, actorId, actorRole, reason,
                correlationId, null, null, null);
    }

    @Transactional
    public LedgerEntry placeHold(UUID userId, UUID orderId, long amount, String idempotencyKey, String correlationId) {
        requirePositive(amount);
        requireText(idempotencyKey, "idempotencyKey");
        if (orderId == null) {
            throw WalletException.badRequest("ORDER_REQUIRED", "A hold needs an order id");
        }
        String hash = hash("HOLD", userId, orderId, amount);
        Optional<LedgerEntry> replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Wallet wallet = wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> WalletException.notFound("WALLET_NOT_FOUND", "No wallet for this user"));
        replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (available(wallet.getId()) < amount) {
            throw WalletException.conflict("INSUFFICIENT_FUNDS", "Available balance is short");
        }
        Hold hold = new Hold();
        hold.setId(UUID.randomUUID());
        hold.setWalletId(wallet.getId());
        hold.setOrderId(orderId);
        hold.setAmount(amount);
        hold.setStatus(HoldStatus.OPEN);
        hold.setIdempotencyKey(idempotencyKey);
        hold.setCreatedAt(Instant.now());
        holds.save(hold);
        return post(wallet, EntryType.HOLD, amount, idempotencyKey, hash, "SYSTEM", "SYSTEM", "hold",
                correlationId, orderId, hold.getId(), null);
    }

    @Transactional
    public LedgerEntry captureHold(UUID holdId, String idempotencyKey, String correlationId) {
        requireText(idempotencyKey, "idempotencyKey");
        Hold hold = holds.findById(holdId)
                .orElseThrow(() -> WalletException.notFound("HOLD_NOT_FOUND", "Hold does not exist"));
        String hash = hash("CAPTURE", holdId, hold.getAmount());
        Optional<LedgerEntry> replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Wallet wallet = lock(hold.getWalletId());
        hold = holds.findById(holdId).orElseThrow();
        replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (hold.getStatus() == HoldStatus.CAPTURED) {
            return entries.findByHoldIdAndEntryType(hold.getId(), EntryType.CAPTURE)
                    .orElseThrow(() -> WalletException.conflict("CAPTURE_MISSING", "Captured hold has no entry"));
        }
        if (hold.getStatus() != HoldStatus.OPEN) {
            throw WalletException.conflict("HOLD_NOT_OPEN", "Only an open hold can be captured");
        }
        hold.setStatus(HoldStatus.CAPTURED);
        holds.save(hold);
        return post(wallet, EntryType.CAPTURE, hold.getAmount(), idempotencyKey, hash, "SYSTEM", "SYSTEM", "capture",
                correlationId, hold.getOrderId(), hold.getId(), null);
    }

    @Transactional
    public LedgerEntry releaseHold(UUID holdId, String idempotencyKey, String correlationId) {
        requireText(idempotencyKey, "idempotencyKey");
        Hold hold = holds.findById(holdId)
                .orElseThrow(() -> WalletException.notFound("HOLD_NOT_FOUND", "Hold does not exist"));
        String hash = hash("RELEASE", holdId, hold.getAmount());
        Optional<LedgerEntry> replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        lock(hold.getWalletId());
        hold = holds.findById(holdId).orElseThrow();
        replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (hold.getStatus() == HoldStatus.RELEASED) {
            return entries.findByHoldIdAndEntryType(hold.getId(), EntryType.RELEASE)
                    .orElseThrow(() -> WalletException.conflict("RELEASE_MISSING", "Released hold has no entry"));
        }
        if (hold.getStatus() != HoldStatus.OPEN) {
            throw WalletException.conflict("HOLD_NOT_OPEN", "Only an open hold can be released");
        }
        hold.setStatus(HoldStatus.RELEASED);
        holds.save(hold);
        Wallet wallet = wallets.findById(hold.getWalletId()).orElseThrow();
        return post(wallet, EntryType.RELEASE, hold.getAmount(), idempotencyKey, hash, "SYSTEM", "SYSTEM", "release",
                correlationId, hold.getOrderId(), hold.getId(), null);
    }

    @Transactional
    public LedgerEntry refundCapture(UUID captureEntryId, String idempotencyKey, String correlationId) {
        requireText(idempotencyKey, "idempotencyKey");
        LedgerEntry capture = entries.findById(captureEntryId)
                .orElseThrow(() -> WalletException.notFound("CAPTURE_NOT_FOUND", "Capture does not exist"));
        if (capture.getEntryType() != EntryType.CAPTURE) {
            throw WalletException.conflict("NOT_A_CAPTURE", "Refund requires a capture entry");
        }
        String hash = hash("REFUND", captureEntryId, capture.getAmount());
        Optional<LedgerEntry> replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Wallet wallet = lock(capture.getWalletId());
        replay = replay(idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (entries.findByRelatedEntryIdAndEntryType(capture.getId(), EntryType.REFUND).isPresent()) {
            throw WalletException.conflict("ALREADY_REFUNDED", "This capture was already refunded");
        }
        return post(wallet, EntryType.REFUND, capture.getAmount(), idempotencyKey, hash, "SYSTEM", "SYSTEM", "refund",
                correlationId, capture.getOrderId(), capture.getHoldId(), capture.getId());
    }

    @Transactional(readOnly = true)
    public Balance balance(UUID userId) {
        Wallet wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> WalletException.notFound("WALLET_NOT_FOUND", "No wallet for this user"));
        long booked = entries.booked(wallet.getId());
        long open = holds.sumAmountByWalletAndStatus(wallet.getId(), HoldStatus.OPEN);
        return new Balance(wallet.getUserId(), wallet.getCurrency(), booked, booked - open, open);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entries(UUID userId) {
        Wallet wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> WalletException.notFound("WALLET_NOT_FOUND", "No wallet for this user"));
        return entries.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }

    public long available(UUID walletId) {
        long booked = entries.booked(walletId);
        long open = holds.sumAmountByWalletAndStatus(walletId, HoldStatus.OPEN);
        return booked - open;
    }

    private Wallet lock(UUID walletId) {
        return wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> WalletException.notFound("WALLET_NOT_FOUND", "No wallet for this hold"));
    }

    private Wallet createWallet(UUID userId) {
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setUserId(userId);
        wallet.setCurrency(Wallet.CURRENCY);
        wallet.setCreatedAt(Instant.now());
        return wallets.save(wallet);
    }

    private LedgerEntry post(Wallet wallet, EntryType type, long amount, String idempotencyKey, String hash,
                             String actorId, String actorRole, String reason, String correlationId,
                             UUID orderId, UUID holdId, UUID relatedEntryId) {
        LedgerEntry entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setWalletId(wallet.getId());
        entry.setEntryType(type);
        entry.setAmount(amount);
        entry.setCurrency(Wallet.CURRENCY);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setActorId(actorId == null || actorId.isBlank() ? "SYSTEM" : actorId);
        entry.setActorRole(actorRole == null || actorRole.isBlank() ? "SYSTEM" : actorRole);
        entry.setReason(reason);
        entry.setCorrelationId(correlationId == null || correlationId.isBlank() ? idempotencyKey : correlationId);
        entry.setOrderId(orderId);
        entry.setHoldId(holdId);
        entry.setRelatedEntryId(relatedEntryId);
        entry.setCreatedAt(Instant.now());
        entries.save(entry);
        ProcessedCommand command = new ProcessedCommand();
        command.setIdempotencyKey(idempotencyKey);
        command.setRequestHash(hash);
        command.setEntryId(entry.getId());
        command.setCreatedAt(entry.getCreatedAt());
        commands.save(command);
        return entry;
    }

    private Optional<LedgerEntry> replay(String idempotencyKey, String hash) {
        return commands.findById(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw WalletException.conflict("IDEMPOTENCY_CONFLICT", "This key was already used for a different command");
            }
            return entries.findById(existing.getEntryId())
                    .orElseThrow(() -> WalletException.conflict("ENTRY_MISSING", "Processed command has no entry"));
        });
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw WalletException.badRequest("AMOUNT_NOT_POSITIVE", "Amount must be a positive number of rials");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw WalletException.badRequest("MISSING_" + field.toUpperCase(), field + " is required");
        }
    }

    private static String hash(String type, Object... parts) {
        StringBuilder body = new StringBuilder(type);
        for (Object part : parts) {
            body.append('|').append(part);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record Balance(UUID userId, String currency, long booked, long available, long openHolds) {
    }
}
