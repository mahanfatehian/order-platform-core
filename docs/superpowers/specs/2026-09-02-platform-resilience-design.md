# Platform Resilience Follow-up Design

## Objective

Strengthen four observable failure boundaries in Order/flow without changing its public architecture or adding infrastructure. The work must remain easy to review: one implementation commit for order creation, Kafka topic configuration, logout safety, and demo lifecycle tooling.

## Constraints

- Keep Java 21, Spring Boot 3.3.5, PostgreSQL, Redis, Kafka, Thymeleaf, and Docker Compose.
- Preserve every existing HTTP and event payload contract.
- Do not rewrite applied Flyway migrations or require a database migration.
- Use the repository's configured Git identity and short, single-line commit subjects without attribution trailers.
- Develop test-first and run the complete Maven reactor plus Compose validation before integration.
- Do not push remote branches.

## 1. Idempotent order creation without remote work in a transaction

`OrderService.createOrder` currently acquires a transaction-scoped PostgreSQL advisory lock before calling Store for an authoritative quote. A slow Store call therefore consumes an Order database connection and holds the keyed lock for the duration of the network wait. The same method also returns an existing order for a reused idempotency key without checking whether the new basket matches the original request.

The orchestration will normalize the basket first and perform a fast existing-order lookup. An existing order is returned only when its product/quantity map exactly matches the normalized request; otherwise the existing `IdempotencyConflictException` produces a conflict response. When no order exists, the Store quote is loaded before entering a `TransactionOperations` callback. Inside that transaction, the service acquires the advisory lock, rechecks the key to close the race between concurrent requests, repeats the payload comparison, and atomically writes the order, history, and outbox event.

This preserves fast, availability-independent replays: a matching committed order can be returned even if Store is currently unavailable. Concurrent first attempts may both obtain a quote, but only one can persist; the second sees the winner after acquiring the advisory lock.

## 2. One Kafka topic configuration contract

The topic provisioner accepts custom `kafka.topics.*` values, while outbox writers and `@KafkaListener` declarations use `KafkaTopics` defaults. That split makes a documented configuration appear valid while producers and consumers continue using different topics.

`KafkaTopicConfig` will expose an immutable `KafkaTopicNames` bean containing the resolved order and store event names. Outbox writers will use that bean, and listener annotations will resolve the same bean through Spring expression syntax. Consumer-owned and legacy DLT beans will derive names from the resolved source topic. Defaults remain `order.events` and `store.events`, so existing deployments require no changes. Compose will pass optional topic environment values into both event-owning services, and documentation will identify the supported override.

Tests will use non-default names and assert provisioning, outbox destinations, listener resolution, and DLT derivation. This makes the configuration executable rather than decorative.

## 3. Logout must not claim success before revocation succeeds

The BFF currently catches every backend logout failure, clears its local token pair, invalidates the HTTP session, expires the cookie, and redirects with a success message. During an Auth or Redis outage, the browser loses its only retry path while a copied refresh token can remain valid.

Spring Security's built-in logout filter will be disabled for this route and a CSRF-protected MVC `POST /logout` controller will own the sequence. It first asks Auth to revoke the token. Only after that succeeds will it clear local tokens, invalidate the session, clear the security context, expire the cookie, and redirect to `/login?logout`. Backend or transport failures flow through the existing page exception handler as `503 Service Unavailable`; the session and cookie remain intact so the user can retry.

Tests will prove both halves: the authentication service retains real session tokens when its backend dependency fails, and the MVC route returns 503 without invalidating or expiring the session. The existing successful logout behavior remains covered.

## 4. Demo lifecycle scripts honor local configuration

The README tells users to copy `.env.example` to `.env`, but all six lifecycle scripts hard-code `.env.example`. A user following the documented path therefore sees their ports, feature flags, and credentials ignored.

The POSIX and PowerShell scripts will share platform-specific helpers with identical precedence:

1. `ORDER_PLATFORM_ENV_FILE` when explicitly set;
2. repository-root `.env` when present;
3. `.env.example` otherwise.

The selected file must exist, and every start, stop, and reset invocation must pass the same resolved value to Compose. The scripts will print the selected source. A portable Python contract test will execute the scripts against a fake `docker` command in an isolated temporary repository and assert the arguments, including reset confirmation behavior. CI will run that contract and will also verify pushes to `dev`, which is the repository's active integration branch. The README's unreachable `localhost:8085` sign-in example will be corrected to the gateway at `localhost:8080`.

## Verification and delivery

Each boundary receives a failing regression test before production changes, a focused green run, an independent task review, and one short implementation commit. A final reviewer examines the complete range. The final gate is the full Maven reactor, the demo-script contract, `git diff --check`, and `docker compose --env-file .env.example config --quiet`. The verified commits are then fast-forwarded onto local `dev`; no push is performed.
