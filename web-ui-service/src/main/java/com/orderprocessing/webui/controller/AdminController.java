package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.*;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.exception.SessionExpiredException;
import com.orderprocessing.webui.form.CategoryForm;
import com.orderprocessing.webui.form.InventoryForm;
import com.orderprocessing.webui.form.ProductForm;
import com.orderprocessing.webui.form.StockReceiptForm;
import com.orderprocessing.webui.form.WalletCreditForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
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
        Set<String> unavailable = new LinkedHashSet<>();
        PageResponse<UserView> users = safe("users", unavailable, () -> client.adminUsers(0, 1, ""), PageResponse.empty(0, 1));
        PageResponse<ProductView> products = safe("products", unavailable, () -> client.adminProducts(0, 50, ""), PageResponse.empty(0, 50));
        PageResponse<InventoryView> inventory = safe("inventory", unavailable, () -> client.inventory(0, 50, ""), PageResponse.empty(0, 50));
        PageResponse<OrderView> pending = safe("orders", unavailable, () -> client.adminOrders(0, 1, "PENDING", ""), PageResponse.empty(0, 1));
        PageResponse<OrderView> failed = safe("orders", unavailable, () -> client.adminOrders(0, 1, "FAILED", ""), PageResponse.empty(0, 1));
        model.addAttribute("unavailableMetrics", unavailable);
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
        model.addAttribute("editing", false);
        addCategories(model);
        return "admin/products/form";
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute ProductForm productForm, BindingResult binding,
                                Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            addCategories(model);
            return "admin/products/form";
        }
        try {
            client.createProduct(productForm);
        } catch (BackendClientException exception) {
            model.addAttribute("editing", false);
            model.addAttribute("error", exception.getMessage());
            addCategories(model);
            return "admin/products/form";
        }
        redirect.addFlashAttribute("success", "Product created");
        return "redirect:/admin/products";
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable UUID id, Model model) {
        ProductView product = client.adminProduct(id);
        if (!model.containsAttribute("productForm")) {
            ProductForm form = new ProductForm();
            form.setName(product.name());
            form.setSku(product.sku());
            form.setDescription(product.description());
            form.setPrice(product.price());
            form.setCategoryId(product.categoryId());
            form.setActive(product.active());
            model.addAttribute("productForm", form);
        }
        model.addAttribute("productId", id);
        model.addAttribute("editing", true);
        model.addAttribute("pictureId", product.pictureId());
        addCategories(model);
        return "admin/products/form";
    }

    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable UUID id, @Valid @ModelAttribute ProductForm productForm,
                                BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("productId", id);
            model.addAttribute("editing", true);
            addCategories(model);
            return "admin/products/form";
        }
        try {
            client.updateProduct(id, productForm);
        } catch (BackendClientException exception) {
            model.addAttribute("productId", id);
            model.addAttribute("editing", true);
            model.addAttribute("error", exception.getMessage());
            addCategories(model);
            return "admin/products/form";
        }
        redirect.addFlashAttribute("success", "Product updated");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/activation")
    public String activation(@PathVariable UUID id, @RequestParam boolean active, RedirectAttributes redirect) {
        try {
            client.setProductActive(id, active);
            redirect.addFlashAttribute("success", active ? "Product activated" : "Product deactivated");
        } catch (BackendClientException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute CategoryForm categoryForm, BindingResult binding,
                                 RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", "Category name is required");
            return "redirect:" + safeProductReturn(categoryForm.getReturnTo());
        }
        try {
            client.createCategory(categoryForm.getName());
            redirect.addFlashAttribute("success", "Category saved");
        } catch (BackendClientException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:" + safeProductReturn(categoryForm.getReturnTo());
    }

    @PostMapping("/products/{id}/pictures")
    public String uploadPicture(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                RedirectAttributes redirect) {
        if (file == null || file.isEmpty()) {
            redirect.addFlashAttribute("error", "Choose a PNG or JPEG picture");
            return "redirect:/admin/products/" + id + "/edit";
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        int separator = contentType.indexOf(';');
        if (separator >= 0) {
            contentType = contentType.substring(0, separator);
        }
        contentType = contentType.trim().toLowerCase();
        if (!contentType.equals("image/png") && !contentType.equals("image/jpeg")) {
            redirect.addFlashAttribute("error", "Only image/png and image/jpeg are accepted");
            return "redirect:/admin/products/" + id + "/edit";
        }
        try {
            client.uploadPicture(id, contentType, file.getBytes());
            redirect.addFlashAttribute("success", "Picture stored");
        } catch (BackendClientException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (IOException exception) {
            redirect.addFlashAttribute("error", "The picture could not be read");
        }
        return "redirect:/admin/products/" + id + "/edit";
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

    @PostMapping("/inventory/{id}/receipts")
    public String receiveStock(@PathVariable UUID id, @Valid @ModelAttribute StockReceiptForm receiptForm,
                               BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", "A receipt needs a positive quantity, a reason, and an idempotency key");
            return "redirect:/admin/inventory";
        }
        try {
            client.receiveStock(id, receiptForm.getQuantity(), receiptForm.getReason(), receiptForm.getIdempotencyKey());
            redirect.addFlashAttribute("success", "Stock received");
        } catch (BackendClientException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/inventory";
    }

    private void addCategories(Model model) {
        List<CategoryView> categories = client.categories();
        model.addAttribute("categories", categories == null ? List.of() : categories);
    }

    private static String safeProductReturn(String returnTo) {
        if (returnTo != null && returnTo.startsWith("/admin/products") && !returnTo.startsWith("//")) {
            return returnTo;
        }
        return "/admin/products/new";
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

    @GetMapping("/wallets")
    public String wallets(@RequestParam(required = false) UUID userId, Model model) {
        model.addAttribute("users", client.adminUsers(0, 100, "").content());
        model.addAttribute("selectedUserId", userId);
        WalletCreditForm form = new WalletCreditForm();
        form.setUserId(userId);
        form.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("creditForm", form);
        model.addAttribute("entries", userId == null ? java.util.List.of() : client.walletEntries(userId));
        model.addAttribute("balance", userId == null ? null : client.walletBalance(userId));
        model.addAttribute("credited", null);
        return "admin/wallets/index";
    }

    @PostMapping("/wallets")
    public String creditWallet(@Valid @ModelAttribute("creditForm") com.orderprocessing.webui.form.WalletCreditForm form,
                               BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("users", client.adminUsers(0, 100, "").content());
            model.addAttribute("selectedUserId", form.getUserId());
            model.addAttribute("entries", form.getUserId() == null ? java.util.List.of() : client.walletEntries(form.getUserId()));
            model.addAttribute("balance", form.getUserId() == null ? null : client.walletBalance(form.getUserId()));
            model.addAttribute("credited", null);
            return "admin/wallets/index";
        }
        WalletEntryView credited = client.creditWallet(form.getUserId(), form.getAmount(), form.getReason(), form.getIdempotencyKey());
        model.addAttribute("users", client.adminUsers(0, 100, "").content());
        model.addAttribute("selectedUserId", form.getUserId());
        model.addAttribute("entries", client.walletEntries(form.getUserId()));
        model.addAttribute("balance", client.walletBalance(form.getUserId()));
        model.addAttribute("credited", credited);
        return "admin/wallets/index";
    }

    private void populateOrderStatusModel(UUID id, Model model) {
        model.addAttribute("order", client.order(id));
        model.addAttribute("history", client.orderHistory(id));
        model.addAttribute("statusEndpoint", "/admin/orders/" + id + "/status");
        model.addAttribute("cancelEndpoint", "/admin/orders/" + id + "/cancel");
    }

    /**
     * Keeps one failing panel from taking the whole dashboard down, while recording that it failed. A metric that
     * could not be read is not a metric that is zero, and on this page the difference matters: an administrator
     * looking at it during an incident would otherwise read "0 failed orders" as reassurance when the truth is
     * that nothing could be asked.
     */
    private static <T> T safe(String metric, Set<String> unavailable,
                              java.util.function.Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (SessionExpiredException expired) {
            // Not a panel failure. The caller has to sign in again, and swallowing this renders a dashboard of
            // zeros to somebody who is no longer authenticated.
            throw expired;
        } catch (RuntimeException exception) {
            unavailable.add(metric);
            return fallback;
        }
    }
}
