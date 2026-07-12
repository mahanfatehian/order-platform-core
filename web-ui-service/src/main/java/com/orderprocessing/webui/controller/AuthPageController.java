package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.form.LoginForm;
import com.orderprocessing.webui.form.RegistrationForm;
import com.orderprocessing.webui.service.UiAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthPageController {
    private final UiAuthenticationService authenticationService;
    private final PlatformClient platformClient;
    private final WebUiProperties properties;

    public AuthPageController(UiAuthenticationService authenticationService, PlatformClient platformClient,
                              WebUiProperties properties) {
        this.authenticationService = authenticationService;
        this.platformClient = platformClient;
        this.properties = properties;
    }

    @GetMapping("/")
    public String home(org.springframework.security.core.Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() ? "redirect:/app" : "redirect:/login";
    }

    @GetMapping("/login")
    public String login(Model model) {
        if (!model.containsAttribute("loginForm")) model.addAttribute("loginForm", new LoginForm());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm loginForm, BindingResult binding,
                        HttpServletRequest request, HttpServletResponse response) {
        if (binding.hasErrors()) return "auth/login";
        try {
            authenticationService.authenticate(loginForm, request, response);
            return "redirect:/app";
        } catch (BackendClientException exception) {
            loginForm.setPassword(null);
            if (exception.getStatus().value() == 401) binding.reject("credentials", "Username or password is incorrect");
            else binding.reject("service", "Sign-in is temporarily unavailable. Try again shortly.");
            return "auth/login";
        } catch (RuntimeException exception) {
            loginForm.setPassword(null);
            binding.reject("service", "Sign-in is temporarily unavailable. Try again shortly.");
            return "auth/login";
        }
    }

    @GetMapping("/register")
    public String register(Model model) {
        requireRegistration();
        if (!model.containsAttribute("registrationForm")) model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegistrationForm registrationForm, BindingResult binding,
                           RedirectAttributes redirect) {
        requireRegistration();
        if (!registrationForm.getPassword().equals(registrationForm.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (binding.hasErrors()) return "auth/register";
        try {
            platformClient.register(registrationForm);
            redirect.addFlashAttribute("success", "Account created. Sign in to continue.");
            return "redirect:/login";
        } catch (BackendClientException exception) {
            registrationForm.setPassword(null);
            registrationForm.setConfirmPassword(null);
            exception.getFieldErrors().forEach((field, message) -> binding.rejectValue(field, "backend", message));
            if (exception.getStatus().value() == 409) binding.reject("duplicate", exception.getMessage());
            else if (exception.getFieldErrors().isEmpty()) binding.reject("service", "Registration could not be completed");
            return "auth/register";
        }
    }

    private void requireRegistration() {
        if (!properties.getFeatures().isRegistrationEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
