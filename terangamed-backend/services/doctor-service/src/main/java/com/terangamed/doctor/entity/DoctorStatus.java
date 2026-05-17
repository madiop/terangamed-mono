package com.terangamed.doctor.entity;

/**
 * Cycle de vie d'un médecin.
 *
 * <p>{@code ACTIVE} : exerce et accepte les RDV.<br>
 * {@code ON_LEAVE} : congé / suspension temporaire — pas de nouveaux RDV.<br>
 * {@code RETIRED} : ne pratique plus — fiche conservée pour historique.
 */
public enum DoctorStatus {
    ACTIVE,
    ON_LEAVE,
    RETIRED
}
