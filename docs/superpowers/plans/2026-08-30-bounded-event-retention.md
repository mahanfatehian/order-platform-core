# Bounded Event-Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound order and store inbox/outbox cleanup by rows per transaction and batches per scheduled run.

**Architecture:** Each scheduler becomes a non-transactional coordinator using one stable clock reading. A separate proxied batch service commits every PostgreSQL CTE delete with `REQUIRES_NEW`; `FOR UPDATE SKIP LOCKED` lets scaled instances take disjoint work without waiting.

**Tech Stack:** Java 21, Spring Scheduling, Spring Data JPA, PostgreSQL 16, Testcontainers, JUnit 5, Mockito

**Spec:** `docs/superpowers/specs/2026-08-30-focused-reliability-improvements-design.md`

## Global Constraints

- Keep the existing 30-day defaults and nightly cron schedules.
- Never delete unpublished or dead-lettered outbox rows.
- Default to 500 rows per transaction and at most 20 batches per source per run.
- Reuse the existing retention indexes; add no Flyway migration.
- Use the repository-configured Mahan Fatehian Git identity with no attribution trailers.
- Deliver both services, configuration, tests, and docs in one implementation commit: `perf(retention): delete event history in bounded batches`.

---

### Task 1: Specify the bounded coordinators

**Files:**
- Create: `order-service/src/test/java/com/orderprocessing/orderservice/service/EventRetentionServiceTest.java`
- Create: `store-service/src/test/java/com/orderprocessing/storeservice/service/EventRetentionServiceTest.java`

**Interfaces:**
- Consumes: `EventRetentionBatchService`, two retention durations, batch size, max batches, and fixed `Clock`
- Produces: deterministic cleanup-loop expectations in each service

- [ ] **Step 1: Add order coordinator tests**

Use `Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC)`, batch size 2, and max batches 3. Verify:

```java
private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
private static final Duration OUTBOX_RETENTION = Duration.ofDays(30);
private static final Duration INBOX_RETENTION = Duration.ofDays(14);
private static final Instant OUTBOX_CUTOFF = NOW.minus(OUTBOX_RETENTION);
private static final Instant INBOX_CUTOFF = NOW.minus(INBOX_RETENTION);

when(batches.deleteOutboxBatch(OUTBOX_CUTOFF, 2)).thenReturn(2, 2, 2);
when(batches.deleteInboxBatch(INBOX_CUTOFF, 2)).thenReturn(2, 2, 2);
service.clean();
verify(batches, times(3)).deleteOutboxBatch(OUTBOX_CUTOFF, 2);
verify(batches, times(3)).deleteInboxBatch(INBOX_CUTOFF, 2);
```

Add cases that stop after both methods return zero, continue when only inbox returns rows, and reject non-positive batch size or maximum.

- [ ] **Step 2: Add equivalent store coordinator tests**

Use the store package's `EventRetentionBatchService` type and identical behavioral assertions. The tests must verify a single fixed `now` supplies both cutoffs rather than calling the system clock between deletes.

- [ ] **Step 3: Run both tests and verify red**

Run:

```powershell
mvn -pl order-service,store-service -am -Dtest=EventRetentionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the batch services, bounded constructors, and loop behavior do not exist.

### Task 2: Add transactional batch services and bounded SQL

**Files:**
- Create: `order-service/src/main/java/com/orderprocessing/orderservice/service/EventRetentionBatchService.java`
- Create: `store-service/src/main/java/com/orderprocessing/storeservice/service/EventRetentionBatchService.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/repository/OutboxEventRepository.java`
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/repository/ProcessedKafkaEventRepository.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/repository/StoreOutboxEventRepository.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/repository/ProcessedKafkaEventRepository.java`

**Interfaces:**
- Produces: `deleteOutboxBatch(Instant cutoff, int batchSize): int`
- Produces: `deleteInboxBatch(Instant cutoff, int batchSize): int`

- [ ] **Step 1: Replace the two order repository deletes**

Rename the methods to `deletePublishedBatchBefore` and `deleteProcessedBatchBefore`, add `@Param("batchSize") int batchSize`, and use:

```sql
WITH candidates AS (
    SELECT id
    FROM outbox_events
    WHERE published = TRUE AND published_at < :cutoff
    ORDER BY published_at
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
)
DELETE FROM outbox_events event
USING candidates
WHERE event.id = candidates.id
```

