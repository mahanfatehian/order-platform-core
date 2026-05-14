package com.orderprocessing.userservice.controller;

import com.orderprocessing.userservice.dto.InternalUserResponse;
import com.orderprocessing.userservice.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserQueryService userQueryService;

    @GetMapping("/username/{username}")
    public InternalUserResponse getByUsername(@PathVariable String username) {
        return userQueryService.getByUsername(username);
    }

    @GetMapping("/search")
    public InternalUserResponse getByUsernameOrEmail(@RequestParam String value) {
        return userQueryService.getByUsernameOrEmail(value);
    }
}
