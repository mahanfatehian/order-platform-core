package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.PaginationLinks;
import com.orderprocessing.webui.exception.BackendClientException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/app/orders")
public class OrderPageController {
    private final AuthenticatedPlatformClient client;
    public OrderPageController(AuthenticatedPlatformClient client) { this.client = client; }

    @GetMapping
    public String orders(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size, Model model) {
        int safeSize = Math.max(1, Math.min(size, 50));
        PageResponse<OrderView> orders = client.myOrders(Math.max(page, 0), safeSize);
        model.addAttribute("orders", orders);
        model.addAttribute("orderPagination", PaginationLinks.forPage(orders, "/app/orders", "page",
                safeSize == 10 ? java.util.Map.of() : java.util.Map.of("size", safeSize)));
        return "orders/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        populateStatusModel(id, model);
        return "orders/detail";
    }

    @GetMapping("/{id}/status")
    public String status(@PathVariable UUID id, Model model) {
        populateStatusModel(id, model);
        return "orders/_status";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            client.cancelOrder(id);
            redirect.addFlashAttribute("success", "Order cancelled. Reserved inventory will be released once.");
        } catch (BackendClientException exception) {
            if (exception.getStatus().value() != 409) throw exception;
            redirect.addFlashAttribute("warning",
                    "This order has already entered fulfillment and can no longer be cancelled.");
        }
        return "redirect:/app/orders/" + id;
    }

    private void populateStatusModel(UUID id, Model model) {
        model.addAttribute("order", client.order(id));
        model.addAttribute("history", client.orderHistory(id));
        model.addAttribute("statusEndpoint", "/app/orders/" + id + "/status");
        model.addAttribute("cancelEndpoint", "/app/orders/" + id + "/cancel");
    }
}
