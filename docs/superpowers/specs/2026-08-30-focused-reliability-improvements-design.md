# Focused Reliability and Performance Improvements

**Date:** 2026-08-30  
**Status:** Approved for implementation  
**Owner:** Mahan Fatehian

## Context

`order-platform-core` already demonstrates transactional outboxes, idempotent Kafka consumers, JWT revocation, Redis-backed abuse controls, and scheduled event retention. The next improvements should strengthen those existing mechanisms without introducing a new platform dependency or broadening the product scope.

This design covers four independently measurable changes. Each implementation remains small enough to review and revert on its own, and each will be delivered as a focused commit with its own tests.

## Goals

- Remove a partial-failure window from Redis-backed abuse counters.
- Reduce Redis work on every authenticated request while preserving fail-closed revocation.
- Give each Kafka consumer service an independently operable dead-letter stream.
- Bound retention work so a backlog cannot create one large database transaction.
- Preserve current APIs and the Docker Compose developer experience.

## Non-goals

- Migrating the deployment to Redis Cluster.
- Building a dead-letter replay user interface.
- Changing Kafka event payload schemas or normal event topic names.
- Adding a distributed scheduler coordinator.
- Replacing the existing outbox/inbox model.
- Changing authentication or retention policies themselves.

## Change 1: Atomic abuse-counter expiry

### Problem

`AttemptCounterStore` currently increments a Redis counter and assigns its expiry in two client calls. If Redis applies the increment but the process fails or times out before the expiry call completes, the key can remain indefinitely. Because reads take the maximum of the Redis and local fallback counts, this stale shared value can keep login captcha or rate-limit enforcement active beyond the configured window.

The current success path can also under-report immediately after Redis recovers because it returns only the shared value, ignoring attempts accumulated in the local fallback.

### Design

- Replace the separate `INCR` and expiry calls with a single Redis Lua script.
- The script will increment one key, apply a millisecond TTL for the configured window, and return the new count.
- On Redis success, return the maximum of the shared result and the local fallback count.
- On Redis failure, retain the existing local fallback behavior.
- Incrementing an old key that has no TTL will repair it by assigning the configured expiry.
- Keep the script single-key so the operation remains compatible with Redis Cluster key-slot rules even though the checked-in deployment uses standalone Redis.

### Measurement and verification

- One Redis operation replaces two operations per successful increment.
- Unit tests will verify script use, absence of standalone increment/expiry calls, and local fallback behavior.
- A real-Redis test will verify TTL creation, expiry renewal, key expiration, and repair of a pre-existing no-TTL key.

## Change 2: Atomic token-state validation

### Problem

The gateway and shared security decoder each validate an access token using two sequential Redis reads: one for the access-token blacklist and one for the user's token version. This creates four Redis commands across a normal gateway-to-service request and permits the two state reads at each hop to observe different revocation moments.

### Design

- Add a backward-compatible combined validation operation to `TokenBlacklistService`.
- Its default implementation will compose the existing methods so alternative implementations remain source-compatible.
- `RedisTokenBlacklistService` will override it with one Lua script that reads the expected user token version and checks the access-token blacklist atomically.
- Both `GatewaySecurityConfig` and `SecurityAutoConfiguration` will use the combined operation.
- A missing or malformed version, version mismatch, blacklisted JTI, or Redis failure will reject the request. Existing fail-closed semantics remain unchanged.
- Existing individual methods remain available because refresh and logout flows still require them.

### Deployment constraint

The existing version and blacklist keys do not share a Redis Cluster hash tag. Existing refresh rotation and logout scripts already have the same multi-key constraint, and Docker Compose uses standalone Redis. This change therefore targets the supported checked-in deployment and documents that Redis Cluster would require a coordinated key-schema migration.

### Measurement and verification

- Gateway plus downstream validation falls from four Redis commands to two per request.
- Tests will cover valid state, blacklisting, version mismatch, missing/malformed state, and Redis errors.
- Wiring tests will verify that the gateway and shared decoder use the combined method rather than issuing the two legacy reads.

## Change 3: Consumer-owned Kafka dead-letter topics

### Problem

Multiple consumer groups process `order.events`, but failed records currently converge on the same source-level dead-letter topic. Kafka preserves the original consumer-group metadata, yet a shared topic still mixes operational ownership, permissions, alerts, retention, and manual replay workflows.

### Design

- Derive dead-letter destinations from both the source topic and consuming application: `<source-topic>.<application-name>.dlt`.
- Validate topic and owner components before constructing a destination.
- Configure each service's recoverer with its own application name and preserve the source partition.
- Provision explicit topics with the same partition count as their source topic:
  - `order.events.order-service.dlt`
  - `order.events.store-service.dlt`
  - `store.events.order-service.dlt`
- Preserve the legacy shared dead-letter topics during the compatibility window; only newly failed records use consumer-owned topics.
- Update operational documentation and AsyncAPI descriptions so replay ownership is unambiguous.

### Measurement and verification

- Each active source-topic/consumer pair has one exclusive dead-letter destination.
- Unit tests will verify naming validation and destination selection.
- Kafka integration coverage will publish poison records for the two `order.events` consumer groups and assert that failures land in separate topics with original metadata intact.

## Change 4: Bounded event-retention batches

### Problem

Order and store retention jobs currently delete all eligible outbox and inbox history in one scheduled transaction. Existing indexes make row selection efficient, but they do not bound deleted rows, transaction duration, generated WAL, dead tuples, or the work performed after a long backlog.

### Design

- Keep each `EventRetentionService` as a non-transactional scheduler/coordinator.
- Introduce a separate batch service so Spring can apply `REQUIRES_NEW` to every batch through a bean proxy.
- Delete rows with a PostgreSQL CTE that selects an ordered, limited batch and uses `FOR UPDATE SKIP LOCKED`.
- Commit outbox and inbox batches independently.
- Capture one stable cutoff at the beginning of a run.
- Continue until both sources are empty or a configurable maximum number of batches is reached.
- Default to 500 rows per batch and 20 batches per run for each source, bounding a scheduled run to 10,000 outbox and 10,000 inbox rows per service.
- Expose batch size and maximum batches through service configuration and Docker Compose environment variables.
- Reuse the existing retention indexes; no schema migration is required.

### Measurement and verification

- No retention transaction deletes more than the configured batch size.
- No scheduled invocation performs more than the configured batch count for either source.
- Unit tests will verify early termination, maximum enforcement, stable cutoffs, and continued draining when only one source has work.
- PostgreSQL integration tests will verify exact batch boundaries, eligibility rules, and independent transaction visibility.

## Delivery sequence

The implementation will use one focused commit per fix:

1. `fix(web-ui): expire abuse counters atomically`
2. `perf(security): validate token state in one Redis round trip`
3. `fix(kafka): isolate dead letters by consumer`
4. `perf(retention): delete event history in bounded batches`

Tests will be written before or alongside each implementation and committed with the behavior they verify. Documentation and configuration directly required by a fix will remain in that fix's commit.

## Compatibility and rollback

- No public HTTP contract or Kafka payload schema changes.
- Lua-script failures retain existing fail-closed or local-fallback behavior, depending on the subsystem.
- Legacy dead-letter topics remain available while new failures are redirected.
- Retention defaults can be tuned through environment variables without rebuilding images.
- Each implementation commit can be reverted independently.

## Completion criteria

- All affected module tests pass.
- New Redis, Kafka, and PostgreSQL integration tests pass when Docker is available.
- The full Maven reactor passes.
- Docker Compose configuration renders successfully and documented environment variables match service configuration.
- Git authorship uses the repository-configured Mahan Fatehian identity and contains no attribution trailers.
