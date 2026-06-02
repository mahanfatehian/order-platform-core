package com.orderprocessing.authservice.controller;

import com.orderprocessing.authservice.dto.LoginRequest;
import com.orderprocessing.authservice.dto.LoginResponse;
import com.orderprocessing.authservice.dto.RefreshTokenRequest;
import com.orderprocessing.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public endpoints for login, refresh, and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "User Login", description = "Authenticates user and returns Access & Refresh JWT tokens")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Login successful"), @ApiResponse(responseCode = "401", description = "Invalid credentials")})
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Refresh Token", description = "Issues a new Access token using a valid Refresh token")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Tokens refreshed"), @ApiResponse(responseCode = "401", description = "Invalid/Revoked refresh token")})
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @Operation(summary = "User Logout", description = "Blacklists the current Access token and increments token version")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ResponseEntity.ok("Logged out successfully");
    }
}