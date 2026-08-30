# Consumer-Owned Kafka Dead Letters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every exhausted Kafka record to a dead-letter topic owned by the service whose consumer failed.

**Architecture:** A shared validated naming helper and destination resolver will derive `<source-topic>.<spring.application.name>.dlt` while preserving the source partition and Spring Kafka metadata. The common topic configuration will provision all three active source/consumer combinations and retain the two legacy source-only DLTs for compatibility.

**Tech Stack:** Java 21, Spring Kafka, Embedded Kafka, JUnit 5, AssertJ, AsyncAPI 2.6

**Spec:** `docs/superpowers/specs/2026-08-30-focused-reliability-improvements-design.md`

## Global Constraints

- Do not change normal event topic names, payloads, keys, partitions, or retry count.
- Preserve original-record and failure headers supplied by `DeadLetterPublishingRecoverer`.
- Keep `order.events.dlt` and `store.events.dlt` provisioned but stop routing new failures to them.
- Use the repository-configured Mahan Fatehian Git identity with no attribution trailers.
- Deliver production code, tests, and documentation in one implementation commit: `fix(kafka): isolate dead letters by consumer`.

---

### Task 1: Specify and implement safe DLT naming

**Files:**
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/KafkaTopics.java`
- Create: `kafka-common/src/test/java/com/orderprocessing/kafkacommon/KafkaTopicsTest.java`

**Interfaces:**
- Consumes: source topic and consumer owner
- Produces: `KafkaTopics.deadLetterTopic(String sourceTopic, String consumerOwner): String`

- [ ] **Step 1: Add naming tests**

Assert:

```java
assertThat(KafkaTopics.deadLetterTopic("order.events", "order-service"))
        .isEqualTo("order.events.order-service.dlt");
assertThat(KafkaTopics.deadLetterTopic("order.events", "store-service"))
        .isEqualTo("order.events.store-service.dlt");
```

Use parameterized cases for null, blank, whitespace, `/`, `:`, and a combined name longer than Kafka's 249-character maximum; each must throw `IllegalArgumentException` naming the invalid argument.

- [ ] **Step 2: Run the naming test and verify red**

Run:

```powershell
mvn -pl kafka-common -Dtest=KafkaTopicsTest test
```

Expected: FAIL because `deadLetterTopic` and the owner constants do not exist.

- [ ] **Step 3: Add owner constants, active topics, and validation**

Add:

```java
public static final String ORDER_SERVICE = "order-service";
public static final String STORE_SERVICE = "store-service";
public static final String ORDER_EVENTS_ORDER_SERVICE_DLT =
        deadLetterTopic(ORDER_EVENTS, ORDER_SERVICE);
public static final String ORDER_EVENTS_STORE_SERVICE_DLT =
        deadLetterTopic(ORDER_EVENTS, STORE_SERVICE);
public static final String STORE_EVENTS_ORDER_SERVICE_DLT =
        deadLetterTopic(STORE_EVENTS, ORDER_SERVICE);
```

Keep existing `ORDER_EVENTS_DLT` and `STORE_EVENTS_DLT`. Implement:

```java
private static final Pattern VALID_TOPIC_COMPONENT = Pattern.compile("[A-Za-z0-9._-]+");

public static String deadLetterTopic(String sourceTopic, String consumerOwner) {
    requireTopicComponent(sourceTopic, "sourceTopic");
    requireTopicComponent(consumerOwner, "consumerOwner");
    String destination = sourceTopic + "." + consumerOwner + ".dlt";
    if (destination.length() > 249) {
        throw new IllegalArgumentException("dead-letter topic exceeds Kafka's 249-character limit");
    }
    return destination;
}

