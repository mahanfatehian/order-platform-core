# Platform Resilience Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct four reliability boundaries in order creation, Kafka routing, logout, and demo lifecycle tooling.

**Architecture:** Keep public APIs unchanged while moving remote quote work outside the Order transaction, resolving every Kafka topic from one bean, making logout revocation-first, and centralizing environment-file selection for demo scripts. Each task is independently testable and produces one implementation commit.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Kafka, Spring Security MVC, JUnit 5, Mockito, Python 3 unittest, Docker Compose v2.

**Spec:** `docs/superpowers/specs/2026-09-02-platform-resilience-design.md`

## Global Constraints

- Preserve every existing HTTP and Kafka payload contract.
- Keep the default topics `order.events` and `store.events`.
- Do not add or rewrite a Flyway migration.
- Use TDD: capture RED output before changing production code and GREEN output afterwards.
- Create exactly one short implementation commit per task using the existing repository Git identity and no attribution trailers.
- Do not push.

---

### Task 1: Make idempotent order creation payload-safe and transaction-bounded

**Files:**
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/service/OrderService.java`
- Modify: `order-service/src/test/java/com/orderprocessing/orderservice/service/OrderServiceTest.java`

**Interfaces:**
- Consumes: Spring Boot's existing `TransactionOperations` bean and `OrderRepository.acquireIdempotencyLock(String)`.
- Produces: unchanged `OrderService.createOrder(UUID, CreateOrderRequest, String, String)` behavior for valid new orders and exact replays; changed baskets with the same key throw `IdempotencyConflictException`.

- [ ] **Step 1: Add failing behavior tests**

Add tests that construct a real existing `Order` with items and prove:

```java
assertThatThrownBy(() -> service.createOrder(userId, changedRequest, "checkout-1", "corr"))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageContaining("different order payload");
verifyNoInteractions(storeClient);
verify(outboxRepository, never()).save(any());
```

Also make `storeClient.quote(...)` throw and assert the advisory lock was never acquired:

```java
when(storeClient.quote(any())).thenThrow(new FeignException.ServiceUnavailable(
        "unavailable", mock(Request.class), null, Map.of()));
assertThatThrownBy(() -> service.createOrder(userId, request, "checkout-2", "corr"))
        .isInstanceOf(ServiceUnavailableException.class);
verify(orderRepository, never()).acquireIdempotencyLock(anyString());
```

Use a mocked `TransactionOperations` that executes its callback for the existing happy-path tests.

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -B -ntp -pl order-service -am -Dtest=OrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the changed-payload test returns the old order, and the quote-failure test observes the advisory lock before the remote failure.

- [ ] **Step 3: Implement the orchestration boundary**

Inject `TransactionOperations`. Remove `@Transactional` from `createOrder`, normalize items before checking a key, and use this flow:

```java
Map<UUID, Integer> quantities = normalizeItems(request.getItems());
Optional<Order> existing = findIdempotentOrder(userId, normalizedKey);
if (existing.isPresent()) return replayOrConflict(existing.get(), quantities);
Map<UUID, StoreQuoteItemResponse> quote = validateQuote(quantities, loadAuthoritativeQuote(quantities));
return Objects.requireNonNull(orderTransactions.execute(status ->
        persistQuotedOrder(userId, quantities, quote, normalizedKey, normalizedCorrelationId)));
```

Inside `persistQuotedOrder`, acquire the advisory lock and recheck the key before writing. Implement `replayOrConflict` by deriving the stored `productId -> quantity` map with `Math::addExact` and comparing it to the normalized request. Keep order, history, and outbox writes inside the callback.

- [ ] **Step 4: Run GREEN and the module suite**

Run the focused command from Step 2, then:

```bash
mvn -B -ntp -pl order-service -am test
```

Expected: all selected reactor modules pass with zero failures and skips.

- [ ] **Step 5: Commit**

```bash
git add order-service/src/main/java/com/orderprocessing/orderservice/service/OrderService.java order-service/src/test/java/com/orderprocessing/orderservice/service/OrderServiceTest.java
git commit -m "fix(order): harden idempotent creation"
```

### Task 2: Resolve Kafka routes from one configuration contract

**Files:**
- Create: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaTopicNames.java`
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/KafkaTopics.java`
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaTopicConfig.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/service/OrderService.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/kafka/OrderKafkaConsumer.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/service/InventoryService.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/kafka/StoreKafkaConsumer.java`
- Modify: focused tests in `kafka-common`, `order-service`, and `store-service`
- Modify: `.env.example`, `docker-compose.yml`, and `README.md`

