package com.terangamed.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository Outbox.
 *
 * <p>Le {@link #findPendingForUpdate} utilise {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * (PostgreSQL) — permet plusieurs instances du relay tournant en parallèle de se
 * partager les events sans contention. Compatible Spring Boot 3 / Hibernate 6.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Récupère les events {@code PENDING} les plus anciens, en posant un verrou
     * {@code FOR UPDATE SKIP LOCKED} pour permettre la concurrence multi-instances.
     *
     * <p>Note : {@code @QueryHints} avec {@code jakarta.persistence.lock.timeout=-2}
     * équivaut à {@code SKIP LOCKED} en PostgreSQL via Hibernate 6.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
    })
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = com.terangamed.common.outbox.OutboxEventStatus.PENDING
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findPendingForUpdate(Pageable pageable);

    /**
     * Compte les events publiés avant une date donnée — utilisé pour le job
     * d'archivage / purge périodique.
     */
    long countByStatusAndProcessedAtBefore(OutboxEventStatus status, Instant before);

    /** Suppression batch des events publiés anciens — purge. */
    long deleteByStatusAndProcessedAtBefore(OutboxEventStatus status, Instant before);
}
