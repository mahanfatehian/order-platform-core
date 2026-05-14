package com.orderprocessing.authservice.security;

import com.orderprocessing.authservice.client.UserServiceClient;
import com.orderprocessing.authservice.client.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserServiceClient userServiceClient;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserResponse user;
        try {
            user = userServiceClient.getByUsernameOrEmail(username);
        } catch (Exception ex) {
            throw new UsernameNotFoundException("User not found: " + username, ex);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(
                        user.getRoles().stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList())
                )
                .build();
    }
}
