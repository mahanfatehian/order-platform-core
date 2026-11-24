package com.orderprocessing.userservice.config;

import com.orderprocessing.userservice.repository.RoleRepository;
import com.orderprocessing.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DevDataInitializerDemoOffTest {
    @Test
    void demoModeOffDoesNotSeedPersonas() {
        UserRepository users = mock(UserRepository.class);
        new DevDataInitializer(users, mock(RoleRepository.class), mock(PasswordEncoder.class), false).run(null);
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
