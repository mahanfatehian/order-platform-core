# Atomic Abuse-Counter Expiry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Redis abuse-counter increment and sliding expiry assignment one atomic operation while preserving the in-process outage fallback.

**Architecture:** `AttemptCounterStore` will execute a single-key Lua script through `StringRedisTemplate`, returning the larger Redis or local count after recovery. Unit tests prove the call shape and fallback semantics; a disposable Redis 7.2 test proves real TTL behavior.

**Tech Stack:** Java 21, Spring Data Redis, Redis Lua, JUnit 5, Mockito, AssertJ, Testcontainers

**Spec:** `docs/superpowers/specs/2026-08-30-focused-reliability-improvements-design.md`

## Global Constraints

- Preserve the public `increment`, `count`, and `clear` APIs.
- Preserve local counting whenever Redis is unavailable.
- Use one Redis key and one script execution per successful increment.
- Use the repository-configured Mahan Fatehian Git identity with no attribution trailers.
- Deliver production code, tests, and test dependency in one implementation commit: `fix(web-ui): expire abuse counters atomically`.

---

### Task 1: Specify atomic counter behavior

**Files:**
- Modify: `web-ui-service/src/test/java/com/orderprocessing/webui/support/AttemptCounterStoreTest.java`

**Interfaces:**
- Consumes: `AttemptCounterStore.increment(String key, Duration window): long`
- Produces: executable expectations for `StringRedisTemplate.execute(RedisScript<Long>, List<String>, Object...)`

- [ ] **Step 1: Replace the shared-counter test with a script-call expectation**

Stub the script result and keep `count` as an independent Redis read:

```java
when(redis.execute(any(RedisScript.class), eq(List.of(KEY)), eq("900000")))
        .thenReturn(4L);
when(values.get(KEY)).thenReturn("4");

assertThat(store.increment(KEY, WINDOW)).isEqualTo(4L);
assertThat(store.count(KEY)).isEqualTo(4L);
verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq("900000"));
verify(values, never()).increment(anyString());
verify(redis, never()).expire(anyString(), any(Duration.class));
```

Add imports for `List`, `RedisScript`, `any`, `eq`, and `never`.

- [ ] **Step 2: Add outage-recovery and null-result cases**

Drive three failed script executions followed by a smaller shared result:

```java
when(redis.execute(any(RedisScript.class), eq(List.of(KEY)), anyString()))
        .thenThrow(new QueryTimeoutException("redis is down"))
        .thenThrow(new QueryTimeoutException("redis is down"))
        .thenThrow(new QueryTimeoutException("redis is down"))
        .thenReturn(1L);

assertThat(store.increment(KEY, WINDOW)).isEqualTo(1L);
assertThat(store.increment(KEY, WINDOW)).isEqualTo(2L);
assertThat(store.increment(KEY, WINDOW)).isEqualTo(3L);
assertThat(store.increment(KEY, WINDOW)).isEqualTo(3L);
```

Update the existing outage and null-result tests to stub `redis.execute(...)` instead of `ValueOperations.increment(...)`. A null script result must fall back to `local.increment`.

- [ ] **Step 3: Run the unit test and verify the red state**

Run:

```powershell
mvn -pl web-ui-service -Dtest=AttemptCounterStoreTest test
```

Expected: FAIL because production still calls `ValueOperations.increment` and standalone `expire`, and it does not preserve the larger local tally on a successful increment.

### Task 2: Implement the single-key Lua increment

**Files:**
- Modify: `web-ui-service/src/main/java/com/orderprocessing/webui/support/AttemptCounterStore.java`

**Interfaces:**
- Consumes: key, positive `Duration`, and the existing `ExpiringCounterMap`
- Produces: one `RedisScript<Long>` execution returning the incremented count

- [ ] **Step 1: Add the immutable script**

Add these imports and constant:

```java
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;

private static final RedisScript<Long> INCREMENT_AND_EXPIRE_SCRIPT =
        new DefaultRedisScript<>("""
                local total = redis.call('INCR', KEYS[1])
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                return total
                """, Long.class);
```

- [ ] **Step 2: Replace the two-call success path**

Implement `increment` as:

