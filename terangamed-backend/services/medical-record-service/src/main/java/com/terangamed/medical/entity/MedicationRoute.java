package com.terangamed.medical.entity;

/**
 * Voie d'administration d'un médicament prescrit.
 * <p>Liste minimaliste suffisante pour la majorité des prescriptions ambulatoires.
 */
public enum MedicationRoute {
    ORAL,
    INJECTION,
    TOPICAL,
    INHALATION,
    OPHTHALMIC,
    NASAL,
    RECTAL,
    OTHER
}
