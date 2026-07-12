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

        if (userRepository.findByUsernameIgnoreCase("johndoe").isEmpty()) {
            userRepository.save(UserEntity.builder()
                    .username("johndoe")
                    .email("john.doe@example.com")
                    .passwordHash(passwordEncoder.encode("Customer123!"))
                    .firstName("John")
                    .lastName("Doe")
                    .enabled(true)
                    .accountNonLocked(true)
                    .roles(new HashSet<>(Set.of(userRole)))
                    .build());
        }
        if (userRepository.findByUsernameIgnoreCase("admin").isEmpty()) {
            userRepository.save(UserEntity.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .passwordHash(passwordEncoder.encode("Admin123!"))
                    .firstName("Admin")
                    .lastName("User")
                    .enabled(true)
                    .accountNonLocked(true)
                    .roles(new HashSet<>(Set.of(userRole, adminRole)))
                    .build());
        }
    }
}
