package com.orderprocessing.webui.controller;

import com.orderprocessing.webui.captcha.CaptchaService;
import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.exception.BackendClientException;
import com.orderprocessing.webui.form.LoginForm;
import com.orderprocessing.webui.form.RegistrationForm;
import com.orderprocessing.webui.model.UiAuthenticatedUser;
import com.orderprocessing.webui.service.LoginAttemptService;
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

import java.util.Objects;

@Controller
public class AuthPageController {
    private static final String CAPTCHA_REQUIRED = "captchaRequired";
    private static final String CAPTCHA_MESSAGE = "Enter the characters shown in the image";

    private final UiAuthenticationService authenticationService;
    private final PlatformClient platformClient;
    private final WebUiProperties properties;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaService captchaService;

    public AuthPageController(UiAuthenticationService authenticationService, PlatformClient platformClient,
                              WebUiProperties properties, LoginAttemptService loginAttemptService,
                              CaptchaService captchaService) {
        this.authenticationService = authenticationService;
        this.platformClient = platformClient;
        this.properties = properties;
        this.loginAttemptService = loginAttemptService;
        this.captchaService = captchaService;
    }

    @GetMapping("/")
    public String home(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";
        if (authentication.getPrincipal() instanceof UiAuthenticatedUser user) return landingPage(user);
        java.util.Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return landingPage(new UiAuthenticatedUser(null, authentication.getName(), roles));
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        if (!model.containsAttribute("loginForm")) model.addAttribute("loginForm", new LoginForm());
        model.addAttribute(CAPTCHA_REQUIRED,
                loginAttemptService.loginCaptchaRequired(loginAttemptService.clientIp(request)));
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm loginForm, BindingResult binding, Model model,
                        HttpServletRequest request, HttpServletResponse response) {
        String clientIp = loginAttemptService.clientIp(request);
        if (binding.hasErrors()) return loginView(model, loginForm, clientIp);

        if (loginAttemptService.loginCaptchaRequired(loginForm.getUsername(), clientIp)
                && !captchaService.verify(request.getSession(false), loginForm.getCaptcha())) {
            // A missing or wrong captcha counts as a failed attempt of its own. Without this the gate could be
            // probed indefinitely at no cost, and the counter would never grow past the threshold.
            loginAttemptService.recordLoginFailure(loginForm.getUsername(), clientIp);
            binding.rejectValue("captcha", "invalid", CAPTCHA_MESSAGE);
            return loginView(model, loginForm, clientIp);
        }

        try {
            UiAuthenticatedUser user = authenticationService.authenticate(loginForm, request, response);
            loginAttemptService.clearLoginFailures(loginForm.getUsername(), clientIp);
            return landingPage(user);
        } catch (BackendClientException exception) {
            if (exception.getStatus().value() == 401) {
                loginAttemptService.recordLoginFailure(loginForm.getUsername(), clientIp);
                binding.reject("credentials", "Username or password is incorrect");
            } else {
                // A backend outage is not caused by the caller, so it must never push them towards a captcha.
                binding.reject("service", "Sign-in is temporarily unavailable. Try again shortly.");
            }
            return loginView(model, loginForm, clientIp);
        } catch (RuntimeException exception) {
            binding.reject("service", "Sign-in is temporarily unavailable. Try again shortly.");
            return loginView(model, loginForm, clientIp);
        }
    }

    @GetMapping("/register")
    public String register(Model model, HttpServletRequest request) {
        requireRegistration();
        if (!model.containsAttribute("registrationForm")) model.addAttribute("registrationForm", new RegistrationForm());
        model.addAttribute(CAPTCHA_REQUIRED,
                loginAttemptService.registrationCaptchaRequired(loginAttemptService.clientIp(request)));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegistrationForm registrationForm, BindingResult binding,
                           Model model, HttpServletRequest request, RedirectAttributes redirect) {
        requireRegistration();
        String clientIp = loginAttemptService.clientIp(request);
        // Read the gate before recording, so a submission is judged against the state the form was rendered with.
        // The captcha then appears from the next submission onwards.
        boolean captchaRequired = loginAttemptService.registrationCaptchaRequired(clientIp);
        loginAttemptService.recordRegistrationAttempt(clientIp);

        if (!Objects.equals(registrationForm.getPassword(), registrationForm.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (binding.hasErrors()) return registrationView(model, registrationForm, clientIp);

        if (captchaRequired && !captchaService.verify(request.getSession(false), registrationForm.getCaptcha())) {
            binding.rejectValue("captcha", "invalid", CAPTCHA_MESSAGE);
            return registrationView(model, registrationForm, clientIp);
        }

        try {
            platformClient.register(registrationForm);
            redirect.addFlashAttribute("success", "Account created. Sign in to continue.");
            return "redirect:/login";
        } catch (BackendClientException exception) {
            exception.getFieldErrors().forEach((field, message) -> binding.rejectValue(field, "backend", message));
            if (exception.getStatus().value() == 409) binding.reject("duplicate", exception.getMessage());
            else if (exception.getFieldErrors().isEmpty()) binding.reject("service", "Registration could not be completed");
            return registrationView(model, registrationForm, clientIp);
        }
    }

    /**
     * Re-renders the sign-in form. The gate is re-evaluated after any failure has been recorded, so an attempt
     * that crosses the threshold shows the captcha on the very response that reports the failure.
     */
    private String loginView(Model model, LoginForm loginForm, String clientIp) {
        loginForm.setPassword(null);
        loginForm.setCaptcha(null);
        model.addAttribute(CAPTCHA_REQUIRED,
                loginAttemptService.loginCaptchaRequired(loginForm.getUsername(), clientIp));
        return "auth/login";
    }

    private String registrationView(Model model, RegistrationForm registrationForm, String clientIp) {
        registrationForm.setPassword(null);
        registrationForm.setConfirmPassword(null);
        registrationForm.setCaptcha(null);
        model.addAttribute(CAPTCHA_REQUIRED, loginAttemptService.registrationCaptchaRequired(clientIp));
        return "auth/register";
    }

    private void requireRegistration() {
        if (!properties.getFeatures().isRegistrationEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private String landingPage(UiAuthenticatedUser user) {
        if (user.isWarehouse()) return "redirect:/admin/warehouse";
        if (user.isDelivery()) return "redirect:/admin/delivery";
        if (user.isAdmin()) return "redirect:/admin";
        return "redirect:/app";
    }
}
