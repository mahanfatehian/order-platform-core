# Showcase Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Order/flow into an evidence-first distributed-systems showcase with a protected public edge, executable Saga proof, controlled recovery, guided demo UX, and push-ready documentation.

**Architecture:** Keep commands inside authenticated owner-service HTTP boundaries and Kafka as the fact transport. Add recovery inside `order-service` and `store-service`, share only transport-level DLT reading/republication in `kafka-common`, and serialize placement/compensation with a store-local lifecycle guard. Keep every change independently testable and committed with a short Conventional Commit message.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Security, Spring Kafka, PostgreSQL/Flyway, Thymeleaf, HTMX, Bootstrap 5.3, Python 3 standard library, Docker Compose, GitHub Actions, Gitleaks.

## Global Constraints

- Work in the existing clean `dev` checkout so the final branch can be pushed directly.
- Author every commit with the repository-configured `mahan fatehian <mahanfatehian@gmail.com>` identity.
- Preserve at-least-once semantics plus idempotent consumers; never claim end-to-end exactly-once delivery.
- Do not add Kubernetes, a workflow engine, CQRS, event sourcing, a service mesh, or another microservice.
- Do not rewrite Git history, rename branches, force-push, rotate credentials, or expose historical secret values.
- Never auto-replay, bulk-replay, or replay a Kafka DLT record without a durable payload `eventId` and an event-specific safety policy.
- Use forward-only Flyway migrations; never edit an applied migration.
- Use test-first changes for behavior and run focused verification before every commit.
- Use `.env.example` and disposable Compose project names for clean-stack verification.

---

### Task 1: Cover the Repository Default Branch in CI

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: GitHub push branch names `master` and `main`.
- Produces: CI push trigger `branches: [master, main]` without changing PR/manual behavior.

- [ ] **Step 1: Prove the current trigger omits `master`**

```powershell
$yaml = Get-Content -LiteralPath '.github/workflows/ci.yml' -Raw
$branches = [regex]::Match($yaml, '(?m)^\s*branches:\s*\[([^\]]+)\]\s*$').Groups[1].Value -split ','
if ('master' -notin @($branches.Trim())) { exit 1 }
```

Expected: exit code `1` before the edit.

- [ ] **Step 2: Add the current and future branch names**

```yaml
on:
  push:
    branches: [master, main]
  pull_request:
  workflow_dispatch:
```

- [ ] **Step 3: Validate workflow syntax and whitespace**

Run:

```powershell
docker run --rm --mount "type=bind,source=$((Resolve-Path '.').Path),target=/repo,readonly" --workdir /repo rhysd/actionlint:1.7.12
git diff --check
```

Expected: both commands exit `0`.

- [ ] **Step 4: Commit only the workflow trigger**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: cover the default branch"
```

### Task 2: Add Redacted Secret Scanning

**Files:**
- Create: `.github/workflows/gitleaks.yml`
- Create: `.gitleaks.toml`
- Create: `.gitleaksignore`
- Modify: `CONTRIBUTING.md`
- Modify: `SECURITY.md`

**Interfaces:**
- Consumes: full Git history and the default Gitleaks rules.
- Produces: redacted push/PR/manual scanning with exact-finding historical baselines only.

- [ ] **Step 1: Add the minimum default-rule configuration**

```toml
title = "Order/flow secret scanning"

