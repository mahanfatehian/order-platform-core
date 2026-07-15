package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.PaginationLinks;
import com.orderprocessing.webui.exception.BackendClientException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.Map;

@Controller
@RequestMapping("/admin/delivery")
@PreAuthorize("hasRole('DELIVERY')")
public class DeliveryController {
    private static final int PAGE_SIZE = 20;
    private final AuthenticatedPlatformClient client;

    public DeliveryController(AuthenticatedPlatformClient client) {
        this.client = client;
    }

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "0") int packagedPage,
                            @RequestParam(defaultValue = "0") int shippedPage,
                            Model model) {
        int boundedPackagedPage = Math.max(packagedPage, 0);
        int boundedShippedPage = Math.max(shippedPage, 0);
        PageResponse<OrderView> packagedOrders =
                client.fulfillmentOrders(boundedPackagedPage, PAGE_SIZE, "PACKAGED");
        PageResponse<OrderView> shippedOrders =
                client.fulfillmentOrders(boundedShippedPage, PAGE_SIZE, "SHIPPED");
        model.addAttribute("packagedOrders", packagedOrders);
        model.addAttribute("shippedOrders", shippedOrders);
        model.addAttribute("packagedPagination", PaginationLinks.forPage(packagedOrders,
                "/admin/delivery", "packagedPage", Map.of("shippedPage", boundedShippedPage)));
        model.addAttribute("shippedPagination", PaginationLinks.forPage(shippedOrders,
                "/admin/delivery", "shippedPage", Map.of("packagedPage", boundedPackagedPage)));
        return "admin/delivery/index";
    }

    @PostMapping("/orders/{id}/ship")
    public String ship(@PathVariable UUID id,
                       @RequestParam(required = false) String trackingReference,
                       RedirectAttributes redirect) {
        try {
            client.shipOrder(id, trackingReference);
            redirect.addFlashAttribute("success", "Order marked as shipped.");
        } catch (BackendClientException exception) {
            handleConflict(exception, redirect, "This order is no longer ready to ship.");
        }
        return "redirect:/admin/delivery";
    }

    @PostMapping("/orders/{id}/deliver")
    public String deliver(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            client.deliverOrder(id);
            redirect.addFlashAttribute("success", "Delivery completed and inventory finalized.");
        } catch (BackendClientException exception) {
            handleConflict(exception, redirect, "This order is no longer awaiting delivery.");
        }
        return "redirect:/admin/delivery";
    }

    private void handleConflict(BackendClientException exception, RedirectAttributes redirect, String message) {
        if (exception.getStatus().value() != 409) throw exception;
        redirect.addFlashAttribute("warning", message + " The queue has been refreshed.");
    }
}
