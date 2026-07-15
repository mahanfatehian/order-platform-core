package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.PageResponse;
import com.orderprocessing.webui.dto.PaginationLinks;
import com.orderprocessing.webui.dto.OrderView;
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
@RequestMapping("/admin/warehouse")
@PreAuthorize("hasRole('WAREHOUSE')")
public class WarehouseController {
    private static final int PAGE_SIZE = 20;
    private final AuthenticatedPlatformClient client;

    public WarehouseController(AuthenticatedPlatformClient client) {
        this.client = client;
    }

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "0") int page, Model model) {
        PageResponse<OrderView> orders = client.fulfillmentOrders(boundedPage(page), PAGE_SIZE, "CONFIRMED");
        model.addAttribute("orders", orders);
        model.addAttribute("warehousePagination",
                PaginationLinks.forPage(orders, "/admin/warehouse", "page", Map.of()));
        return "admin/warehouse/index";
    }

    @PostMapping("/orders/{id}/pack")
    public String pack(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            client.packOrder(id);
            redirect.addFlashAttribute("success", "Order packed and handed to the delivery queue.");
        } catch (BackendClientException exception) {
            if (exception.getStatus().value() != 409) throw exception;
            redirect.addFlashAttribute("warning",
                    "This order changed before it could be packed. The queue has been refreshed.");
        }
        return "redirect:/admin/warehouse";
    }

    private int boundedPage(int page) { return Math.max(page, 0); }
}