[extend]
useDefault = true
```

- [ ] **Step 2: Run a redacted full-history scan and review metadata only**

Use the immutable CLI image:

```text
ghcr.io/gitleaks/gitleaks:v8.30.0@sha256:691af3c7c5a48b16f187ce3446d5f194838f91238f27270ed36eef6359a574d9
```

Write JSON only under the OS temporary directory. Extract only `RuleID`, `File`, `StartLine`, `Commit`, and `Fingerprint`; never output `Secret` or `Match`. Put each reviewed historical finding fingerprint on its own sorted line in `.gitleaksignore`. Do not allowlist a whole commit or file.

- [ ] **Step 3: Prove the scanner catches a generated canary**

Generate an AWS-shaped test key by concatenating string fragments in the OS temp directory, run `gitleaks dir --redact --exit-code=2`, assert exit `2`, then remove only that verified temp directory. Do not add the canary to the repository.

- [ ] **Step 4: Add the pinned workflow**

Use `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1` (`v7.0.1`) with `fetch-depth: 0` and `persist-credentials: false`. Run the immutable Gitleaks CLI image with:

```bash
gitleaks git --source=/repo --config=/repo/.gitleaks.toml --redact --no-banner
```

Set workflow permissions to `contents: read`, triggers to unfiltered `push`, `pull_request`, and `workflow_dispatch`, and timeout to ten minutes. Do not upload finding artifacts or post finding comments.

- [ ] **Step 5: Document remediation and baseline rules**

In `CONTRIBUTING.md`, document the immutable Docker scan and generated canary. In `SECURITY.md`, require rotate/revoke first, prohibit copying values into logs/issues, and state that `.gitleaksignore` neither removes history nor makes old credentials safe.

- [ ] **Step 6: Verify current tree and history**

Run directory and Git scans with `--redact`, then actionlint and `git diff --check`. Expected: canary is detected; repository scans exit `0` after exact baselining.

- [ ] **Step 7: Commit the scanner boundary**

```bash
git add .github/workflows/gitleaks.yml .gitleaks.toml .gitleaksignore CONTRIBUTING.md SECURITY.md
git commit -m "security: add secret scanning"
```

### Task 3: Enforce the Public Gateway Trust Boundary

**Files:**
- Create: `gateway-service/src/main/java/com/orderprocessing/gateway/filter/InternalApiBoundaryFilter.java`
- Create: `gateway-service/src/test/java/com/orderprocessing/gateway/filter/InternalApiBoundaryFilterTest.java`
- Modify: `gateway-service/src/test/java/com/orderprocessing/gateway/GatewayApplicationContextTest.java`

**Interfaces:**
- Consumes: public requests and gateway `GatewayErrorWriter`.
- Produces: correlated `403 INTERNAL_API_FORBIDDEN` responses for internal paths and globally sanitized internal credentials.

- [ ] **Step 1: Write failing filter tests**

Test both `/api/users/internal/authenticate` and `/api/store/internal/quote`, plus both headers regardless of case:

```java
static final String USER_INTERNAL_HEADER = "X-Internal-Api-Key";
static final String STORE_INTERNAL_HEADER = "X-Store-Internal-Api-Key";
```

Assert the downstream chain is never called for internal paths. For a public path, capture the forwarded `ServerWebExchange` and assert both headers are absent while `X-Correlation-Id` remains.

- [ ] **Step 2: Run the focused test and observe failure**

Run in the Java 21 Maven container:

```bash
mvn -B -ntp -pl gateway-service -am -Dtest=InternalApiBoundaryFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because the filter does not exist.

- [ ] **Step 3: Implement the ordered WebFilter**

Create `InternalApiBoundaryFilter implements WebFilter, Ordered`. Use parsed path patterns `/api/users/internal/**` and `/api/store/internal/**`, remove both internal headers on every non-blocked request, and return `GatewayErrorWriter.write(exchange, FORBIDDEN, "INTERNAL_API_FORBIDDEN", "Internal service APIs are not available through the public gateway")`. Return `Ordered.HIGHEST_PRECEDENCE + 10` so correlation processing runs first.

- [ ] **Step 4: Add a real security-chain regression test**

Use the existing Spring Boot gateway context and `WebTestClient` to assert authenticated requests cannot route to the two internal paths and the response contains the standard JSON code and correlation header.

- [ ] **Step 5: Run focused and module tests**

Expected: filter, gateway context, and all gateway tests pass.

- [ ] **Step 6: Commit the edge fix**

```bash
git add gateway-service/src/main gateway-service/src/test
git commit -m "fix(gateway): block internal APIs"
```

### Task 4: Execute the Complete Saga in CI

**Files:**
- Create: `scripts/acceptance/full-saga.py`
- Modify: `.github/workflows/ci.yml`
- Modify: `CONTRIBUTING.md`

**Interfaces:**
- Consumes: gateway base URL, four dev personas, public catalog/order APIs.
- Produces: bounded black-box proof of the five-stage Saga and authorization/idempotency invariants.

- [ ] **Step 1: Implement deterministic HTTP helpers**

Use only Python standard-library `urllib`, `json`, `argparse`, `time`, and `uuid`. Define:

```python
def request_json(method, url, *, token=None, body=None, headers=None, expected=(200,)) -> dict: ...
def login(base_url: str, username: str, password: str) -> str: ...
def poll_order(base_url: str, token: str, order_id: str, expected: str, deadline: float) -> dict: ...
```

Errors must include method, sanitized URL, expected status, actual status, order ID, and last observed order state. Never print passwords or JWTs.

- [ ] **Step 2: Drive and assert the real lifecycle**

Select the first active in-stock catalog product. Record starting availability. Log in customer, warehouse, delivery, and admin. Create quantity `1` with a UUID idempotency key, repeat creation and assert the same order ID, poll `CONFIRMED`, assert customer cannot pack, pack, reject duplicate pack, ship with a tracking reference, reject duplicate ship, deliver, reject duplicate delivery, and assert final availability is exactly one lower.

Fetch history and assert the ordered subsequence `PENDING`, `CONFIRMED`, `PACKAGED`, `SHIPPED`, `DELIVERED`, with non-null event and correlation IDs.

- [ ] **Step 3: Run the acceptance script against a clean Compose project**

Start with `docker compose -p orderflow-acceptance --env-file .env.example up -d --build --wait --wait-timeout 360`, run the script, capture bounded logs on failure, and always tear down that project with volumes.

- [ ] **Step 4: Wire CI after health and metrics checks**

Add `python3 scripts/acceptance/full-saga.py --base-url http://localhost:8080` to `compose-smoke`. Existing unconditional teardown remains last.

- [ ] **Step 5: Document local execution and commit**

```bash
git add scripts/acceptance/full-saga.py .github/workflows/ci.yml CONTRIBUTING.md
git commit -m "test: exercise the full saga"
```

### Task 5: Serialize Inventory Placement and Compensation

**Files:**
- Create: `store-service/src/main/resources/db/migration/V8__guard_inventory_saga_lifecycle.sql`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/model/InventoryOrderLifecycle.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/repository/InventoryOrderLifecycleRepository.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/service/InventoryService.java`
- Modify: `store-service/src/test/java/com/orderprocessing/storeservice/service/InventoryServiceTest.java`

**Interfaces:**
- Produces: one locked local guard per order with `ACTIVE`, `RELEASED`, or `CONSUMED` state.
- Consumes: `OrderPlacedEvent`, `OrderCancelledEvent`, `OrderFailedEvent`, and `OrderDeliveredEvent`.

- [ ] **Step 1: Write both race-order tests**

Add tests proving:

```text
placement -> compensation = reservation created then RELEASED
compensation -> placement = terminal tombstone retained and no reservation created
```

Also prove duplicate delivery consumes only once and late placement after `CONSUMED` is ignored.

- [ ] **Step 2: Add the forward-only schema**

Create `inventory_order_lifecycle(order_id uuid primary key, state varchar(16), last_event_id uuid, updated_at timestamptz)`. Add a check constraint for the three states. Backfill one row per existing reservation order: any `RESERVED` is `ACTIVE`, otherwise any `CONSUMED` is `CONSUMED`, otherwise `RELEASED`.

- [ ] **Step 3: Add insert-and-lock repository operations**

Define:

```java
int insertIfAbsent(UUID orderId, String state, UUID eventId, Instant updatedAt);
Optional<InventoryOrderLifecycle> findByOrderIdForUpdate(UUID orderId);
```

The insert uses `on conflict (order_id) do nothing`; the lookup uses `PESSIMISTIC_WRITE`.

- [ ] **Step 4: Guard every inventory event transaction**

Before domain mutation, insert the event's initial state and lock the guard. Placement proceeds only in `ACTIVE`. Cancellation/failure set `RELEASED` and release active reservations. Delivery sets `CONSUMED` only from `ACTIVE`; a terminal state never moves backward. Keep inbox insertion and guard/domain mutation in the same transaction.

- [ ] **Step 5: Run store tests and migration validation**

Run focused `InventoryServiceTest`, the entire store module, and a clean Compose migration start. Expected: both interleavings are safe and all existing inventory tests remain green.

- [ ] **Step 6: Commit the race fix**

```bash
git add store-service/src/main store-service/src/test
git commit -m "fix(store): guard compensated orders"
```

### Task 6: Give Every Consumer an Owned DLT

**Files:**
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/KafkaTopics.java`
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaConfig.java`
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaTopicConfig.java`
- Modify: `kafka-common/src/test/java/com/orderprocessing/kafkacommon/KafkaEventRegistryTest.java`
- Modify: `docs/asyncapi.yaml`