**Interfaces:**
- Consumes: `kafka.topics.order-events` and `kafka.topics.store-events`.
- Produces: `KafkaTopicNames.orderEvents()` and `KafkaTopicNames.storeEvents()`, used by provisioners, outbox writers, listeners, and DLT derivation.

- [ ] **Step 1: Add failing custom-topic tests**

Use `orders.v2` and `stores.v2` in tests. Assert all provisioned topic names, including `orders.v2.dlt`, `stores.v2.dlt`, and consumer-owned DLTs. Assert Order and Store outbox rows use the custom names. Add an application-context listener test that resolves the listener destinations to those same custom names.

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -B -ntp -pl kafka-common,order-service,store-service -am test
```

Expected: outbox and listener assertions still observe `order.events` or `store.events`; legacy DLT assertions observe default names.

- [ ] **Step 3: Implement shared resolved names**

Create:

```java
public record KafkaTopicNames(String orderEvents, String storeEvents) {
    public KafkaTopicNames {
        orderEvents = KafkaTopics.requireValidTopic(orderEvents, "orderEvents");
        storeEvents = KafkaTopics.requireValidTopic(storeEvents, "storeEvents");
    }
}
```

Expose it as a bean from `KafkaTopicConfig`. Add `KafkaTopics.requireValidTopic` and `KafkaTopics.deadLetterTopic(String sourceTopic)` for legacy source-owned DLT names. Inject `KafkaTopicNames` into both services and replace hard-coded outbox destinations. Resolve listeners with `#{@kafkaTopicNames.orderEvents()}` and `#{@kafkaTopicNames.storeEvents()}`. Derive every `NewTopic` bean from the resolved names.

Pass `KAFKA_TOPICS_ORDER_EVENTS` and `KAFKA_TOPICS_STORE_EVENTS` through Compose to Order and Store, add default values to `.env.example`, and document that both services must receive identical values.

- [ ] **Step 4: Run GREEN and configuration checks**

Run the reactor command from Step 2 plus:

```bash
docker compose --env-file .env.example config --quiet
```

Expected: all tests and Compose validation pass.

- [ ] **Step 5: Commit**

```bash
git add kafka-common order-service store-service .env.example docker-compose.yml README.md
git commit -m "fix(kafka): honor configured topic names"
```

### Task 3: Make browser logout revocation-first

**Files:**
- Create: `web-ui-service/src/main/java/com/orderprocessing/webui/controller/LogoutController.java`
- Modify: `web-ui-service/src/main/java/com/orderprocessing/webui/config/SecurityConfig.java`
- Modify: `web-ui-service/src/main/java/com/orderprocessing/webui/service/UiAuthenticationService.java`
- Create: `web-ui-service/src/test/java/com/orderprocessing/webui/service/UiAuthenticationServiceTest.java`
- Modify: `web-ui-service/src/test/java/com/orderprocessing/webui/WebUiMvcTest.java`

**Interfaces:**
- Consumes: `PlatformClient.logout(String)` and existing `PageExceptionHandler` mappings.
- Produces: CSRF-protected `POST /logout`; successful response remains a redirect to `/login?logout`, while revocation failure is HTTP 503 and keeps the session reusable.

- [ ] **Step 1: Add failing service and MVC tests**

With a real `SessionTokenService` and `MockHttpSession`, make `PlatformClient.logout` throw `ResourceAccessException`; assert `logoutCurrentSession` propagates and `tokenService.current()` still returns the token pair. In `WebUiMvcTest`, make `authenticationService.logoutCurrentSession()` throw and assert:

```java
mvc.perform(post("/logout").with(user("customer").roles("USER")).with(csrf()).session(session))
        .andExpect(status().isServiceUnavailable())
        .andExpect(cookie().doesNotExist("ORDER_PLATFORM_SESSION"));
assertThat(session.isInvalid()).isFalse();
```

Use an observable sentinel session attribute if the servlet mock does not expose `isInvalid()`.

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -B -ntp -pl web-ui-service -am -Dtest=UiAuthenticationServiceTest,WebUiMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the service swallows the backend failure and removes tokens; the built-in logout flow still redirects and invalidates the session.

