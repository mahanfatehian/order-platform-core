package com.orderprocessing.orderservice.tax;

import com.orderprocessing.orderservice.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaxInvoiceIssuer {
    private final TaxInvoiceRepository invoices;

    public TaxInvoiceIssuer(TaxInvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional
    public TaxInvoice issueIfAbsent(Order order) {
        return invoices.findByOrderId(order.getId()).orElseGet(() -> invoices.save(from(order)));
    }

    @Transactional(readOnly = true)
    public Optional<TaxInvoice> findOwned(UUID orderId, UUID userId) {
        return invoices.findByOrderId(orderId).filter(invoice -> userId.equals(invoice.getUserId()));
    }

    public TaxInvoiceView view(TaxInvoice invoice) {
        return new TaxInvoiceView(invoice.getId(), invoice.getOrderId(), invoice.getGoodsMinor(),
                invoice.getRateBps(), invoice.getTaxMinor(), invoice.getChargedMinor());
    }

    private static TaxInvoice from(Order order) {
        long tax = order.getTaxAmountMinor() == null ? 0L : order.getTaxAmountMinor();
        long charged = order.getWalletAmountMinor() == null ? tax : order.getWalletAmountMinor();
        long goods = Math.max(0L, charged - tax);
        TaxInvoice invoice = new TaxInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrderId(order.getId());
        invoice.setUserId(order.getUserId());
        invoice.setGoodsMinor(goods);
        invoice.setRateBps(order.getTaxRateBps() == null ? 0 : order.getTaxRateBps());
        invoice.setTaxMinor(tax);
        invoice.setChargedMinor(charged);
        invoice.setIssuedAt(Instant.now());
        return invoice;
    }
}
