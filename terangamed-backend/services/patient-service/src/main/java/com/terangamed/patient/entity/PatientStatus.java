package com.terangamed.patient.entity;

/**
 * Cycle de vie du dossier patient.
 *
 * <p>{@code ACTIVE} : dossier en cours de suivi.<br>
 * {@code INACTIVE} : dossier en pause (patient parti, à réactiver si retour).<br>
 * {@code ARCHIVED} : dossier figé (décès, demande d'archivage, etc.) — lecture seule.
 */
public enum PatientStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
