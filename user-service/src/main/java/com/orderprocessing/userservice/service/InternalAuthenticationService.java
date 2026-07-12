package com.orderprocessing.userservice.service;

import com.orderprocessing.userservice.dto.InternalAuthenticateRequest;
import com.orderprocessing.userservice.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.userservice.dto.InternalUserStateResponse;
import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.repository.UserRepository;
import com.orderprocessing.userservice.exception.AuthenticationFailedException;
import com.orderprocessing.userservice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InternalAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public InternalAuthenticatedUserResponse authenticate(InternalAuthenticateRequest request) {
        UserEntity user = userRepository.findByUsernameIgnoreCase(
                        request.getUsername().strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Invalid username or password");
        }

        return InternalAuthenticatedUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(
                        user.getRoles()
                                .stream()
                                .map(RoleEntity::getName)
                                .collect(Collectors.toSet())
                )
                .enabled(user.isEnabled())
                .accountNonLocked(user.isAccountNonLocked())
                .build();
    }

    @Transactional(readOnly = true)
    public InternalUserStateResponse getCurrentState(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return InternalUserStateResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .accountNonLocked(user.isAccountNonLocked())
                .roles(user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()))
                .build();
    }
}
