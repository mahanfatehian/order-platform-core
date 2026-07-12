package com.orderprocessing.userservice.service;

import com.orderprocessing.userservice.dto.InternalAuthenticateRequest;
import com.orderprocessing.userservice.entity.RoleEntity;
import com.orderprocessing.userservice.entity.UserEntity;
import com.orderprocessing.userservice.exception.AuthenticationFailedException;
import com.orderprocessing.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalAuthenticationServiceTest {

    @Test
    void disabledUserIsRejectedAfterPasswordVerification() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserEntity disabled = UserEntity.builder()
                .username("customer")
                .email("customer@example.com")
                .passwordHash("hash")
                .enabled(false)
                .accountNonLocked(true)
                .roles(Set.of(RoleEntity.builder().name("ROLE_USER").build()))
                .build();
        when(users.findByUsernameIgnoreCase("customer")).thenReturn(Optional.of(disabled));
        when(encoder.matches("Password1!", "hash")).thenReturn(true);
        InternalAuthenticateRequest request = InternalAuthenticateRequest.builder()
                .username(" Customer ")
                .password("Password1!")
                .build();

        assertThatThrownBy(() -> new InternalAuthenticationService(users, encoder).authenticate(request))
                .isInstanceOf(AuthenticationFailedException.class);
    }
}
