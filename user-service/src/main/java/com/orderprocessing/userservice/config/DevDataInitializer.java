package com.orderprocessing.userservice.config;

import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.exception.ResourceNotFoundException;
import com.orderprocessing.userservice.repository.RoleRepository;
import com.orderprocessing.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    // These credentials exist only when the dev profile is active. Keep them
    // deterministic so the repository's guided demo can hand work between
    // customer, warehouse, delivery, and administrator personas.
    public static final String CUSTOMER_PASSWORD = "Customer123!";
    public static final String ADMIN_PASSWORD = "Admin123!";
    public static final String WAREHOUSE_PASSWORD = "WarehouseDemo2026!";
    public static final String DELIVERY_PASSWORD = "DeliveryDemo2026!";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        RoleEntity userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("ROLE_USER is required for dev data"));
        RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new ResourceNotFoundException("ROLE_ADMIN is required for dev data"));
        RoleEntity warehouseRole = roleRepository.findByName("ROLE_WAREHOUSE")
                .orElseThrow(() -> new ResourceNotFoundException("ROLE_WAREHOUSE is required for dev data"));
        RoleEntity deliveryRole = roleRepository.findByName("ROLE_DELIVERY")
                .orElseThrow(() -> new ResourceNotFoundException("ROLE_DELIVERY is required for dev data"));

        seedUser("johndoe", "john.doe@example.com", CUSTOMER_PASSWORD,
                "John", "Doe", userRole);
        seedUser("admin", "admin@example.com", ADMIN_PASSWORD,
                "Admin", "User", userRole, adminRole);
        seedUser("warehouse_worker", "warehouse.worker@order-platform.test", WAREHOUSE_PASSWORD,
                "Maya", "Chen", warehouseRole);
        seedUser("delivery_driver", "delivery.driver@order-platform.test", DELIVERY_PASSWORD,
                "Daniel", "Brooks", deliveryRole);
    }

    private void seedUser(String username, String email, String password, String firstName, String lastName,
                          RoleEntity... roles) {
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return;
        }
        userRepository.save(UserEntity.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .accountNonLocked(true)
                .roles(new HashSet<>(Set.of(roles)))
                .build());
    }
}