private static void requireTopicComponent(String value, String argument) {
    if (value == null || !VALID_TOPIC_COMPONENT.matcher(value).matches()) {
        throw new IllegalArgumentException(argument + " is not a valid Kafka topic component");
    }
}
```

- [ ] **Step 4: Re-run the naming test**

Expected: PASS.

### Task 2: Route failures with the consuming application name

**Files:**
- Create: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/ConsumerOwnedDeadLetterResolver.java`
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaConfig.java`
- Create: `kafka-common/src/test/java/com/orderprocessing/kafkacommon/config/ConsumerOwnedDeadLetterResolverTest.java`

**Interfaces:**
- Consumes: `ConsumerRecord<?, ?>`, ignored exception, and constructor owner
- Produces: `TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception)`

- [ ] **Step 1: Add resolver tests**

Construct a record on `order.events`, partition 2, then assert order-service and store-service resolver instances return their distinct topic names while both retain partition 2. Assert an invalid owner is rejected by the constructor.

- [ ] **Step 2: Run the resolver test and verify red**

Run:

```powershell
mvn -pl kafka-common -Dtest=ConsumerOwnedDeadLetterResolverTest test
```

Expected: FAIL because the resolver does not exist.

- [ ] **Step 3: Implement the focused resolver**

Create:

```java
public final class ConsumerOwnedDeadLetterResolver
        implements BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {
    private final String consumerOwner;

    public ConsumerOwnedDeadLetterResolver(String consumerOwner) {
        KafkaTopics.deadLetterTopic("validation", consumerOwner);
        this.consumerOwner = consumerOwner;
    }

    @Override
    public TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception) {
        Objects.requireNonNull(record, "consumerRecord");
        return new TopicPartition(
                KafkaTopics.deadLetterTopic(record.topic(), consumerOwner),
                record.partition());
    }
}
```

- [ ] **Step 4: Wire the application name into `KafkaConfig`**

Inject:

```java
@Value("${spring.application.name}")
private String applicationName;
```

Replace the inline `<source>.dlt` lambda with:

```java
DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        new ConsumerOwnedDeadLetterResolver(applicationName));
```

Do not change `FixedBackOff(1000L, 3)` or record acknowledgement mode.

- [ ] **Step 5: Re-run both unit tests**

Run:

```powershell
mvn -pl kafka-common -Dtest=KafkaTopicsTest,ConsumerOwnedDeadLetterResolverTest test
```

Expected: PASS.

### Task 3: Provision active and legacy destinations

**Files:**
- Modify: `kafka-common/src/main/java/com/orderprocessing/kafkacommon/config/KafkaTopicConfig.java`
- Create: `kafka-common/src/test/java/com/orderprocessing/kafkacommon/config/KafkaTopicConfigTest.java`

**Interfaces:**
- Consumes: configured source topic names, partition counts, and replica counts
- Produces: three active consumer-owned `NewTopic` beans plus two legacy beans

- [ ] **Step 1: Add topic-bean tests**

Instantiate `KafkaTopicConfig` after converting its six injected fields (two topic names, two partition counts, and two replica counts) to constructor arguments. Assert the five DLT bean methods produce these names with three partitions and one replica:

```java
public KafkaTopicConfig(
        @Value("${kafka.topics.order-events:order.events}") String orderEventsTopic,
        @Value("${kafka.topics.order-events-partitions:3}") int orderEventsPartitions,
        @Value("${kafka.topics.order-events-replicas:1}") int orderEventsReplicas,
        @Value("${kafka.topics.store-events:store.events}") String storeEventsTopic,
        @Value("${kafka.topics.store-events-partitions:3}") int storeEventsPartitions,
        @Value("${kafka.topics.store-events-replicas:1}") int storeEventsReplicas) {
    this.orderEventsTopic = orderEventsTopic;
    this.orderEventsPartitions = orderEventsPartitions;
    this.orderEventsReplicas = orderEventsReplicas;
    this.storeEventsTopic = storeEventsTopic;
    this.storeEventsPartitions = storeEventsPartitions;
    this.storeEventsReplicas = storeEventsReplicas;
}
```

The test constructs it as `new KafkaTopicConfig("order.events", 3, 1, "store.events", 3, 1)`.

```text
order.events.order-service.dlt
order.events.store-service.dlt
store.events.order-service.dlt
order.events.dlt
store.events.dlt
```

- [ ] **Step 2: Run the topic configuration test and verify red**

Run:

```powershell
mvn -pl kafka-common -Dtest=KafkaTopicConfigTest test
```

Expected: FAIL because consumer-owned `NewTopic` beans do not exist.

- [ ] **Step 3: Add explicit active-topic beans**

Add these three beans and retain `orderEventsDltTopic` and `storeEventsDltTopic` unchanged as legacy compatibility topics:

```java
@Bean
public NewTopic orderEventsOrderServiceDltTopic() {
    return TopicBuilder.name(KafkaTopics.deadLetterTopic(orderEventsTopic, KafkaTopics.ORDER_SERVICE))
            .partitions(orderEventsPartitions).replicas(orderEventsReplicas).build();
}

@Bean
public NewTopic orderEventsStoreServiceDltTopic() {
    return TopicBuilder.name(KafkaTopics.deadLetterTopic(orderEventsTopic, KafkaTopics.STORE_SERVICE))
            .partitions(orderEventsPartitions).replicas(orderEventsReplicas).build();
}

