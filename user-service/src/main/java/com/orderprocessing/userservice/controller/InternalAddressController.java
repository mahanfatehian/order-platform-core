package com.orderprocessing.userservice.controller;

import com.orderprocessing.userservice.dto.AddressResponse;
import com.orderprocessing.userservice.service.AddressBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal/addresses")
@RequiredArgsConstructor
public class InternalAddressController {

    private final AddressBookService addressBookService;

    @GetMapping("/{id}")
    public AddressResponse get(@PathVariable UUID id, @RequestParam UUID userId) {
        return addressBookService.get(userId, id);
    }
}
