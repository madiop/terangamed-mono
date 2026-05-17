package com.terangamed.medical.entity;

/**
 * Groupe sanguin du patient (système ABO + Rhésus).
 * <p>{@code UNKNOWN} pour les dossiers où l'information n'a pas encore été
 * collectée — éviter les nullable inutiles.
 */
public enum BloodType {
    A_POS,
    A_NEG,
    B_POS,
    B_NEG,
    AB_POS,
    AB_NEG,
    O_POS,
    O_NEG,
    UNKNOWN
}
