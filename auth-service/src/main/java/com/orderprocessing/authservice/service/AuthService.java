package com.orderprocessing.authservice.service;

import com.orderprocessing.authservice.client.UserServiceClient;
import com.orderprocessing.authservice.client.dto.InternalAuthenticateRequest;
import com.orderprocessing.authservice.client.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.authservice.dto.LoginRequest;
import com.orderprocessing.authservice.dto.LoginResponse;
import com.orderprocessing.authservice.security.JwtTokenService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserServiceClient userServiceClient;
    private final JwtTokenService jwtTokenService;

    public LoginResponse login(LoginRequest request) {
        try {
            InternalAuthenticatedUserResponse user = userServiceClient.authenticate(
                    InternalAuthenticateRequest.builder()
                            .username(request.getUsername())
                            .password(request.getPassword())
                            .build()
            );

            String token = jwtTokenService.generateToken(
                    user.getUsername(),
                    user.getRoles()
            );

            return LoginResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .roles(user.getRoles())
                    .build();

        } catch (FeignException.Unauthorized ex) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }
}
