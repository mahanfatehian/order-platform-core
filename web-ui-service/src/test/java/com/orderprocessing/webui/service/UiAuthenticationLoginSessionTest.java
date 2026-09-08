package com.orderprocessing.webui.service;

import com.orderprocessing.webui.client.PlatformClient;
import com.orderprocessing.webui.config.WebUiProperties;
import com.orderprocessing.webui.dto.LoginTokens;
import com.orderprocessing.webui.form.LoginForm;
import com.orderprocessing.webui.model.UiAuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * /login has no guard against being posted by someone who is already signed in, and changeSessionId() rotates the
 * identifier while keeping every attribute. Signing in as a second identity on the same browser therefore inherits
 * whatever the first one left in the session - its cart, and its outstanding checkout idempotency key.
 */
class UiAuthenticationLoginSessionTest {
    private static final String CART = "com.orderprocessing.webui.service.CartService.CART";
    private static final String CHECKOUT_KEY =
            "com.orderprocessing.webui.controller.CheckoutController.IDEMPOTENCY_KEY";

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final PlatformClient platformClient = mock(PlatformClient.class);
    private final UiAuthenticationService service = new UiAuthenticationService(
            platformClient, new SessionTokenService(), decoder(), new WebUiProperties());

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void signingInAsSomeoneElseDoesNotInheritTheCartOfThePreviousIdentity() {
        MockHttpServletRequest request = requestWithSession(ALICE);

        service.authenticate(form(), request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getAttribute(CART))
                .describedAs("Alice's cart must not follow Bob into his session")
                .isNull();
        assertThat(request.getSession(false).getAttribute(CHECKOUT_KEY)).isNull();
    }

    @Test
    void signingInAgainAsTheSameIdentityKeepsTheCart() {
        MockHttpServletRequest request = requestWithSession(BOB);

        service.authenticate(form(), request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getAttribute(CART)).isEqualTo("alice-cart");
    }

    @Test
    void anAnonymousCartStillSurvivesSigningIn() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CART, "alice-cart");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));

        service.authenticate(form(), request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getAttribute(CART))
                .describedAs("a cart built before signing in is the visitor's own and is meant to carry over")
                .isEqualTo("alice-cart");
    }

    private MockHttpServletRequest requestWithSession(UUID priorUserId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CART, "alice-cart");
        session.setAttribute(CHECKOUT_KEY, "checkout-1");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UiAuthenticatedUser(priorUserId, "prior", Set.of("ROLE_USER")), null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        return request;
    }

    private LoginForm form() {
        when(platformClient.login("bob", "secret")).thenReturn(new LoginTokens("access", "refresh"));
        LoginForm form = new LoginForm();
        form.setUsername("bob");
        form.setPassword("secret");
        return form;
    }

    /** Both tokens decode to Bob; only the type claim distinguishes them. */
    private JwtDecoder decoder() {
        Instant now = Instant.now();
        return token -> Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .subject("bob")
                .claim("type", "access".equals(token) ? "access" : "refresh")
                .claim("userId", BOB.toString())
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
