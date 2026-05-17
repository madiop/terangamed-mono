package com.terangamed.common.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration {@code terangamed.outbox.*} — règle le polling et le retry du
 * {@link OutboxEventRelay}.
 *
 * <pre>
 * terangamed:
 *   outbox:
 *     enabled: true                # désactivable en test si on mock le Kafka
 *     poll-batch-size: 100
 *     poll-interval: 1s
 *     max-attempts: 5
 *     purge-after: 7d
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "terangamed.outbox")
public class OutboxProperties {

    /** Active/désactive le scheduler relay. Permet de mocker en test. */
    private boolean enabled = true;

    /** Nombre max d'events lus par cycle de polling. */
    private int pollBatchSize = 100;

    /** Intervalle entre deux cycles de polling. */
    private Duration pollInterval = Duration.ofSeconds(1);

    /** Tentatives max avant marquer un event en {@code FAILED}. */
    private int maxAttempts = 5;

    /** Âge minimum pour purger un event {@code PUBLISHED}. */
    private Duration purgeAfter = Duration.ofDays(7);

    /** Active/désactive le job de purge périodique. */
    private boolean purgeEnabled = true;
}
