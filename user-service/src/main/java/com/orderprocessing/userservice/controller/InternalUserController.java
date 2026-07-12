package com.orderprocessing.userservice.controller;

import com.orderprocessing.userservice.dto.InternalAuthenticateRequest;
import com.orderprocessing.userservice.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.userservice.dto.InternalUserStateResponse;
import com.orderprocessing.userservice.service.InternalAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
@Tag(name = "User Service - Internal APIs", description = "Internal endpoints for service-to-service communication (Requires X-Internal-Api-Key)")
@SecurityRequirement(name = "internalApiKey") // Tells Swagger this requires the internal API key
public class InternalUserController {

    private final InternalAuthenticationService internalAuthenticationService;

    @Operation(summary = "Authenticate user internally", description = "Validates user credentials and returns user details including roles. Used exclusively by the Auth Service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or missing/invalid internal API key"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/authenticate")
    public InternalAuthenticatedUserResponse authenticate(@Valid @RequestBody InternalAuthenticateRequest request) {
        return internalAuthenticationService.authenticate(request);
    }

    @GetMapping("/{id}")
    public InternalUserStateResponse getCurrentState(@PathVariable UUID id) {
        return internalAuthenticationService.getCurrentState(id);
    }
}
