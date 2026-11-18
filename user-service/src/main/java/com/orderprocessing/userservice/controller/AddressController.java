package com.orderprocessing.userservice.controller;

import com.orderprocessing.userservice.dto.AddressRequest;
import com.orderprocessing.userservice.dto.AddressResponse;
import com.orderprocessing.userservice.service.AddressBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressBookService addressBookService;

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return addressBookService.list(userId(jwt));
    }

    @GetMapping("/{id}")
    public AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return addressBookService.get(userId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return addressBookService.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody AddressRequest request) {
        return addressBookService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        addressBookService.delete(userId(jwt), id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
