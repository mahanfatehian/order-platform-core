package com.orderprocessing.security.config;

import com.orderprocessing.security.service.TokenRevocationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Boots the starter's own filter chain over a trivial protected endpoint so the refusal a caller actually receives
 * is asserted end to end, rather than inferred from the decoder in isolation.
 */
@SpringBootTest(classes = ResourceServerErrorContractTest.TestApp.class, properties = {
        "app.security.jwt.secret=0123456789abcdef0123456789abcdef",
        "spring.main.banner-mode=off"
})
@AutoConfigureMockMvc
class ResourceServerErrorContractTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Autowired MockMvc mvc;
    @Autowired TokenRevocationService revocation;

    @Test
    void anAnonymousRequestIsRefusedWith401() throws Exception {
        assertThat(mvc.perform(get("/probe")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aMalformedBearerTokenIsRefusedWith401() throws Exception {
        MvcResult result = mvc.perform(get("/probe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    /**
     * A revoked token is the caller's problem, not the server's. Signalling it as a server fault makes every
     * request after a logout, a remote sign-out, or an admin disabling the account answer 500.
     */
    @Test
    void aRevokedAccessTokenIsRefusedWith401AndNotReportedAsAServerFault() throws Exception {
        when(revocation.isAccessTokenValid(anyString(), any(UUID.class), anyLong())).thenReturn(false);

        MvcResult result = mvc.perform(get("/probe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedAccessToken())).andReturn();

        assertThat(result.getResolvedException())
                .describedAs("the refusal must be handled by the chain, not thrown out of it")
                .isNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    private String signedAccessToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("someone")
                .id(UUID.randomUUID().toString())
                .claim("type", "access")
                .claim("roles", List.of("ROLE_USER"))
                .claim("userId", UUID.randomUUID().toString())
                .claim("tokenVersion", 42L)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @SpringBootApplication(exclude = RedisAutoConfiguration.class)
    @RestController
    static class TestApp {
        @GetMapping("/probe")
        String probe() {
            return "reached";
        }

        @Bean
        TokenRevocationService tokenRevocationService() {
            TokenRevocationService revocation = mock(TokenRevocationService.class);
            when(revocation.isAccessTokenValid(anyString(), any(UUID.class), anyLong())).thenReturn(true);
            return revocation;
        }
    }
}
