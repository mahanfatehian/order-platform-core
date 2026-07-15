package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.*;
import com.orderprocessing.webui.form.InventoryForm;
import com.orderprocessing.webui.form.ProductForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AuthenticatedPlatformClient client;
    public AdminController(AuthenticatedPlatformClient client) { this.client = client; }

    @GetMapping
    public String dashboard(Model model) {
        PageResponse<UserView> users = safe(() -> client.adminUsers(0, 1, ""), PageResponse.empty(0, 1));
        PageResponse<ProductView> products = safe(() -> client.adminProducts(0, 50, ""), PageResponse.empty(0, 50));
        PageResponse<InventoryView> inventory = safe(() -> client.inventory(0, 50, ""), PageResponse.empty(0, 50));
        PageResponse<OrderView> pending = safe(() -> client.adminOrders(0, 1, "PENDING", ""), PageResponse.empty(0, 1));
        PageResponse<OrderView> failed = safe(() -> client.adminOrders(0, 1, "FAILED", ""), PageResponse.empty(0, 1));
        model.addAttribute("userCount", users.totalElements());
        model.addAttribute("activeProducts", products.content().stream().filter(ProductView::active).count());
        model.addAttribute("lowStockProducts", inventory.content().stream().filter(item -> item.availableQuantity() <= 5).count());
        model.addAttribute("pendingOrders", pending.totalElements()); model.addAttribute("failedOrders", failed.totalElements());
        model.addAttribute("serviceStatuses", client.serviceHealth());
        return "admin/dashboard";
    }

    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q, Model model) {
        PageResponse<ProductView> products = client.adminProducts(Math.max(page, 0), 15, q);
        model.addAttribute("products", products); model.addAttribute("q", q);
        model.addAttribute("productPagination",
                PaginationLinks.forPage(products, "/admin/products", "page", java.util.Map.of("q", q)));
        return "admin/products/list";
    }

    @GetMapping("/products/new")
    public String newProduct(Model model) {
        if (!model.containsAttribute("productForm")) model.addAttribute("productForm", new ProductForm());
        model.addAttribute("editing", false); return "admin/products/form";
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute ProductForm productForm, BindingResult binding,
                                Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) { model.addAttribute("editing", false); return "admin/products/form"; }
        client.createProduct(productForm); redirect.addFlashAttribute("success", "Product created");
        return "redirect:/admin/products";
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable UUID id, Model model) {
        ProductView product = client.product(id);
        ProductForm form = new ProductForm(); form.setName(product.name()); form.setSku(product.sku());
        form.setDescription(product.description()); form.setPrice(product.price()); form.setCategory(product.category()); form.setActive(product.active());
        model.addAttribute("productForm", form); model.addAttribute("productId", id); model.addAttribute("editing", true);
        return "admin/products/form";
    }

    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable UUID id, @Valid @ModelAttribute ProductForm productForm,
                                BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) { model.addAttribute("productId", id); model.addAttribute("editing", true); return "admin/products/form"; }
        client.updateProduct(id, productForm); redirect.addFlashAttribute("success", "Product updated");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/activation")
    public String activation(@PathVariable UUID id, @RequestParam boolean active, RedirectAttributes redirect) {
        client.setProductActive(id, active); redirect.addFlashAttribute("success", active ? "Product activated" : "Product deactivated");
        return "redirect:/admin/products";
    }

    @GetMapping("/inventory")
    public String inventory(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q, Model model) {
        PageResponse<InventoryView> inventory = client.inventory(Math.max(page, 0), 20, q);
        model.addAttribute("inventory", inventory); model.addAttribute("q", q);
        model.addAttribute("inventoryPagination",
                PaginationLinks.forPage(inventory, "/admin/inventory", "page", java.util.Map.of("q", q)));
        return "admin/inventory/list";
    }

    @PostMapping("/inventory/{id}")
    public String inventory(@PathVariable UUID id, @Valid @ModelAttribute InventoryForm inventoryForm,
                            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) redirect.addFlashAttribute("error", "Stock quantity must be zero or greater");
        else { client.updateInventory(id, inventoryForm.getQuantity()); redirect.addFlashAttribute("success", "Inventory updated"); }
        return "redirect:/admin/inventory";
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String status,
                         @RequestParam(defaultValue = "") String search, Model model) {
        PageResponse<OrderView> orders = client.adminOrders(Math.max(page, 0), 20, status, search);
        model.addAttribute("orders", orders);
        model.addAttribute("status", status); model.addAttribute("search", search);
        java.util.Map<String, Object> filters = new java.util.LinkedHashMap<>();
        filters.put("status", status); filters.put("search", search);
        model.addAttribute("adminOrderPagination",
                PaginationLinks.forPage(orders, "/admin/orders", "page", filters));
        return "admin/orders/list";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable UUID id, Model model) {
        populateOrderStatusModel(id, model);
        return "admin/orders/detail";
    }

    @GetMapping("/orders/{id}/status")
    public String orderStatus(@PathVariable UUID id, Model model) {
        populateOrderStatusModel(id, model);
        return "orders/_status";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            client.cancelOrder(id);
            redirect.addFlashAttribute("success", "Order cancelled");
        } catch (com.orderprocessing.webui.exception.BackendClientException exception) {
            if (exception.getStatus().value() != 409) throw exception;
            redirect.addFlashAttribute("warning",
                    "This order has already entered fulfillment and can no longer be cancelled.");
        }
        return "redirect:/admin/orders/" + id;
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String search, Model model) {
        PageResponse<UserView> users = client.adminUsers(Math.max(page, 0), 20, search);
        model.addAttribute("users", users); model.addAttribute("search", search);
        model.addAttribute("userPagination",
                PaginationLinks.forPage(users, "/admin/users", "page", java.util.Map.of("search", search)));
        return "admin/users/list";
    }

    @GetMapping("/users/{id}")
    public String user(@PathVariable UUID id, Model model) { model.addAttribute("managedUser", client.adminUser(id)); return "admin/users/detail"; }

    @PostMapping("/users/{id}/status")
    public String userStatus(@PathVariable UUID id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        client.setUserStatus(id, enabled); redirect.addFlashAttribute("success", "Account status updated"); return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/roles")
    public String userRoles(@PathVariable UUID id, @RequestParam(required = false) Set<String> roles, RedirectAttributes redirect) {
        Set<String> safeRoles = roles == null ? Set.of("USER") : new LinkedHashSet<>(roles);
        client.setUserRoles(id, safeRoles); redirect.addFlashAttribute("success", "Roles updated"); return "redirect:/admin/users/" + id;
    }

    @GetMapping("/system")
    public String system(Model model) { model.addAttribute("serviceStatuses", client.serviceHealth()); return "admin/system/index"; }

    private void populateOrderStatusModel(UUID id, Model model) {
        model.addAttribute("order", client.order(id));
        model.addAttribute("history", client.orderHistory(id));
        model.addAttribute("statusEndpoint", "/admin/orders/" + id + "/status");
        model.addAttribute("cancelEndpoint", "/admin/orders/" + id + "/cancel");
    }

    private static <T> T safe(java.util.function.Supplier<T> supplier, T fallback) {
        try { return supplier.get(); } catch (RuntimeException ignored) { return fallback; }
    }
}