**Interfaces:**
- Produces: `KafkaTopics.deadLetterTopic(sourceTopic, consumerGroup)`.
- Produces topics such as `order.events.store-service.dlt` and `store.events.order-service.dlt`.

- [ ] **Step 1: Write topic-name tests**

Assert valid source/group names produce `<source>.<group>.dlt` and blank/unsafe values are rejected.

- [ ] **Step 2: Replace shared DLT routing**

Inject `${spring.application.name}` into Kafka configuration. Route exhausted records to `KafkaTopics.deadLetterTopic(record.topic(), applicationName)` on the original partition. Create owned DLT topic beans for each source in each consuming application context.

- [ ] **Step 3: Update the contract document**

Replace shared DLT channels with the consumer-owned channels and document original topic, partition, offset, consumer-group, exception, key, and Spring type metadata headers.

- [ ] **Step 4: Run Kafka common and consumer routing tests**

Expected: registry compatibility tests and both consumer test classes pass.

- [ ] **Step 5: Commit the transport boundary**

```bash
git add kafka-common docs/asyncapi.yaml
git commit -m "feat(kafka): isolate dead letters"
```

### Task 7: Recover Dead-Lettered Outbox Heads

**Files:**
- Create: `order-service/src/main/resources/db/migration/V8__audit_outbox_recovery.sql`
- Create: `store-service/src/main/resources/db/migration/V9__audit_outbox_recovery.sql`
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/model/RecoveryAuditEntry.java`
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/repository/RecoveryAuditRepository.java`
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/service/OutboxRecoveryService.java`
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/dto/OutboxRecoveryRequest.java`
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/dto/OutboxRecoveryResponse.java`
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/controller/RecoveryController.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/model/RecoveryAuditEntry.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/repository/RecoveryAuditRepository.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/service/OutboxRecoveryService.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/dto/OutboxRecoveryRequest.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/dto/OutboxRecoveryResponse.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/controller/RecoveryController.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/model/OutboxEvent.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/repository/OutboxEventRepository.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/service/OutboxPublisherService.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/model/StoreOutboxEvent.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/repository/StoreOutboxEventRepository.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/service/StoreOutboxPublisherService.java`
- Modify: `order-service/src/test/java/com/orderprocessing/orderservice/service/OrderServiceTest.java`
- Modify: `store-service/src/test/java/com/orderprocessing/storeservice/service/InventoryServiceTest.java`

**Interfaces:**
- Order endpoint: `POST /api/orders/admin/recovery/outbox/{eventId}`.
- Store endpoint: `POST /api/store/admin/recovery/outbox/{eventId}`.
- Body: `record OutboxRecoveryRequest(@NotBlank @Size(max=500) String reason)`.
- Response: event ID, aggregate ID, recovery generation, cumulative attempts, outcome `REQUEUED`.

- [ ] **Step 1: Write strict state/authorization tests**

Prove only `ROLE_ADMIN` succeeds; non-dead-letter, published, missing, later aggregate event, blank reason, duplicate, and concurrent requests fail without mutation.

- [ ] **Step 2: Add retry-generation schema**

Add `recovery_generation int not null default 0` and `generation_attempt_count int not null default attempt_count` to each outbox table. Create append-only `recovery_audit_entries` with recovery type, target reference, generation, outcome, actor UUID, reason, detail, and recorded timestamp. Add uniqueness on target/generation/outcome.

- [ ] **Step 3: Lock the aggregate publish head**

Add a native repository query that locks the earliest unpublished row for one aggregate. Recovery must lock the requested row, verify it is the head and `dead_lettered=true`, increment generation, reset only generation attempts, preserve cumulative attempts, clear scheduling error state, and append `REQUEUED` atomically.

- [ ] **Step 4: Make publishers generation-aware**

