package com.orderprocessing.kafkacommon;

import com.orderprocessing.kafkacommon.event.DomainEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;

import java.util.regex.Pattern;

/**
 * Binds the correlation id of a consumed event to the logging MDC.
 *
 * <p>HTTP requests get this for free: a servlet filter populates the MDC and
 * removes it when the request completes. Kafka listeners run on consumer
 * threads that never pass through that filter, so without this the whole
 * asynchronous half of the saga logs with an empty correlation field.</p>
 *
 * <p>The correlation id is carried in the event payload itself, which makes it
 * caller-influenced data. It is validated against the same character set the
 * HTTP edge enforces before being written to the MDC, so a hostile producer
 * cannot inject newlines or control characters into the log stream.</p>
 *
 * <p>When an event carries no usable correlation id, the record's own
 * coordinates are used instead. That value is stable across redeliveries and
 * retries of the same record, so the attempts of one message still group
 * together.</p>
 */
public final class EventCorrelationContext {

    /** MDC key, matching the one the servlet correlation filters populate. */
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private EventCorrelationContext() {
    }

    /**
     * Runs {@code handler} with the record's correlation id bound to the MDC,
     * restoring the previous value afterwards.
     */
    public static void run(ConsumerRecord<?, ?> record, Runnable handler) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, resolve(record));
        try {
            handler.run();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    /**
     * Returns the event's correlation id when it is present and safe to log,
     * otherwise a deterministic id derived from the record's coordinates.
     */
    public static String resolve(ConsumerRecord<?, ?> record) {
        if (record.value() instanceof DomainEvent event) {
            String value = event.getCorrelationId();
            if (value != null && SAFE_VALUE.matcher(value).matches()) {
                return value;
            }
        }
        return record.topic() + ":" + record.partition() + ":" + record.offset();
    }
}