- [ ] **Step 3: Implement ordered logout**

Change `logoutCurrentSession` so `platformClient.logout` must succeed before `tokenService.clearCurrent()`. Disable Spring Security's built-in logout filter with `AbstractHttpConfigurer::disable`. Implement `LogoutController` to call the service first, then invalidate the HTTP session, clear `SecurityContextHolder`, emit an expired `ORDER_PLATFORM_SESSION` cookie with `SameSite=Lax`, and redirect. Do not catch backend or transport failures; existing advice must render the 503 before any local cleanup occurs.

- [ ] **Step 4: Run GREEN and the module suite**

Run the focused command from Step 2, then:

```bash
mvn -B -ntp -pl web-ui-service -am test
```

Expected: success and failure logout paths pass with zero failures.

- [ ] **Step 5: Commit**

```bash
git add web-ui-service
git commit -m "fix(web-ui): make logout revocation-first"
```

### Task 4: Make demo tooling honor `.env` and verify `dev`

**Files:**
- Create: `scripts/demo-env.sh`
- Create: `scripts/DemoEnvironment.ps1`
- Modify: `scripts/start-demo.sh`, `scripts/stop-demo.sh`, `scripts/reset-demo.sh`
- Modify: `scripts/start-demo.ps1`, `scripts/stop-demo.ps1`, `scripts/reset-demo.ps1`
- Create: `scripts/acceptance/test_demo_script_env.py`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: optional `ORDER_PLATFORM_ENV_FILE`, then `.env`, then `.env.example`.
- Produces: identical environment selection for all six lifecycle scripts and a portable executable contract test.

- [ ] **Step 1: Add failing executable script tests**

In a temporary repository layout, copy the scripts, create both environment files, prepend a fake `docker` executable to `PATH`, and execute start/stop/reset. Assert the captured command contains:

```text
compose --env-file .env
```

Repeat with `ORDER_PLATFORM_ENV_FILE=config/demo.env`, and without `.env` to assert `.env.example`. Cover POSIX shell when `sh` exists and PowerShell when `pwsh` or `powershell` exists; skip only the unavailable interpreter, not assertions for the available one.

- [ ] **Step 2: Run RED**

Run:

```bash
python3 -m unittest scripts/acceptance/test_demo_script_env.py -v
```

Expected: scripts always pass `.env.example` even when `.env` or the explicit override exists.

- [ ] **Step 3: Implement shared resolvers and CI coverage**

The shell helper returns the first existing candidate using this precedence:

```sh
if [ -n "${ORDER_PLATFORM_ENV_FILE:-}" ]; then
  DEMO_ENV_FILE=$ORDER_PLATFORM_ENV_FILE
elif [ -f .env ]; then
  DEMO_ENV_FILE=.env
else
  DEMO_ENV_FILE=.env.example
fi
```

The PowerShell helper implements the same sequence and throws when the selected file is missing. Dot-source the appropriate helper in all lifecycle scripts, print `Using environment file: <path>`, and pass it to every Compose invocation.

Add `dev` to the CI push branch list and run the Python contract during reactor verification. Correct the README sign-in-control URL from port 8085 to the public gateway on port 8080 and state the script precedence.

- [ ] **Step 4: Run GREEN and repository checks**

Run:

```bash
python3 -m unittest scripts/acceptance/test_demo_script_env.py -v
git diff --check
docker compose --env-file .env.example config --quiet
```

Expected: every available interpreter contract passes and both repository checks exit zero.

- [ ] **Step 5: Commit**

```bash
git add scripts .github/workflows/ci.yml README.md
git commit -m "fix(demo): honor local environment files"
```

### Final verification

- [ ] Run `mvn -B -ntp clean verify` with Docker available.
- [ ] Run `python3 -m unittest scripts/acceptance/test_demo_script_env.py -v`.
- [ ] Run `git diff --check` across the implementation range.
- [ ] Run `docker compose --env-file .env.example config --quiet`.
- [ ] Verify every implementation commit's author and committer are `mahan fatehian <mahanfatehian@gmail.com>` and every commit body/trailer is empty.
- [ ] Dispatch a whole-range review and fix any critical or important finding.
- [ ] Fast-forward the verified branch onto local `dev` without pushing.
