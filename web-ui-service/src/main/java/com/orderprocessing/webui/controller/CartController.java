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
    private static boolean isHtmx(String value) { return "true".equalsIgnoreCase(value); }
}
