package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.AuthenticatedPlatformClient;
import com.orderprocessing.webui.dto.CartView;
import com.orderprocessing.webui.dto.OrderView;
import com.orderprocessing.webui.exception.BackendClientException;
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
import java.util.Map;
import java.util.LinkedHashMap;

@Controller
@RequestMapping("/app/checkout")
public class CheckoutController {
    private static final String KEY = CheckoutController.class.getName() + ".IDEMPOTENCY_KEY";
    private static final String REVIEWED_CART = CheckoutController.class.getName() + ".REVIEWED_CART";
    private final CartService cartService;
    private final AuthenticatedPlatformClient client;
    public CheckoutController(CartService cartService, AuthenticatedPlatformClient client) {
        this.cartService = cartService; this.client = client;
    }

    @GetMapping
    public String review(HttpSession session, Model model, RedirectAttributes redirect) {
        Map<UUID, Integer> quantities = cartService.checkoutSnapshot(session);
        if (quantities.isEmpty()) { redirect.addFlashAttribute("warning", "Your cart is empty"); return "redirect:/app/cart"; }
        CartView quote = client.quote(quantities);
        String key = UUID.randomUUID().toString();
        session.setAttribute(KEY, key);
        session.setAttribute(REVIEWED_CART, new LinkedHashMap<>(quantities));
        model.addAttribute("cart", quote); model.addAttribute("idempotencyKey", key);
        return "checkout/review";
    }

    @PostMapping
    public String create(@RequestParam String idempotencyKey, HttpSession session, RedirectAttributes redirect) {
        Object expected = session.getAttribute(KEY);
        if (expected == null || !expected.equals(idempotencyKey)) {
            clearCheckoutAttempt(session);
            redirect.addFlashAttribute("error", "Checkout confirmation expired. Review your cart again.");
            return "redirect:/app/checkout";
        }
        Map<UUID, Integer> quantities = cartService.checkoutSnapshot(session);
        if (quantities.isEmpty()) {
            clearCheckoutAttempt(session);
            return "redirect:/app/cart";
        }
        if (!(session.getAttribute(REVIEWED_CART) instanceof Map<?, ?> reviewed)
                || !reviewed.equals(quantities)) {
            clearCheckoutAttempt(session);
            redirect.addFlashAttribute("warning", "Your cart changed after review. Confirm the refreshed total before ordering.");
            return "redirect:/app/checkout";
        }
        CartView quote = client.quote(quantities);
        if (!quote.checkoutReady()) {
            clearCheckoutAttempt(session);
            redirect.addFlashAttribute("warning", "Availability changed. Review the updated cart before ordering.");
            return "redirect:/app/cart";
        }
        OrderView order;
        try {
            order = client.createOrder(quantities, idempotencyKey);
        } catch (BackendClientException exception) {
            if (exception.getStatus().value() != 409) throw exception;
            clearCheckoutAttempt(session);
            redirect.addFlashAttribute("warning",
                    "Inventory changed while the order was being placed. Your cart was kept so you can review it and try again.");
            return "redirect:/app/cart";
        }
        cartService.removeOrdered(session, quantities);
        clearCheckoutAttempt(session);
        redirect.addFlashAttribute("success", "Order received. Inventory confirmation is in progress.");
        return "redirect:/app/orders/" + order.id();
    }

    private void clearCheckoutAttempt(HttpSession session) {
        session.removeAttribute(KEY);
        session.removeAttribute(REVIEWED_CART);
    }
}
