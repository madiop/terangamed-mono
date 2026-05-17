package com.terangamed.common.outbox;

/**
 * État d'un événement dans la table {@code outbox_events}.
 *
 * <ul>
 *   <li>{@link #PENDING} — fraîchement inséré, pas encore publié vers Kafka</li>
 *   <li>{@link #PUBLISHED} — publié avec succès, peut être archivé/purgé</li>
 *   <li>{@link #FAILED} — échec après {@code maxAttempts} tentatives — investigation manuelle</li>
 * </ul>
 *
 * <p>Le polling du relay traite uniquement les {@link #PENDING}. Un event en
 * {@link #FAILED} doit être remis à PENDING manuellement (admin) après diagnostic.
 */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
