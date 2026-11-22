package com.orderprocessing.orderservice.controller;

import com.orderprocessing.orderservice.dto.ShipOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ShipRoleTest {
    @Test
    void aCustomerRoleCannotShip() throws Exception {
        Method ship = OrderController.class.getMethod("shipOrder", java.util.UUID.class, Jwt.class,
                ShipOrderRequest.class, String.class);
        PreAuthorize rule = ship.getAnnotation(PreAuthorize.class);

        assertThat(rule).isNotNull();
        assertThat(rule.value()).isEqualTo("hasRole('DELIVERY')");
        assertThat(rule.value()).doesNotContain("USER");
        assertThat(rule.value()).doesNotContain("ADMIN");
    }
}
