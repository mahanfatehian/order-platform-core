package com.orderprocessing.userservice.service;

import com.orderprocessing.userservice.dto.InternalUserResponse;
import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    public InternalUserResponse getByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return map(user);
    }

    public InternalUserResponse getByUsernameOrEmail(String value) {
        Optional<UserEntity> userOpt = userRepository.findByUsername(value);
        if (!userOpt.isPresent()) {
            userOpt = userRepository.findByEmail(value);
        }
        UserEntity user = userOpt
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + value));
        return map(user);
    }

    private InternalUserResponse map(UserEntity user) {
        return InternalUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .roles(
                        user.getRoles().stream()
                                .map(RoleEntity::getName)
                                .collect(Collectors.toList())
                )
                .enabled(user.isEnabled())
                .build();
    }
}
