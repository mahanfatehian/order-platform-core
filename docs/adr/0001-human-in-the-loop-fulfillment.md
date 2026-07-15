# ADR-0001: Authorize fulfillment with HTTP commands and publish Kafka facts

- **Status:** Accepted
- **Date:** 2026-07-15
- **Decision owners:** Order platform maintainers

## Context

The original order flow progressed through machine-to-machine events immediately after checkout. That demonstrated asynchronous inventory processing, but it did not model the people who package an order, hand it to a carrier, and confirm delivery.

Adding human operators introduces an authority question. A Kafka record is durable delivery of data, but it is not by itself proof that an authenticated warehouse or delivery user was allowed to perform an action. Letting any producer publish an event that mutates order state would blur ownership, make role enforcement difficult to audit, and allow steps to be skipped.

The order aggregate must also remain safe under repeated form submissions, concurrent operators, Kafka redelivery, and partial infrastructure failure. Inventory must stay reserved while fulfillment is in progress, return to availability on an eligible cancellation, and become permanently consumed only when delivery is complete.

## Decision

### 1. The order service owns the lifecycle state machine

Only `order-service` may commit an order status. The supported forward path is:

```text
PENDING -> CONFIRMED -> PACKAGED -> SHIPPED -> DELIVERED
```

`PENDING -> FAILED` is driven by an insufficient-stock result or by the bounded pending-order reconciliation deadline. Cancellation is allowed only from `PENDING` or `CONFIRMED`; both transition to `CANCELLED`.

No API accepts an arbitrary target status.

### 2. Human actions enter as authenticated HTTP commands

The command endpoints and required roles are:

| Command | Role | Required state | Result |
| --- | --- | --- | --- |
| Pack | `ROLE_WAREHOUSE` | `CONFIRMED` | `PACKAGED` |
| Ship | `ROLE_DELIVERY` | `PACKAGED` | `SHIPPED` |
| Deliver | `ROLE_DELIVERY` | `SHIPPED` | `DELIVERED` |

Customer/admin cancellation is a command with its own ownership and role checks. `ROLE_ADMIN` does not implicitly grant warehouse or delivery authority.

For an accepted fulfillment command, `order-service` obtains a pessimistic lock on the order, verifies the exact source state, commits the destination state and audit row, and writes the corresponding outbox fact in one transaction. Repeating the same command against its already-achieved destination is idempotent; conflicting state or shipment data returns HTTP `409`.

### 3. Kafka messages are past-tense facts

The resulting facts are `OrderPackagedEvent`, `OrderShippedEvent`, and `OrderDeliveredEvent` on `order.events`. They describe transitions that the order service has already authorized and committed.

The order service's listener for these fulfillment events is observational: it records inbox processing and acknowledges retries, but never changes order state. Publishing a fulfillment-shaped Kafka message is therefore not an alternative command path.

The existing asynchronous inventory handshake remains event-driven:

- `OrderPlacedEvent` asks the store-side saga participant to attempt a reservation.
- `StockReservedEvent` causes `PENDING -> CONFIRMED`.
- `StockInsufficientEvent` causes `PENDING -> FAILED`.
- `OrderCancelledEvent` releases an active reservation.
- `OrderFailedEvent` releases an active reservation after the inventory handshake exceeds its reconciliation deadline.
- `OrderDeliveredEvent` settles an active reservation as consumed.

### 4. Every transition has durable history

`order_status_history` is append-only at the application/JPA boundary and stores event identity, source/destination status, actor identity/role or system label, reason, correlation ID, occurrence time, and record time. Customers can read their own history; admin, warehouse, and delivery users can read history needed for operations.

### 5. Reservation settlement is explicit

An inventory reservation has three states:

- `RESERVED`: units are unavailable to other orders but still part of total on-hand stock.
- `RELEASED`: cancellation removed the hold without reducing total stock.
- `CONSUMED`: delivery reduced both total and reserved quantities, preserving a durable settlement record.

