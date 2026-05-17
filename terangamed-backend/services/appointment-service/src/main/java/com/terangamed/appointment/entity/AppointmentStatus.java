package com.terangamed.appointment.entity;

/**
 * Cycle de vie d'un rendez-vous.
 *
 * <p>Transitions autorisées :
 * <pre>
 *   PLANNED ──── confirm ───→ CONFIRMED ──── complete ───→ COMPLETED  (terminal)
 *      │                          │
 *      │                          ├──── markNoShow ────→ NO_SHOW       (terminal)
 *      │                          │
 *      ├──────── cancel ──────────┴──── cancel ────────→ CANCELLED     (terminal)
 * </pre>
 */
public enum AppointmentStatus {
    PLANNED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
