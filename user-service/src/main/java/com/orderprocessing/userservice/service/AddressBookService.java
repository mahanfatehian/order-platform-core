package com.orderprocessing.userservice.service;

import com.orderprocessing.userservice.dto.AddressRequest;
import com.orderprocessing.userservice.dto.AddressResponse;
import com.orderprocessing.userservice.entity.AddressEntity;
import com.orderprocessing.userservice.exception.ResourceNotFoundException;
import com.orderprocessing.userservice.repository.AddressRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AddressBookService {

    static final String COUNTRY = "IR";

    private final AddressRepository addressRepository;

    public AddressBookService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID userId, UUID addressId) {
        return toResponse(requireOwned(userId, addressId));
    }

    @Transactional
    public AddressResponse create(UUID userId, AddressRequest request) {
        requireIran(request.getCountry());
        if (request.isDefaultAddress()) {
            addressRepository.clearDefault(userId);
            addressRepository.flush();
        }
        AddressEntity entity = new AddressEntity();
        entity.setUserId(userId);
        apply(entity, request);
        return toResponse(save(entity));
    }

    @Transactional
    public AddressResponse update(UUID userId, UUID addressId, AddressRequest request) {
        requireIran(request.getCountry());
        AddressEntity entity = requireOwned(userId, addressId);
        if (request.isDefaultAddress()) {
            addressRepository.clearDefault(userId);
            addressRepository.flush();
        }
        apply(entity, request);
        return toResponse(save(entity));
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        addressRepository.delete(requireOwned(userId, addressId));
    }

    private AddressEntity requireOwned(UUID userId, UUID addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private void requireIran(String country) {
        if (!COUNTRY.equals(country)) {
            throw new IllegalArgumentException("Country must be IR");
        }
    }

    private AddressEntity save(AddressEntity entity) {
        try {
            return addressRepository.save(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("Only one default address is allowed");
        }
    }

    private void apply(AddressEntity entity, AddressRequest request) {
        entity.setRecipient(request.getRecipient().trim());
        entity.setLine1(request.getLine1().trim());
        entity.setLine2(blankToNull(request.getLine2()));
        entity.setCity(request.getCity().trim());
        entity.setRegion(request.getRegion().trim());
        entity.setPostalCode(request.getPostalCode().trim());
        entity.setPhone(request.getPhone().trim());
        entity.setCountry(COUNTRY);
        entity.setDefaultAddress(request.isDefaultAddress());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AddressResponse toResponse(AddressEntity entity) {
        return new AddressResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getRecipient(),
                entity.getLine1(),
                entity.getLine2(),
                entity.getCity(),
                entity.getRegion(),
                entity.getPostalCode(),
                entity.getPhone(),
                entity.getCountry(),
                entity.isDefaultAddress());
    }
}
