package com.terangamed.common.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPropertiesTest {

    @Test
    void defaults_are_sensible() {
        OutboxProperties props = new OutboxProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getPollBatchSize()).isEqualTo(100);
        assertThat(props.getPollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.getMaxAttempts()).isEqualTo(5);
        assertThat(props.getPurgeAfter()).isEqualTo(Duration.ofDays(7));
        assertThat(props.isPurgeEnabled()).isTrue();
    }

    @Test
    void setters_work() {
        OutboxProperties props = new OutboxProperties();
        props.setEnabled(false);
        props.setPollBatchSize(50);
        props.setPollInterval(Duration.ofMillis(500));
        props.setMaxAttempts(10);
        props.setPurgeAfter(Duration.ofDays(30));
        props.setPurgeEnabled(false);

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getPollBatchSize()).isEqualTo(50);
        assertThat(props.getPollInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(props.getMaxAttempts()).isEqualTo(10);
        assertThat(props.getPurgeAfter()).isEqualTo(Duration.ofDays(30));
        assertThat(props.isPurgeEnabled()).isFalse();
    }
}