Each failure increments cumulative and generation attempt counts. The max budget applies to the current generation. A recovered publish appends `PUBLISHED`; exhaustion appends `DEAD_LETTERED`. The HTTP transaction never claims asynchronous success.

- [ ] **Step 5: Run owner-service tests and migration starts**

Expected: ordering, retry-budget, audit, and authorization tests pass for both services.

- [ ] **Step 6: Commit owner outbox recovery**

```bash
git add order-service store-service
git commit -m "feat(ops): recover outbox events"
```

### Task 8: Resolve Exact Kafka DLT Records Safely

**Files:**
- Create: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/recovery/KafkaDeadLetterRecord.java`.
- Create: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/recovery/KafkaDeadLetterOperations.java`.
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaConfig.java`.
- Create: `kafka-common/src/test/java/com/orderprocessing/kafkacommon/recovery/KafkaDeadLetterOperationsTest.java`.
- Create: `order-service/src/main/resources/db/migration/V9__track_dlt_resolution.sql`.
- Create: `store-service/src/main/resources/db/migration/V10__track_dlt_resolution.sql`.
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/model/KafkaDltResolution.java`.
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/repository/KafkaDltResolutionRepository.java`.
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/service/OrderDltReplayPolicy.java`.
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/service/DltRecoveryService.java`.
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/dto/DltResolutionRequest.java`.
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/dto/DltResolutionResponse.java`.
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/controller/RecoveryController.java`.
- Create: `order-service/src/test/java/com/orderprocessing/orderservice/service/DltRecoveryServiceTest.java`.
- Create: `order-service/src/test/java/com/orderprocessing/orderservice/integration/OrderRecoveryIntegrationTest.java`.
- Modify: `order-service/pom.xml`.
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/model/KafkaDltResolution.java`.
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/repository/KafkaDltResolutionRepository.java`.
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/service/StoreDltReplayPolicy.java`.
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/service/DltRecoveryService.java`.
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/dto/DltResolutionRequest.java`.
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/dto/DltResolutionResponse.java`.
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/controller/RecoveryController.java`.
- Create: `store-service/src/test/java/com/orderprocessing/storeservice/service/DltRecoveryServiceTest.java`.
- Create: `store-service/src/test/java/com/orderprocessing/storeservice/integration/StoreRecoveryIntegrationTest.java`.
- Modify: `store-service/pom.xml`.
- Create: `docs/runbooks/dead-letter-recovery.md`.
- Modify: `docs/architecture.md`, `docs/adr/0001-human-in-the-loop-fulfillment.md`, `docs/asyncapi.yaml`.

**Interfaces:**
- Endpoint: `POST <owner-admin-base>/recovery/dlt`.
- Request fields: `dltTopic`, `partition`, `offset`, `disposition`, `reason`.
- Dispositions: `REPLAY`, `ACKNOWLEDGE_SUPERSEDED`.
- Common operation: `KafkaDeadLetterRecord read(String dltTopic, int partition, long offset, String expectedConsumerGroup)` and `void replay(KafkaDeadLetterRecord record)`.

- [ ] **Step 1: Write failing transport and policy tests**

Reject missing payload `eventId`, wrong consumer-owned DLT, mismatched original consumer group/source topic, unsafe type, negative coordinates, copied exception headers, and completed duplicate resolution. Prove one in-progress claim wins under concurrency.

- [ ] **Step 2: Read one exact immutable coordinate**

Create a short-lived manually assigned consumer, seek to the requested offset, poll with a bounded timeout, and require an exact match. Validate DLT metadata. Accept only `DomainEvent` classes registered by `KafkaEventRegistry` and consumed by that owner service.

- [ ] **Step 3: Republish clean transport metadata**

Send the original aggregate key and typed event to the original source topic using `KafkaTemplate`. Preserve event/correlation IDs in the payload. Let `JsonSerializer` regenerate only its type header; do not copy DLT or exception headers.

- [ ] **Step 4: Persist claim and append audit outcomes**

Create one mutable resolution claim per DLT coordinate with generation/status, plus append-only entries in the existing recovery audit table. Commit `IN_PROGRESS` before Kafka I/O, then append `REPUBLISHED`, `ACKNOWLEDGED_SUPERSEDED`, or `FAILED`. A failed generation may be retried; a completed resolution conflicts.

- [ ] **Step 5: Enforce event-specific owner policies**

Order service permits current stock response and fulfillment fact contracts only when their order state makes replay harmless; otherwise require superseded disposition. Store service permits current order lifecycle facts and relies on the locked inventory lifecycle guard at consumption time. Compensation facts missing their terminal tombstone must be repaired before placement replay.

- [ ] **Step 6: Prove both race orders and duplicate delivery with real infrastructure**

Add test-scoped `org.testcontainers:junit-jupiter` and `org.testcontainers:kafka` dependencies to both owner-service POMs. Use PostgreSQL and Kafka containers to assert compensation-before-placement creates no reservation, placement-before-compensation releases it, duplicate `OrderDeliveredEvent` identity consumes once, legacy identity is rejected, and a republished record carries no DLT exception headers.

- [ ] **Step 7: Document operator procedure and contracts**

The runbook must require cause inspection, exact coordinates, reason, current state verification, correct disposition, outcome verification, and alert clearing. Explicitly prohibit automatic/bulk replay.

- [ ] **Step 8: Commit controlled DLT resolution**

```bash
git add kafka-common order-service store-service docs
git commit -m "feat(ops): resolve dead letters"
```

### Task 9: Guide the Multi-Persona Demo

**Files:**
- Create: `web-ui-service/src/main/resources/templates/fragments/demo-guide.html`
- Modify: `web-ui-service/src/main/resources/templates/auth/login.html`
- Modify: `web-ui-service/src/main/resources/templates/dashboard/index.html`
- Modify: `web-ui-service/src/main/resources/templates/admin/dashboard.html`
- Modify: `web-ui-service/src/main/resources/templates/admin/warehouse/index.html`
- Modify: `web-ui-service/src/main/resources/templates/admin/delivery/index.html`
- Modify: `web-ui-service/src/main/resources/templates/orders/_status.html`
- Modify: `web-ui-service/src/main/resources/static/assets/js/app.js`
- Modify: `web-ui-service/src/main/resources/static/assets/css/app.css`
- Modify: `web-ui-service/src/test/java/com/orderprocessing/webui/WebUiMvcTest.java`

**Interfaces:**
- Persona hooks: `data-demo-persona`, `data-demo-username`, `data-demo-password`, `data-demo-label`.
- Guide fragment: `guide(stepLabel, action, nextRole, nextUsername, nextAction)`.
- Poll hooks: `data-order-poll-state`, `data-order-poll-message`, `data-order-poll-retry`.
- Audit hooks: `data-copy-target`, `data-copy-feedback`.

- [ ] **Step 1: Add failing MVC contract tests**

Prove helpers are absent with demo mode off, four fill buttons render with it on, login still calls the normal authentication service, each workspace names the correct next persona, audit IDs render in native details, null legacy IDs create no copy button, active orders expose recovery hooks, and terminal orders stop polling.

- [ ] **Step 2: Implement persona fill controls and role guide**

Use native buttons inside a fieldset. JS fills username/password, dispatches input/change, updates `aria-pressed`, and announces that the user must still sign in. It must never submit the form. Render the guide fragment only when `demoMode=true`.

- [ ] **Step 3: Expose technical audit evidence**

Render event/correlation IDs in `<details>` per history row. Add delegated copy behavior using Clipboard API with a selection fallback and polite announcement.

- [ ] **Step 4: Make polling failure honest and recoverable**

Handle HTMX response, send, and timeout errors. Keep the last valid timeline, show an interrupted state and Retry now button, retain automatic polling, preserve 401 redirect, exempt the retry button from focus-blocked polling, and announce reconnection.

- [ ] **Step 5: Add responsive, theme-aware styles**

Use existing tokens, 44px controls, visible focus, wrapping code identifiers, desktop two-column persona layout, one column below 720px, stacked guides, and reduced-motion compatibility.

- [ ] **Step 6: Run MVC/module tests and browser checks**

Verify keyboard persona selection, no automatic authentication, audit copy fallback, disconnected/reconnected polling, 375x812 overflow, light/dark themes, and reduced motion.

- [ ] **Step 7: Commit the guided demo**

```bash
git add web-ui-service
git commit -m "feat(ui): guide the demo"
```

### Task 10: Add Showcase Evidence and Reproduction Docs

**Files:**
- Create: `docs/showcase/customer-order-timeline.png`
- Create: `docs/showcase/warehouse-queue.png`
- Create: `docs/showcase/delivery-queues.png`
- Create: `docs/showcase/order-lifecycle.gif`
- Create: `docs/runbooks/showcase-evidence.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: isolated Compose project `orderflow-evidence`, port `18080`, seeded `ELEC-AUR-1001`.
- Produces: truthful, reproducible 1440x900 light-theme evidence with no passwords.

