package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.ProductView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {
    private final AuthenticatedPlatformClient client;
    public DashboardController(AuthenticatedPlatformClient client) { this.client = client; }

    @GetMapping("/app")
    public String dashboard(Model model) {
        model.addAttribute("ordersUnavailable", false);
        model.addAttribute("productsUnavailable", false);
        try {
            PageResponse<OrderView> orders = client.myOrders(0, 5);
            model.addAttribute("recentOrders", orders.content());
            model.addAttribute("pendingOrders", orders.content().stream().filter(OrderView::pending).count());
        } catch (RuntimeException exception) {
            model.addAttribute("recentOrders", List.of());
            model.addAttribute("ordersUnavailable", true);
        }
        try {
            PageResponse<ProductView> products = client.products(0, 1, "", "createdAt,desc", true);
            model.addAttribute("availableProducts", products.totalElements());
        } catch (RuntimeException exception) {
            model.addAttribute("productsUnavailable", true);
        }
        return "dashboard/index";
    }
}
