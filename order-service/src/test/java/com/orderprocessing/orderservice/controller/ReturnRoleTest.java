package com.orderprocessing.orderservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnRoleTest {
    @Test
    void acceptAndRejectStayOnTheWarehouseRole() throws Exception {
        Method accept = OrderController.class.getMethod("acceptReturn", java.util.UUID.class, Jwt.class, String.class);
        Method reject = OrderController.class.getMethod("rejectReturn", java.util.UUID.class, Jwt.class);
        Method request = OrderController.class.getMethod("requestReturn", java.util.UUID.class, Jwt.class);

        assertThat(accept.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('WAREHOUSE')");
        assertThat(reject.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('WAREHOUSE')");
        assertThat(request.getAnnotation(PreAuthorize.class)).isNull();
    }
}
