package com.orderprocessing.orderservice.tax;

import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import com.orderprocessing.orderservice.model.Order;
import com.orderprocessing.orderservice.repository.OrderRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders/{orderId}/invoice")
public class TaxInvoiceController {
    private final TaxInvoiceIssuer invoices;
    private final OrderRepository orders;

    public TaxInvoiceController(TaxInvoiceIssuer invoices, OrderRepository orders) {
        this.invoices = invoices;
        this.orders = orders;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public TaxInvoiceView read(@PathVariable UUID orderId, Jwt jwt) {
        return invoices.findOwned(orderId, userId(jwt))
                .map(invoices::view)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice does not exist"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TaxInvoiceView issue(@PathVariable UUID orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order does not exist"));
        return invoices.view(invoices.issueIfAbsent(order));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