Cancellation and delivery consumers change only `RESERVED` rows. This makes duplicate delivery/cancellation harmless and prevents a late cancellation from restoring delivered stock.

### 6. Delivery is at least once, with local atomicity

Order and store services use transactional outboxes, Kafka keys based on order ID, and inbox/processed-event tables. Aggregate-order queries prevent a later event for one order from overtaking any earlier unpublished event, including a dead-lettered row. Publisher retries use capped exponential backoff with jitter. A broker-acknowledged message can still be published twice if the publisher dies before marking its outbox row; consumers use event ID and Kafka position to avoid applying work twice.

Orders that remain `PENDING` beyond the configurable deadline (10 minutes by default) are locked and transitioned to `FAILED` by a reconciliation worker. The same transaction appends history and writes `OrderFailedEvent`, allowing store-side reservations to be released even when the original stock result never completed the handshake.

Consumer failures use bounded retry and then the source topic's `.dlt`. Publisher failures use bounded attempts and a database `dead_lettered` flag. These mechanisms are intentionally described as at-least-once delivery, not end-to-end exactly once.

## Consequences

### Positive

- Role authorization and current-state validation happen at a synchronous boundary that can return a clear result to the operator.
- The order service remains the single writer and source of truth for lifecycle state.
- Kafka preserves decoupled downstream reactions and a visible event trail without becoming an authorization bypass.
- Human transitions, system inventory results, and cancellations share one queryable history model and correlation IDs.
- Pessimistic locks, idempotent command behavior, outbox ordering, and consumer inboxes tolerate concurrent operators and redelivery.
- Inventory distinguishes a temporary hold from a delivered sale, so operational stock values remain explainable.

### Trade-offs and risks

- An HTTP command succeeds when the order transaction and outbox row commit, not when every downstream consumer finishes.
- The BFF must handle `409` conflicts when two operators act on the same queue item.
- Kafka and the database can temporarily disagree until an outbox row publishes and consumers catch up.
- A delivery event can be committed while store settlement later exhausts retries. The current system then needs operator investigation of the DLT and inventory state.
- Database-dead-lettered outbox rows and Kafka DLT records have no automated replay workflow in this repository.
- Pending inventory handshakes are automatically reconciled, but database dead letters and Kafka DLT records still require operator intervention.
- The local Compose Kafka listener is plaintext and relies on the private network; production requires broker TLS, authentication, and topic ACLs.

## Alternatives considered

### Publish fulfillment commands directly to Kafka

Rejected. It would require a separate authenticated command ingress and authorization proof, would make immediate operator feedback harder, and would allow broker producers to compete with the order service for lifecycle authority.

### Let the BFF update order status and then publish

Rejected. The BFF does not own the aggregate or database, and a direct state/event dual write would create split-brain failure modes.

### Treat fulfillment events as commands in the order consumer

Rejected. A past-tense name would hide command semantics, Kafka producer access would become order mutation authority, and reprocessing could apply a human action without revalidating role/current state.

### Use a distributed transaction across order, store, and Kafka

Rejected for this showcase. It would tightly couple independent services and infrastructure. Local transactions plus outbox/inbox patterns make the actual consistency boundary visible and testable.

### Introduce a workflow engine

Deferred. A workflow engine could add deadlines, retries, reconciliation, and operator tasks, but it is not needed to express the current five-step lifecycle and would add operational scope not implemented by this repository.

## Follow-up constraints

- New human lifecycle actions must enter through an authenticated command boundary owned by the aggregate service.
- New facts must be registered in `KafkaEventRegistry`, added to `docs/asyncapi.yaml`, and written through an outbox.
- Breaking event changes require an explicit schema-version and stored-message compatibility plan.
- A future replay/reconciliation feature must preserve event IDs, per-order ordering, history integrity, and `CONSUMED` inventory semantics.
