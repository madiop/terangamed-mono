package com.terangamed.notification.consumer;

import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventHeadersExtractorTest {

    @Test
    void header_returns_value_when_present() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("event-type", "patient.created".getBytes(StandardCharsets.UTF_8)));

        assertThat(EventHeadersExtractor.header(headers, "event-type"))
                .isEqualTo("patient.created");
    }

    @Test
    void header_returns_null_when_absent() {
        assertThat(EventHeadersExtractor.header(new RecordHeaders(), "missing")).isNull();
    }

    @Test
    void event_id_parses_uuid() {
        UUID expected = UUID.randomUUID();
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("event-id", expected.toString().getBytes(StandardCharsets.UTF_8)));

        assertThat(EventHeadersExtractor.eventId(headers)).isEqualTo(expected);
    }

    @Test
    void event_id_returns_null_when_invalid() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("event-id", "not-a-uuid".getBytes(StandardCharsets.UTF_8)));
        assertThat(EventHeadersExtractor.eventId(headers)).isNull();
    }

    @Test
    void event_id_returns_null_when_missing() {
        assertThat(EventHeadersExtractor.eventId(new RecordHeaders())).isNull();
    }

    @Test
    void event_id_returns_null_when_blank() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("event-id", "  ".getBytes(StandardCharsets.UTF_8)));
        assertThat(EventHeadersExtractor.eventId(headers)).isNull();
    }
}
