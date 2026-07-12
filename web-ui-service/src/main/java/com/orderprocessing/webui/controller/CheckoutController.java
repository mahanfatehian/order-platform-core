package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.CartView;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.model.Cart;
import com.orderprocessing.webui.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/app/checkout")
public class CheckoutController {
    private static final String KEY = CheckoutController.class.getName() + ".IDEMPOTENCY_KEY";
    private final CartService cartService;
    private final AuthenticatedPlatformClient client;
    public CheckoutController(CartService cartService, AuthenticatedPlatformClient client) {
        this.cartService = cartService; this.client = client;
    }

    @GetMapping
    public String review(HttpSession session, Model model, RedirectAttributes redirect) {
        Cart cart = cartService.get(session);
        if (cart.isEmpty()) { redirect.addFlashAttribute("warning", "Your cart is empty"); return "redirect:/app/cart"; }
        CartView quote = client.quote(cart.getQuantities());
        String key = UUID.randomUUID().toString();
        session.setAttribute(KEY, key);
        model.addAttribute("cart", quote); model.addAttribute("idempotencyKey", key);
        return "checkout/review";
    }

    @PostMapping
    public String create(@RequestParam String idempotencyKey, HttpSession session, RedirectAttributes redirect) {
        Object expected = session.getAttribute(KEY);
        if (expected == null || !expected.equals(idempotencyKey)) {
            redirect.addFlashAttribute("error", "Checkout confirmation expired. Review your cart again.");
            return "redirect:/app/checkout";
        }
        Cart cart = cartService.get(session);
        if (cart.isEmpty()) return "redirect:/app/cart";
        CartView quote = client.quote(cart.getQuantities());
        if (!quote.checkoutReady()) {
            redirect.addFlashAttribute("warning", "Availability changed. Review the updated cart before ordering.");
            return "redirect:/app/cart";
        }
        OrderView order = client.createOrder(cart.getQuantities(), idempotencyKey);
        cartService.clear(session); session.removeAttribute(KEY);
        redirect.addFlashAttribute("success", "Order received. Inventory confirmation is in progress.");
        return "redirect:/app/orders/" + order.id();
    }
}
