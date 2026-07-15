package com.orderprocessing.orderservice.controller;

import com.orderprocessing.orderservice.dto.ShipOrderRequest;
import com.orderprocessing.orderservice.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderControllerContractTest {

    @Test
    void fulfillmentCommandsUseStablePathsAndDoNotGrantImplicitAdminBypass() throws Exception {
        Method pack = OrderController.class.getMethod(
                "packOrder", UUID.class, Jwt.class, String.class);
        Method ship = OrderController.class.getMethod(
                "shipOrder", UUID.class, Jwt.class, ShipOrderRequest.class, String.class);
        Method deliver = OrderController.class.getMethod(
                "deliverOrder", UUID.class, Jwt.class, String.class);

        assertCommand(pack, "/{id}/pack", "hasRole('WAREHOUSE')");
        assertCommand(ship, "/{id}/ship", "hasRole('DELIVERY')");
        assertCommand(deliver, "/{id}/deliver", "hasRole('DELIVERY')");
        assertThat(pack.getAnnotation(PreAuthorize.class).value()).doesNotContain("ADMIN");
        assertThat(ship.getAnnotation(PreAuthorize.class).value()).doesNotContain("ADMIN");
        assertThat(deliver.getAnnotation(PreAuthorize.class).value()).doesNotContain("ADMIN");
    }

    @Test
    void fulfillmentQueueUsesRoleScopedContract() throws Exception {
        Method queue = OrderController.class.getMethod("getFulfillmentOrders",
                Authentication.class, int.class, int.class, Order.Status.class, String.class);

        assertThat(queue.getAnnotation(GetMapping.class).value()).containsExactly("/fulfillment");
        assertThat(queue.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('WAREHOUSE', 'DELIVERY')");
    }

    private void assertCommand(Method method, String path, String authorization) {
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(path);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(authorization);
    }
}
