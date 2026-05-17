package com.terangamed.medical.entity;

/**
 * Catégorie d'antécédent médical du patient.
 *
 * <ul>
 *   <li>{@link #ALLERGY} — allergies (médicaments, alimentaires, environnementales)</li>
 *   <li>{@link #MEDICAL_CONDITION} — maladies chroniques ou ATCD pathologiques (HTA, diabète…)</li>
 *   <li>{@link #SURGERY} — interventions chirurgicales</li>
 *   <li>{@link #MEDICATION} — traitement de fond en cours</li>
 *   <li>{@link #FAMILY} — antécédents familiaux (cardiopathie père, cancer mère…)</li>
 * </ul>
 */
public enum AntecedentType {
    ALLERGY,
    MEDICAL_CONDITION,
    SURGERY,
    MEDICATION,
    FAMILY
}