@Bean
public NewTopic storeEventsOrderServiceDltTopic() {
    return TopicBuilder.name(KafkaTopics.deadLetterTopic(storeEventsTopic, KafkaTopics.ORDER_SERVICE))
            .partitions(storeEventsPartitions).replicas(storeEventsReplicas).build();
}
```

- [ ] **Step 4: Re-run the topic configuration test**

Expected: PASS.

### Task 4: Prove two consumer groups are isolated

**Files:**
- Modify: `kafka-common/pom.xml`
- Create: `kafka-common/src/test/java/com/orderprocessing/kafkacommon/config/ConsumerOwnedDeadLetterRoutingIntegrationTest.java`

**Interfaces:**
- Consumes: two consumers of the same `order.events` record
- Produces: one exhausted record in each owner-specific DLT, on the source partition

- [ ] **Step 1: Add embedded Kafka test support**

Add `org.springframework.kafka:spring-kafka-test` with test scope to `kafka-common/pom.xml`.

- [ ] **Step 2: Build the two-group poison-record fixture**

Use `@EmbeddedKafka(partitions = 3, topics = {"order.events", "order.events.order-service.dlt", "order.events.store-service.dlt", "order.events.dlt"})`. Create two `KafkaMessageListenerContainer<String, String>` instances over `order.events`, with group IDs `order-service` and `store-service`. Give each a listener that throws `IllegalStateException("poison")` and a `DefaultErrorHandler` configured with its own `ConsumerOwnedDeadLetterResolver` and `new FixedBackOff(0L, 0L)`.

Start both containers, wait for assignment with `ContainerTestUtils.waitForAssignment`, and publish one keyed string record to source partition 1.

- [ ] **Step 3: Assert destinations and metadata**

Consume one record from each owner-specific DLT and assert:

```java
assertThat(orderDlt.partition()).isEqualTo(1);
assertThat(storeDlt.partition()).isEqualTo(1);
assertThat(header(orderDlt, KafkaHeaders.DLT_ORIGINAL_TOPIC))
        .isEqualTo("order.events");
assertThat(header(storeDlt, KafkaHeaders.DLT_ORIGINAL_TOPIC))
        .isEqualTo("order.events");
assertThat(header(orderDlt, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP))
        .isEqualTo("order-service");
assertThat(header(storeDlt, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP))
        .isEqualTo("store-service");
assertThat(header(orderDlt, KafkaHeaders.DLT_EXCEPTION_FQCN))
        .contains("IllegalStateException");
```

Also assert no record appears on legacy `order.events.dlt` within a short bounded poll. Stop both containers and close consumers/producers in `@AfterEach`.

- [ ] **Step 4: Run Kafka tests**

Run:

```powershell
mvn -pl kafka-common test
```

Expected: PASS, including separate DLT records for the two consumer groups.

### Task 5: Update the operator contract and commit

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/asyncapi.yaml`
- Modify/create: all Kafka files from Tasks 1–4

**Interfaces:**
- Consumes: completed routing implementation
- Produces: operator-visible topic ownership and the third independently revertible reliability commit

- [ ] **Step 1: Update README and architecture**

Replace the source-only DLT claim with the three active destinations. In the architecture topic table, list each active destination and owner; label `order.events.dlt` and `store.events.dlt` as legacy compatibility topics receiving no new failures. Update failure examples so delivery settlement points to `order.events.store-service.dlt`.

- [ ] **Step 2: Update AsyncAPI channels**

Add channels for:

```text
order.events.order-service.dlt
order.events.store-service.dlt
store.events.order-service.dlt
```

Give each a unique `operationId`, an `x-owner` matching the service, three Kafka partitions, and the existing dead-letter message schema for its source. Retain the two legacy channels with `x-legacy: true` and text stating that new failures are not routed there.

- [ ] **Step 3: Run verification**

Run:

```powershell
mvn -pl kafka-common,order-service,store-service -am test
docker compose --env-file .env.example config --quiet
```

Expected: Maven BUILD SUCCESS and valid Compose configuration.

- [ ] **Step 4: Check the patch and commit**

Run `git diff --check`, confirm all changed files belong to this plan, stage them, then:

```powershell
git commit -m "fix(kafka): isolate dead letters by consumer"
```

Expected: one commit authored by `mahan fatehian <mahanfatehian@gmail.com>` with no attribution trailers.
