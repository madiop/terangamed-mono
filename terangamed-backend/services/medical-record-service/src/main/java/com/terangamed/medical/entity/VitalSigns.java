package com.terangamed.medical.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Signes vitaux relevés pendant l'examen clinique.
 *
 * <p>Sérialisé en JSONB sur la colonne {@code vital_signs} de la table
 * {@code consultations} via {@code @JdbcTypeCode(SqlTypes.JSON)} sur le champ
 * conteneur dans {@link Consultation}. Pas une entité — simple POJO.
 *
 * <p>Tous les champs sont optionnels : selon le motif, certaines mesures
 * peuvent ne pas être pertinentes (téléconsultation par ex.).
 *
 * <h3>Unités</h3>
 * <ul>
 *   <li>{@code weightKg} — kilogrammes</li>
 *   <li>{@code heightCm} — centimètres</li>
 *   <li>{@code temperatureCelsius} — degrés Celsius</li>
 *   <li>{@code heartRateBpm} — battements par minute</li>
 *   <li>{@code respiratoryRateBpm} — respirations par minute</li>
 *   <li>{@code bloodPressureSystolic} / {@code bloodPressureDiastolic} — mmHg</li>
 *   <li>{@code oxygenSaturationPercent} — % SpO₂</li>
 *   <li>{@code bloodGlucoseMgDl} — mg/dL (utile pour les diabétiques)</li>
 * </ul>
 *
 * <p>Une vue calculée {@code bmi} (kg/m²) est exposée dans le DTO de réponse
 * — calculée côté lecture, jamais stockée.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VitalSigns {

    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal temperatureCelsius;
    private Integer heartRateBpm;
    private Integer respiratoryRateBpm;
    private Integer bloodPressureSystolic;
    private Integer bloodPressureDiastolic;
    private Integer oxygenSaturationPercent;
    private BigDecimal bloodGlucoseMgDl;
    private String notes;
}
