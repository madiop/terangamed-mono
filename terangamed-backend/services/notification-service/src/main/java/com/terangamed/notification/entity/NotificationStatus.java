package com.terangamed.notification.entity;

/**
 * Statut d'une notification dans son cycle de vie.
 *
 * <p>V1 : seul {@link #RECEIVED} est utilisé (le service log uniquement).
 * V2 (post-MVP) : on ajoutera {@code SENT_EMAIL}, {@code SENT_SMS}, {@code FAILED}
 * une fois les providers externes branchés.
 */
public enum NotificationStatus {
    RECEIVED,
    SENT_EMAIL,
    SENT_SMS,
    FAILED
}
