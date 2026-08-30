# Atomic Token-State Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validate access-token version and blacklist state in one Redis round trip at both the gateway and downstream servlet services.

**Architecture:** Add a compatibility-preserving combined method to `TokenRevocationService`, override it with one imperative Lua script, and use the equivalent script in the reactive gateway. Both decoders remain fail-closed; a standalone Redis integration test proves the script semantics while unit tests prove each decoder uses only the combined path.

**Tech Stack:** Java 21, Spring Security JWT, Spring Data Redis imperative/reactive APIs, Redis Lua, Reactor, JUnit 5, Mockito, Testcontainers

**Spec:** `docs/superpowers/specs/2026-08-30-focused-reliability-improvements-design.md`

## Global Constraints

- Preserve existing blacklist, refresh rotation, logout, and token-version APIs.
- Reject missing, malformed, mismatched, or unavailable revocation state.
- Target the checked-in standalone Redis topology; do not claim Redis Cluster compatibility for multi-key scripts.
- Use the repository-configured Mahan Fatehian Git identity with no attribution trailers.
- Deliver the complete change in one implementation commit: `perf(security): validate token state in one Redis round trip`.

---

### Task 1: Define the combined revocation contract

**Files:**
- Modify: `security-starter/src/main/java/com/orderprocessing/security/service/TokenRevocationService.java`
- Modify: `security-starter/src/test/java/com/orderprocessing/security/service/RedisTokenBlacklistServiceTest.java`

**Interfaces:**
- Consumes: JTI, user ID, and token-version claim
- Produces: `boolean isAccessTokenValid(String jti, UUID userId, long expectedTokenVersion)`

- [ ] **Step 1: Add tests for one imperative script execution**

Mock `StringRedisTemplate.execute` with keys in this exact order:

```java
List<String> keys = List.of(
        RedisTokenBlacklistService.ACCESS_PREFIX + "jti",
        RedisTokenBlacklistService.VERSION_PREFIX + userId);
when(template.execute(any(RedisScript.class), eq(keys), eq("42"))).thenReturn(1L);

assertThat(service.isAccessTokenValid("jti", userId, 42L)).isTrue();
verify(template).execute(any(RedisScript.class), eq(keys), eq("42"));
verify(template, never()).hasKey(anyString());
verify(template, never()).opsForValue();
```

Add cases where `0L` returns false, null throws `IllegalStateException`, and `RedisConnectionFailureException` propagates.

- [ ] **Step 2: Run the focused service test and verify red**

Run:

```powershell
mvn -pl security-starter -Dtest=RedisTokenBlacklistServiceTest test
```

Expected: FAIL because the combined method does not exist.

- [ ] **Step 3: Add the compatible interface default**

Add to `TokenRevocationService`:

```java
default boolean isAccessTokenValid(String jti, UUID userId, long expectedTokenVersion) {
    if (isAccessTokenBlacklisted(jti)) {
        return false;
    }
    OptionalLong currentVersion = getTokenVersion(userId);
    return currentVersion.isPresent()
            && currentVersion.getAsLong() == expectedTokenVersion;
}
```

This preserves source compatibility for non-Redis implementations.

- [ ] **Step 4: Override the method with one Lua script**

Add to `RedisTokenBlacklistService`:

```java
private static final DefaultRedisScript<Long> ACCESS_VALIDATION_SCRIPT =
        new DefaultRedisScript<>("""
                local current = redis.call('GET', KEYS[2])
                if not current or current ~= ARGV[1] then
                  return 0
                end
                if redis.call('EXISTS', KEYS[1]) == 1 then
                  return 0
                end
                return 1
                """, Long.class);

@Override
public boolean isAccessTokenValid(String jti, UUID userId, long expectedTokenVersion) {
    Long result = redisTemplate.execute(
            ACCESS_VALIDATION_SCRIPT,
            List.of(
                    ACCESS_PREFIX + requiredText(jti, "access token jti"),
                    versionKey(userId)),
            Long.toString(expectedTokenVersion));
    if (result == null) {
        throw new IllegalStateException("Access-token validation failed");
    }
    return result == 1L;
}
```