- [ ] **Step 1: Start an isolated evidence stack**

Set gateway/discovery ports to `18080`/`18761`, use `docker compose -p orderflow-evidence`, and never touch the user's default Compose volumes.

- [ ] **Step 2: Capture the deterministic scenario**

Use separate customer/warehouse/delivery browser contexts at 1440x900, scale 1, light theme, reduced motion. Create two one-unit Aurora headphone orders. Capture warehouse with two confirmed orders, delivery with one packaged and one shipped order, and the final delivered customer timeline with Technical audit expanded.

- [ ] **Step 3: Assemble the lifecycle recording**

Capture equal-size confirmed, packaged, shipped, and delivered frames. Build a 1200px-wide, 128-color looping GIF with fixed two-second frames. Require a final size below 5 MiB.

- [ ] **Step 4: Rewrite the README proof entry**

Place `Proof in 60 seconds` after the opening. Add descriptive alt text and an evidence table linking the ADR, architecture, acceptance runner, recovery runbook, and reproduction runbook. Keep `Run it` canonical, update persona wording, demo-mode description, consumer-owned DLT behavior, recovery procedure, and CI acceptance claim.

- [ ] **Step 5: Document exact reproduction and clean up**

Record scenario, viewport, product, theme, filenames, and GIF command. Tear down only `orderflow-evidence` with its volumes.

