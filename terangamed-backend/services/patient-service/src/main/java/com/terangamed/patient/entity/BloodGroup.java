package com.terangamed.patient.entity;

/**
 * Groupe sanguin ABO + Rhésus.
 *
 * <p>Stocké en base sous forme {@code String} via {@code @Enumerated(STRING)}.
 * Suffixes : {@code POS} = Rh+, {@code NEG} = Rh−.
 */
public enum BloodGroup {
    A_POS, A_NEG,
    B_POS, B_NEG,
    AB_POS, AB_NEG,
    O_POS, O_NEG,
    UNKNOWN
}