- [ ] **Step 5: Re-run the service test**

Run the Task 1 command. Expected: PASS.

### Task 2: Switch the servlet decoder to the combined method

**Files:**
- Modify: `security-starter/src/main/java/com/orderprocessing/security/config/SecurityAutoConfiguration.java`
- Create: `security-starter/src/test/java/com/orderprocessing/security/config/SecurityAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `TokenRevocationService.isAccessTokenValid(...)`
- Produces: a servlet `JwtDecoder` making exactly one revocation-service call

- [ ] **Step 1: Add a signed-token decoder test**

Build an HS256 token containing `jti`, `type=access`, `userId`, `tokenVersion=42`, and `roles`. Mock the combined method to return true, decode the token, then assert:

```java
private static final String SECRET = "0123456789abcdef0123456789abcdef";

private String signedAccessToken(String jti, UUID userId) {
    Instant now = Instant.now();
    return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .claim("type", "access")
            .claim("userId", userId.toString())
            .claim("tokenVersion", 42L)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
}

private JwtSecurityProperties properties() {
    JwtSecurityProperties properties = new JwtSecurityProperties();
    properties.setSecret(SECRET);
    return properties;
}
```

```java
verify(revocation).isAccessTokenValid(jti, userId, 42L);
verify(revocation, never()).isAccessTokenBlacklisted(anyString());
verify(revocation, never()).getTokenVersion(any());
```

Add false-result and thrown-runtime-exception cases; both must surface as `JwtException`.

- [ ] **Step 2: Run the decoder test and verify red**

Run:

```powershell
mvn -pl security-starter -Dtest=SecurityAutoConfigurationTest test
```

Expected: FAIL because the decoder invokes the two legacy methods.

- [ ] **Step 3: Replace the two reads**

Remove the `OptionalLong` import and use:

```java
if (!tokenRevocationService.isAccessTokenValid(jwt.getId(), userId, tokenVersion)) {
    throw new JwtException("Access token has been revoked");
}
return jwt;
```

Keep the existing `try/catch` so unexpected Redis/runtime failures remain fail-closed.

- [ ] **Step 4: Re-run both security-starter tests**

Run:

```powershell
mvn -pl security-starter -Dtest=RedisTokenBlacklistServiceTest,SecurityAutoConfigurationTest test
```

Expected: PASS.

### Task 3: Switch the reactive gateway to one script

**Files:**
- Modify: `gateway-service/src/main/java/com/orderprocessing/gateway/config/GatewaySecurityConfig.java`
- Create: `gateway-service/src/test/java/com/orderprocessing/gateway/config/GatewaySecurityConfigTest.java`

**Interfaces:**
- Consumes: JWT claims and `ReactiveStringRedisTemplate`
- Produces: package-private `Mono<Jwt> validateRevocation(Jwt jwt, ReactiveStringRedisTemplate redisTemplate)`

- [ ] **Step 1: Add reactive one-call tests**

Mock:

```java
Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .id("jti")
        .claim("userId", userId.toString())
        .claim("tokenVersion", 42L)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();

List<String> keys = List.of(
        "blacklist:access:jti",
        "user:token-version:" + userId);
when(redis.execute(any(RedisScript.class), eq(keys), eq("42")))
        .thenReturn(Flux.just(1L));
