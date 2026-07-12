package com.orderprocessing.userservice.controller;

import com.orderprocessing.userservice.dto.PageResponse;
import com.orderprocessing.userservice.dto.UpdateUserRolesRequest;
import com.orderprocessing.userservice.dto.UpdateUserStatusRequest;
import com.orderprocessing.userservice.dto.UserResponse;
import com.orderprocessing.userservice.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/admin")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public PageResponse<UserResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return userService.searchAdminUsers(search, enabled, page, size, sort,
                Sort.Direction.fromString(direction.toUpperCase(Locale.ROOT)));
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable UUID id) {
        return userService.getAdminUser(id);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return userService.updateStatus(id, request);
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return userService.updateRoles(id, request);
    }
}