```java
public long increment(String key, Duration window) {
    try {
        Long total = redis.execute(
                INCREMENT_AND_EXPIRE_SCRIPT,
                List.of(key),
                Long.toString(window.toMillis()));
        if (total != null) {
            markHealthy();
            return Math.max(total, local.count(key));
        }
    } catch (RuntimeException exception) {
        markDegraded(exception);
    }
    return local.increment(key, window);
}
```

Do not clear the local tally on recovery; its original window must remain effective.

- [ ] **Step 3: Run the unit test and verify green**

Run:

```powershell
mvn -pl web-ui-service -Dtest=AttemptCounterStoreTest test
```

Expected: PASS.

### Task 3: Prove TTL behavior against Redis 7.2

**Files:**
- Modify: `web-ui-service/pom.xml`
- Create: `web-ui-service/src/test/java/com/orderprocessing/webui/support/AttemptCounterStoreRedisIntegrationTest.java`

**Interfaces:**
- Consumes: the production Lua script through `AttemptCounterStore`
- Produces: real-Redis evidence for TTL creation, renewal, expiration, and stale-key repair

- [ ] **Step 1: Add the managed Testcontainers dependency**

Add under test dependencies:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Add a disposable Redis fixture**

Use `@Testcontainers(disabledWithoutDocker = true)` and:

```java
@Container
static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
        .withExposedPorts(6379);
```

In `@BeforeEach`, create and initialize a `LettuceConnectionFactory` from the mapped host and port, build a `StringRedisTemplate`, flush the test database, and construct `AttemptCounterStore` with a fresh `ExpiringCounterMap`. Destroy the connection factory in `@AfterEach`.

- [ ] **Step 3: Add concrete TTL acceptance tests**

Use a two-second window and assert:

```java
assertThat(store.increment(KEY, Duration.ofSeconds(2))).isEqualTo(1L);
assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isBetween(1L, 2_000L);

Thread.sleep(1_100L);
long beforeRenewal = redis.getExpire(KEY, TimeUnit.MILLISECONDS);
assertThat(store.increment(KEY, Duration.ofSeconds(2))).isEqualTo(2L);
assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isGreaterThan(beforeRenewal);
```

Add a stale-key repair case:

```java
redis.opsForValue().set(KEY, "7");
assertThat(redis.getExpire(KEY)).isEqualTo(-1L);
assertThat(store.increment(KEY, Duration.ofSeconds(2))).isEqualTo(8L);
assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive();
```

Add an expiry case using Awaitility-free bounded polling until `Boolean.FALSE.equals(redis.hasKey(KEY))`, failing after four seconds.

- [ ] **Step 4: Run all counter tests**

Run:

```powershell
mvn -pl web-ui-service -Dtest=AttemptCounterStoreTest,AttemptCounterStoreRedisIntegrationTest test
```

Expected: unit tests pass; the Redis integration test passes when Docker is available and is skipped otherwise.

### Task 4: Verify and commit the fix

**Files:**
- Modify: `web-ui-service/pom.xml`
- Modify: `web-ui-service/src/main/java/com/orderprocessing/webui/support/AttemptCounterStore.java`
- Modify: `web-ui-service/src/test/java/com/orderprocessing/webui/support/AttemptCounterStoreTest.java`
- Create: `web-ui-service/src/test/java/com/orderprocessing/webui/support/AttemptCounterStoreRedisIntegrationTest.java`

**Interfaces:**
- Consumes: completed Tasks 1–3
- Produces: the first independently revertible reliability commit

- [ ] **Step 1: Run the complete module test suite**

Run:

```powershell
mvn -pl web-ui-service test
```

Expected: BUILD SUCCESS, with the disposable test skipped only when Docker is unavailable.

- [ ] **Step 2: Check the patch and commit**

Run `git diff --check`, confirm only the four listed files changed, then:

```powershell
git add -- web-ui-service/pom.xml web-ui-service/src/main/java/com/orderprocessing/webui/support/AttemptCounterStore.java web-ui-service/src/test/java/com/orderprocessing/webui/support/AttemptCounterStoreTest.java web-ui-service/src/test/java/com/orderprocessing/webui/support/AttemptCounterStoreRedisIntegrationTest.java
git commit -m "fix(web-ui): expire abuse counters atomically"
```

Expected: one commit authored by `mahan fatehian <mahanfatehian@gmail.com>` with no co-author or generated-by trailer.
