package com.orderprocessing.userservice.config;

import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.repository.RoleRepository;
import com.orderprocessing.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevDataInitializerTest {

    @Test
    void createsDedicatedWarehouseAndDeliveryDemoIdentities() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        RoleEntity userRole = role("ROLE_USER");
        RoleEntity adminRole = role("ROLE_ADMIN");
        RoleEntity warehouseRole = role("ROLE_WAREHOUSE");
        RoleEntity deliveryRole = role("ROLE_DELIVERY");
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(roles.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roles.findByName("ROLE_WAREHOUSE")).thenReturn(Optional.of(warehouseRole));
        when(roles.findByName("ROLE_DELIVERY")).thenReturn(Optional.of(deliveryRole));
        when(users.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));

        new DevDataInitializer(users, roles, encoder).run(null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users, times(4)).save(captor.capture());
        List<UserEntity> seededUsers = captor.getAllValues();
        UserEntity warehouse = find(seededUsers, "warehouse_worker");
        assertThat(warehouse.getPasswordHash()).isEqualTo("encoded:" + DevDataInitializer.WAREHOUSE_PASSWORD);
        assertThat(warehouse.getRoles()).extracting(RoleEntity::getName).containsExactly("ROLE_WAREHOUSE");
        UserEntity delivery = find(seededUsers, "delivery_driver");
        assertThat(delivery.getPasswordHash()).isEqualTo("encoded:" + DevDataInitializer.DELIVERY_PASSWORD);
        assertThat(delivery.getRoles()).extracting(RoleEntity::getName).containsExactly("ROLE_DELIVERY");
    }

    private RoleEntity role(String name) {
        return RoleEntity.builder().name(name).build();
    }

    private UserEntity find(List<UserEntity> users, String username) {
        return users.stream()
                .filter(user -> username.equals(user.getUsername()))
                .findFirst()
                .orElseThrow();
    }
}