For `processed_kafka_events`, select and join `event_id`, filter `processed_at < :cutoff`, and order by `processed_at`. Keep `@Modifying` and `nativeQuery = true`.

- [ ] **Step 2: Replace the two store repository deletes**

Use the same SQL, replacing only the outbox table with `store_outbox_events`. The inbox table remains `processed_kafka_events` within the store database.

- [ ] **Step 3: Add the order batch bean**

Create a `@Component` whose methods are:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public int deleteOutboxBatch(Instant cutoff, int batchSize) {
    return outbox.deletePublishedBatchBefore(cutoff, batchSize);
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public int deleteInboxBatch(Instant cutoff, int batchSize) {
    return inbox.deleteProcessedBatchBefore(cutoff, batchSize);
}
```

- [ ] **Step 4: Add the store batch bean**

Repeat the bean with `StoreOutboxEventRepository`. Keep it separate from the coordinator so Spring proxy interception cannot be bypassed by self-invocation.

### Task 3: Implement the bounded coordinators

**Files:**
- Modify: `order-service/src/main/java/com/orderprocessing/orderservice/service/EventRetentionService.java`
- Modify: `store-service/src/main/java/com/orderprocessing/storeservice/service/EventRetentionService.java`

**Interfaces:**
- Consumes: the batch APIs from Task 2
- Produces: `clean()` with bounded work and no surrounding transaction

- [ ] **Step 1: Replace repository dependencies with the order batch bean**

The public Spring constructor accepts the batch bean, retention values, `cleanup-batch-size`, and `cleanup-max-batches-per-run`, delegating to a package-private constructor that also accepts a `Clock`. Validate both integers are greater than zero and store `Clock.systemUTC()` in production.

- [ ] **Step 2: Implement the order loop**

Remove `@Transactional`, capture `Instant now = clock.instant()` once, derive both cutoffs, and run:

```java
for (int batch = 0; batch < maxBatchesPerRun; batch++) {
    int outboxRows = batches.deleteOutboxBatch(outboxCutoff, batchSize);
    int inboxRows = batches.deleteInboxBatch(inboxCutoff, batchSize);
    outboxTotal += outboxRows;
    inboxTotal += inboxRows;
    if (outboxRows == 0 && inboxRows == 0) {
        break;
    }
}
```

Log the accumulated totals once when any rows were removed.

- [ ] **Step 3: Implement the store loop**

Use identical behavior while retaining the store default cron `0 30 3 * * *`.

- [ ] **Step 4: Run coordinator tests**

Run the Task 1 command. Expected: PASS.

### Task 4: Prove batch boundaries and transaction isolation

**Files:**
- Create: `order-service/src/test/java/com/orderprocessing/orderservice/support/PostgresAvailability.java`
- Create: `order-service/src/test/java/com/orderprocessing/orderservice/service/EventRetentionBatchServiceIntegrationTest.java`
- Create: `store-service/src/test/java/com/orderprocessing/storeservice/service/EventRetentionBatchServiceIntegrationTest.java`

**Interfaces:**
- Consumes: the production repositories, batch beans, Flyway schema, and PostgreSQL 16
- Produces: database-level proof of exact batch limits and `REQUIRES_NEW`

- [ ] **Step 1: Add the order Docker-availability helper**

Mirror the store helper: return true for an externally supplied `task5.postgres.url`; otherwise return `DockerClientFactory.instance().isDockerAvailable()`, catching runtime failures.

- [ ] **Step 2: Create both PostgreSQL fixtures**

Use the following annotation with the exact helper in each module:

```java
@EnabledIf(value = "com.orderprocessing.orderservice.support.PostgresAvailability#present",
        disabledReason = "Needs Docker for Testcontainers, or -Dtask5.postgres.url")
@DataJpaTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.datasource.hikari.maximum-pool-size=3"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EventRetentionBatchService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
```

The store class uses `com.orderprocessing.storeservice.support.PostgresAvailability#present`; all remaining annotations are identical.

Use the repository's existing PostgreSQL 16 container/property pattern and a `NamedParameterJdbcTemplate` built from the test `DataSource` to execute the named-parameter seed statements below.

- [ ] **Step 3: Test exact eligibility and batching**

For each module, seed three published outbox rows older than a fixed cutoff, one old unpublished row, one old dead-lettered unpublished row, one published row exactly at cutoff, three old inbox rows, and one inbox row exactly at cutoff. Use these complete insert shapes so every non-null constraint is explicit:

```sql
-- order-service
INSERT INTO outbox_events
    (id, aggregate_type, aggregate_id, event_type, payload, created_at,
     published, topic, published_at, attempt_count, dead_lettered)
VALUES
    (:id, 'Order', :aggregateId, 'RetentionTestEvent', '{}', :createdAt,
     :published, 'order.events', :publishedAt, 0, :deadLettered);

-- store-service
INSERT INTO store_outbox_events
    (id, aggregate_id, topic, event_type, payload, created_at,
     published, published_at, attempt_count, dead_lettered)
VALUES
    (:id, :aggregateId, 'store.events', 'RetentionTestEvent', '{}', :createdAt,
     :published, :publishedAt, 0, :deadLettered);

-- both service databases
INSERT INTO processed_kafka_events
    (event_id, event_type, topic, partition_number, record_offset, processed_at)
VALUES
    (:eventId, 'RetentionTestEvent', 'retention.test', 0, :recordOffset, :processedAt);
```

With batch size 2, assert successive delete results are 2, 1, and 0 for each eligible source and assert boundary/ineligible rows remain.

- [ ] **Step 4: Test independent commits**

Seed one eligible row, open an outer `TransactionTemplate`, call `batchService.deleteOutboxBatch(cutoff, 1)`, and query from a separate connection before rolling back the outer transaction. Assert the separate connection sees zero eligible rows and that the deletion survives the outer rollback. This fails if `REQUIRES_NEW` is absent or self-invoked.

- [ ] **Step 5: Run integration tests**

Run:

```powershell
mvn -pl order-service -am -Dtest=EventRetentionBatchServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl store-service -am -Dtest=EventRetentionBatchServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS with disposable PostgreSQL, or SKIP when Docker/external PostgreSQL is unavailable.

### Task 5: Expose safe operational limits

**Files:**
- Modify: `order-service/src/main/resources/application.yml`
- Modify: `store-service/src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/architecture.md`

**Interfaces:**
- Produces: eight newly exposed Compose/example environment variables and documented row bounds

- [ ] **Step 1: Add service properties**

Under each `maintenance` block add:

```yaml
cleanup-batch-size: ${ORDER_EVENT_CLEANUP_BATCH_SIZE:500}
cleanup-max-batches-per-run: ${ORDER_EVENT_CLEANUP_MAX_BATCHES_PER_RUN:20}
```

Use the `STORE_` prefix in store-service.

- [ ] **Step 2: Add Compose and example environment values**

Keep the two existing outbox-retention values. Add inbox retention, cleanup cron, batch size, and max batches for both services: `ORDER_INBOX_RETENTION=P30D`, `ORDER_EVENT_CLEANUP_CRON=0 15 3 * * *`, `ORDER_EVENT_CLEANUP_BATCH_SIZE=500`, `ORDER_EVENT_CLEANUP_MAX_BATCHES_PER_RUN=20`, and the four equivalent `STORE_` values with the `0 30 3 * * *` cron.

- [ ] **Step 3: Update operator documentation**

Extend README's configuration table with inbox retention, cleanup cron, batch size, and max batches. Update architecture retention text to state that each source deletes at most 10,000 rows per run by default, in independently committed batches of 500, and remaining backlog waits for the next schedule.

- [ ] **Step 4: Validate Compose rendering**

Run:

```powershell
docker compose --env-file .env.example config --quiet
```

Expected: exit code 0.

### Task 6: Run regression coverage and commit

**Files:**
- Modify/create: all files from Tasks 1–5

**Interfaces:**
- Consumes: completed retention implementation
- Produces: the fourth independently revertible performance commit

- [ ] **Step 1: Run affected suites**

Run:

```powershell
mvn -pl order-service,store-service -am test
```

Expected: BUILD SUCCESS; PostgreSQL tests skip only when no test database is available.

- [ ] **Step 2: Retain scheduler capacity coverage**

Run:

```powershell
mvn -pl order-service,store-service -am -Dtest=ScheduledTaskCapacityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS because this change adds no scheduled method.

- [ ] **Step 3: Check the patch and commit**

Run `git diff --check`, confirm all changes belong to this plan, stage them, then:

```powershell
git commit -m "perf(retention): delete event history in bounded batches"
```

Expected: one commit authored by `mahan fatehian <mahanfatehian@gmail.com>` with no attribution trailers.
