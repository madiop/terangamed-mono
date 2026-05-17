package com.terangamed.common.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventStatusTest {

    @Test
    void enum_has_three_values() {
        assertThat(OutboxEventStatus.values()).hasSize(3)
                .containsExactly(OutboxEventStatus.PENDING,
                        OutboxEventStatus.PUBLISHED,
                        OutboxEventStatus.FAILED);
    }

    @Test
    void value_of_round_trip() {
        for (OutboxEventStatus s : OutboxEventStatus.values()) {
            assertThat(OutboxEventStatus.valueOf(s.name())).isEqualTo(s);
        }
    }
}