```

Assert the method emits the same JWT and verify no `hasKey` or `opsForValue` access. A `0L` or empty publisher must emit `JwtException`. A direct Redis error must propagate to the existing outer decoder mapping; add a signed-token decoder case that verifies the public `ReactiveJwtDecoder` exposes that failure as `JwtException`.

- [ ] **Step 2: Run the gateway test and verify red**

Run:

```powershell
mvn -pl gateway-service -Dtest=GatewaySecurityConfigTest test
```

Expected: FAIL because `validateRevocation` is private and performs sequential `hasKey`/`GET` calls.

- [ ] **Step 3: Add the reactive script and replace the chain**

Import `DefaultRedisScript` and `RedisScript` from `org.springframework.data.redis.core.script`, define the same script text as Task 1, make `validateRevocation` package-private, and implement:

```java
return redisTemplate.execute(
                ACCESS_VALIDATION_SCRIPT,
                List.of(
                        ACCESS_BLACKLIST_PREFIX + jwt.getId(),
                        TOKEN_VERSION_PREFIX + userId),
                Long.toString(tokenVersion))
        .next()
        .flatMap(result -> result == 1L
                ? Mono.just(jwt)
                : Mono.error(new JwtException("Access token has been revoked")))
        .switchIfEmpty(Mono.error(
                new JwtException("Token revocation state could not be verified")));
```

Leave `reactiveJwtDecoder(...).onErrorMap(...)` in place.

- [ ] **Step 4: Re-run the gateway test**

Run the Task 3 command. Expected: PASS.

### Task 4: Prove script semantics against standalone Redis

**Files:**
- Modify: `security-starter/pom.xml`
- Create: `security-starter/src/test/java/com/orderprocessing/security/service/RedisTokenValidationIntegrationTest.java`

**Interfaces:**
- Consumes: `RedisTokenBlacklistService.isAccessTokenValid(...)`
- Produces: real Redis validation evidence

- [ ] **Step 1: Add Testcontainers JUnit support**

Add `org.testcontainers:junit-jupiter` with test scope to `security-starter/pom.xml`.

- [ ] **Step 2: Create the Redis 7.2 fixture**

Use `@Testcontainers(disabledWithoutDocker = true)`, a static `GenericContainer<>("redis:7.2-alpine")`, a mapped-port `LettuceConnectionFactory`, and a fresh `StringRedisTemplate` flushed before every test.

- [ ] **Step 3: Cover every server-side decision**

Seed `user:token-version:<userId>` and assert valid state returns true. Then independently assert false for a mismatched version, missing version, and `blacklist:access:<jti>` set to `true`. Set the stored version to `not-a-number` and assert false rather than a Redis/Lua error, because string inequality is a revoked state.

- [ ] **Step 4: Run imperative unit and integration tests**

Run:

```powershell
mvn -pl security-starter -Dtest=RedisTokenBlacklistServiceTest,RedisTokenValidationIntegrationTest test
```

Expected: PASS; the integration class is skipped only when Docker is unavailable.

### Task 5: Document, verify, and commit

**Files:**
- Modify: `docs/architecture.md`
- Modify: `security-starter/pom.xml`
- Modify: `security-starter/src/main/java/com/orderprocessing/security/service/TokenRevocationService.java`
- Modify: `security-starter/src/main/java/com/orderprocessing/security/service/RedisTokenBlacklistService.java`
- Modify: `security-starter/src/main/java/com/orderprocessing/security/config/SecurityAutoConfiguration.java`
- Modify: `gateway-service/src/main/java/com/orderprocessing/gateway/config/GatewaySecurityConfig.java`
- Create/modify: the four tests from Tasks 1–4

**Interfaces:**
- Consumes: completed Tasks 1–4
- Produces: the second independently revertible performance commit

- [ ] **Step 1: Document the operational boundary**

In the architecture trust-boundary section, state that gateway and resource services each use one atomic Redis script for blacklist/version validation. State that current multi-key revocation scripts support the standalone Compose Redis deployment and require a hash-tagged key migration before Redis Cluster.

- [ ] **Step 2: Run affected module suites**

Run:

```powershell
mvn -pl security-starter,auth-service,user-service,store-service,order-service,gateway-service -am test
```

Expected: BUILD SUCCESS, with the disposable Redis test skipped only without Docker.

- [ ] **Step 3: Check the patch and commit**

Run `git diff --check`, confirm all changes belong to this plan, stage the exact files listed above, then:

```powershell
git commit -m "perf(security): validate token state in one Redis round trip"
```

Expected: one commit authored by `mahan fatehian <mahanfatehian@gmail.com>` with no attribution trailers.
