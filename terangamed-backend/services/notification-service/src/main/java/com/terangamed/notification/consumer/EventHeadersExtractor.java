package com.terangamed.notification.consumer;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Extrait les headers Kafka standard publiés par {@code OutboxEventRelay}.
 * Tolérant : retourne {@code null} si le header est absent.
 */
final class EventHeadersExtractor {

    static final String EVENT_ID = "event-id";
    static final String EVENT_TYPE = "event-type";
    static final String AGGREGATE_TYPE = "aggregate-type";
    static final String AGGREGATE_ID = "aggregate-id";

    private EventHeadersExtractor() {}

    static String header(Headers headers, String name) {
        Header h = headers.lastHeader(name);
        if (h == null || h.value() == null) {
            return null;
        }
        return new String(h.value(), StandardCharsets.UTF_8);
    }

    static UUID eventId(Headers headers) {
        String raw = header(headers, EVENT_ID);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
