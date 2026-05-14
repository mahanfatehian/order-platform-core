package com.orderprocessing.userservice.controller;

import com.orderprocessing.userservice.dto.InternalAuthenticateRequest;
import com.orderprocessing.userservice.dto.InternalAuthenticatedUserResponse;
import com.orderprocessing.userservice.service.InternalAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalAuthenticationService internalAuthenticationService;

    @PostMapping("/authenticate")
    public InternalAuthenticatedUserResponse authenticate(@Valid @RequestBody InternalAuthenticateRequest request) {
        return internalAuthenticationService.authenticate(request);
    }

}
