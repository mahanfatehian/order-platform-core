package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.dto.PageResponse;
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
        PageResponse<OrderView> orders = client.myOrders(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
        model.addAttribute("orders", orders);
        return "orders/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("order", client.order(id));
        return "orders/detail";
    }

    @GetMapping("/{id}/status")
    public String status(@PathVariable UUID id, Model model) {
        model.addAttribute("order", client.order(id));
        return "orders/_status";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
        client.cancelOrder(id);
        redirect.addFlashAttribute("success", "Cancellation requested. Reserved inventory will be released once.");
        return "redirect:/app/orders/" + id;
    }
}
