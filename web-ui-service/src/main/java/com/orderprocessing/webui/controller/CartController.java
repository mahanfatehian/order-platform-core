package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.CartView;
import com.orderprocessing.webui.dto.ProductView;
import com.orderprocessing.webui.form.QuantityForm;
import com.orderprocessing.webui.model.Cart;
import com.orderprocessing.webui.service.CartService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/app/cart")
public class CartController {
    private final CartService cartService;
    private final AuthenticatedPlatformClient client;
    public CartController(CartService cartService, AuthenticatedPlatformClient client) {
        this.cartService = cartService; this.client = client;
    }

    @GetMapping
    public String cart(HttpSession session, Model model) { populate(session, model); return "cart/index"; }

    @PostMapping("/items")
    public String add(@RequestParam UUID productId, @Valid @ModelAttribute QuantityForm quantityForm,
                      BindingResult binding, HttpSession session, Model model,
                      @RequestHeader(value = "HX-Request", required = false) String htmx,
                      RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            ProductView product = client.product(productId);
            if (!product.active()) {
                binding.reject("unavailable", "This product is not active");
            }
        }
        if (!binding.hasErrors() && overQuantityLimit(quantityForm.getQuantity())) {
            binding.reject("quantityLimit", quantityLimitMessage());
        }
        if (!binding.hasErrors() && !cartService.hasRoomFor(session, productId)) {
            // Refuse here rather than letting the service throw, so the shopper gets the existing flash message
            // instead of an error page.
            binding.reject("cartFull", "Your cart already holds the maximum of "
                    + cartService.maximumLineItems() + " different products");
        }
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", binding.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/app/catalog/" + productId;
        }
        cartService.put(session, productId, quantityForm.getQuantity());
        if (isHtmx(htmx)) { populate(session, model); model.addAttribute("success", "Cart updated"); return "cart/_cart-content"; }
        redirect.addFlashAttribute("success", "Added to cart");
        return "redirect:/app/cart";
    }

    @PostMapping("/items/{productId}/quantity")
    public String quantity(@PathVariable UUID productId, @Valid @ModelAttribute QuantityForm form,
                           BindingResult binding, HttpSession session, Model model,
                           @RequestHeader(value = "HX-Request", required = false) String htmx,
                           RedirectAttributes redirect) {
        // Reported separately from a binding failure so the existing "Enter a valid quantity" wording, which the
        // fragment test pins, keeps its meaning: this is a valid number that is simply above the ceiling.
        if (!binding.hasErrors() && overQuantityLimit(form.getQuantity())) {
            if (isHtmx(htmx)) {
                model.addAttribute("cartError", quantityLimitMessage());
                populate(session, model);
                return "cart/_cart-content";
            }
            redirect.addFlashAttribute("error", quantityLimitMessage());
            return result(session, model, htmx);
        }
        if (binding.hasErrors()) {
            if (isHtmx(htmx)) {
                model.addAttribute("cartError", "Enter a valid quantity");
                populate(session, model);
                return "cart/_cart-content";
            }
            redirect.addFlashAttribute("error", "Enter a valid quantity");
        } else {
            cartService.put(session, productId, form.getQuantity());
        }
        return result(session, model, htmx);
    }

    @PostMapping("/items/{productId}/remove")
    public String remove(@PathVariable UUID productId, HttpSession session, Model model,
                         @RequestHeader(value = "HX-Request", required = false) String htmx) {
        cartService.remove(session, productId); return result(session, model, htmx);
    }

    @PostMapping("/clear")
    public String clear(HttpSession session, Model model,
                        @RequestHeader(value = "HX-Request", required = false) String htmx) {
        cartService.clear(session); return result(session, model, htmx);
    }

    private String result(HttpSession session, Model model, String htmx) {
        if (isHtmx(htmx)) { populate(session, model); return "cart/_cart-content"; }
        return "redirect:/app/cart";
    }
    private void populate(HttpSession session, Model model) {
        Cart cart = cartService.get(session);
        CartView view = cart.isEmpty() ? CartView.empty() : client.quote(cart.getQuantities());
        model.addAttribute("cart", view); model.addAttribute("cartCount", cart.totalItems());
    }
    /**
     * The ceiling is configurable, so it is checked against the configured value rather than pinned in an
     * annotation on the form. Rejecting here keeps the shopper on the page with a message; letting CartService
     * throw would surface an IllegalArgumentException that nothing maps, which renders as a server error.
     */
    private boolean overQuantityLimit(Integer quantity) {
        return quantity != null && quantity > cartService.maximumQuantity();
    }

    private String quantityLimitMessage() {
        return "Quantity cannot exceed " + cartService.maximumQuantity() + " per product";
    }

    private static boolean isHtmx(String value) { return "true".equalsIgnoreCase(value); }
}
