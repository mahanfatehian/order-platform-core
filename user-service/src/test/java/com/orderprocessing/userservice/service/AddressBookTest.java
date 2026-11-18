package com.orderprocessing.userservice.service;

import com.orderprocessing.userservice.dto.AddressRequest;
import com.orderprocessing.userservice.dto.AddressResponse;
import com.orderprocessing.userservice.entity.AddressEntity;
import com.orderprocessing.userservice.exception.ResourceNotFoundException;
import com.orderprocessing.userservice.repository.AddressRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressBookTest {

    private final AddressRepository repository = mock(AddressRepository.class);
    private final AddressBookService service = new AddressBookService(repository);
    private final UUID userId = UUID.randomUUID();

    @Test
    void aCountryOtherThanIrIsRejectedAndStoresNothing() {
        assertThatThrownBy(() -> service.create(userId, request("DE", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IR");
        assertThatThrownBy(() -> service.update(userId, UUID.randomUUID(), request("US", true)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).clearDefault(any());
        verify(repository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void aSecondDefaultAddressClearsTheFirst() {
        List<AddressEntity> stored = new ArrayList<>();
        when(repository.save(any())).thenAnswer(invocation -> {
            AddressEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            stored.removeIf(existing -> existing.getId().equals(entity.getId()));
            stored.add(entity);
            return entity;
        });
        when(repository.clearDefault(userId)).thenAnswer(invocation -> {
            stored.forEach(entity -> entity.setDefaultAddress(false));
            return 1;
        });

        AddressResponse first = service.create(userId, request("IR", true));
        AddressResponse second = service.create(userId, request("IR", true));

        InOrder order = inOrder(repository);
        order.verify(repository).clearDefault(userId);
        order.verify(repository).flush();
        order.verify(repository).save(any());
        assertThat(stored).filteredOn(AddressEntity::isDefaultAddress).hasSize(1);
        assertThat(stored).filteredOn(AddressEntity::isDefaultAddress)
                .extracting(AddressEntity::getId)
                .containsExactly(second.id());
        assertThat(first.defaultAddress()).isTrue();
        assertThat(stored).filteredOn(entity -> entity.getId().equals(first.id()))
                .allSatisfy(entity -> assertThat(entity.isDefaultAddress()).isFalse());
    }

    @Test
    void updateAndDeleteTouchOnlyTheOwnersRow() {
        UUID addressId = UUID.randomUUID();
        when(repository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, addressId, request("IR", false)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(userId, addressId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void editingThenDeletingRemovesOnlyThatAddressBookRow() {
        UUID addressId = UUID.randomUUID();
        AddressEntity entity = owned(addressId);
        when(repository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse edited = service.update(userId, addressId, request("IR", false));
        service.delete(userId, addressId);

        assertThat(edited.recipient()).isEqualTo("Ada Lovelace");
        assertThat(edited.city()).isEqualTo("Tehran");
        verify(repository).delete(entity);
    }

    private AddressEntity owned(UUID addressId) {
        AddressEntity entity = new AddressEntity();
        entity.setId(addressId);
        entity.setUserId(userId);
        entity.setRecipient("Before");
        entity.setLine1("1 First");
        entity.setCity("Shiraz");
        entity.setRegion("Fars");
        entity.setPostalCode("7135712345");
        entity.setPhone("02100000000");
        entity.setCountry("IR");
        return entity;
    }

    private AddressRequest request(String country, boolean defaultAddress) {
        AddressRequest request = new AddressRequest();
        request.setRecipient("Ada Lovelace");
        request.setLine1("12 Enghelab");
        request.setLine2("Unit 4");
        request.setCity("Tehran");
        request.setRegion("Tehran");
        request.setPostalCode("1136912345");
        request.setPhone("02112345678");
        request.setCountry(country);
        request.setDefaultAddress(defaultAddress);
        return request;
    }
}