- [ ] **Step 6: Verify assets and commit**

Check image dimensions/nonzero sizes, GIF size, README relative links/anchors, Compose config, and `git diff --check`.

```bash
git add README.md docs/showcase docs/runbooks/showcase-evidence.md
git commit -m "docs: add showcase evidence"
```

### Task 11: Publish the MIT License

**Files:**
- Create: `LICENSE`
- Modify: `README.md`

**Interfaces:**
- Produces: standard MIT terms attributed to Mahan Fatehian in 2026.

- [ ] **Step 1: Add the canonical license text**

Use the unmodified MIT text with `Copyright (c) 2026 Mahan Fatehian`.

- [ ] **Step 2: Link it from repository policies**

Begin the README policy paragraph with `Order/flow is licensed under the [MIT License](LICENSE).`

- [ ] **Step 3: Verify exact title, attribution, link, and whitespace**

Expected: all assertions pass and no other file is staged.

- [ ] **Step 4: Commit the license**

```bash
git add LICENSE README.md
git commit -m "docs: add project license"
```

### Task 12: Final Clean-Clone Verification and History Audit

**Files:**
- Verify only; modify a prior task's files only if a newly discovered defect requires a focused follow-up commit.

**Interfaces:**
- Produces: clean `dev` branch with push-ready atomic commits.

- [ ] **Step 1: Run the complete Java reactor in the Java 21 Maven container**

Copy the read-only source mount into the container filesystem before `mvn -B -ntp clean verify` to avoid Windows bind-mount cleanup errors. Mount the Docker socket when Testcontainers tests execute.

- [ ] **Step 2: Validate configuration and security tooling**

Run Compose config, actionlint, the Gitleaks canary, current-tree scan, and redacted history scan.

- [ ] **Step 3: Run a fresh isolated Compose Saga**

Build every service, wait for health, run `full-saga.py`, inspect metrics, capture failure logs if needed, and always tear down disposable volumes.

- [ ] **Step 4: Run browser acceptance**

Verify customer, warehouse, delivery, and admin journeys at desktop/mobile in light/dark mode, including technical audit and poll recovery.

- [ ] **Step 5: Audit Git state and authorship**

Run `git diff --check`, `git status --short`, and inspect every new commit's hash, subject, author name, and email. Expected: empty status and only short, issue-scoped commits after the approved design/plan commits.
