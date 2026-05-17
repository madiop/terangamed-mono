package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO miroir de {@link com.terangamed.medical.entity.VitalSigns} — utilisé
 * en entrée (création/màj de consultation) et en sortie (lecture).
 *
 * <p>En sortie, {@link #bmi()} est calculé automatiquement (poids/taille²).
 * Ce champ est read-only côté API : marqué {@code Access.READ_ONLY}, donc
 * Jackson l'ignore en désérialisation (corps de requête).
 *
 * <p>Validation : bornes physiologiques larges pour éviter les saisies absurdes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Signes vitaux structurés")
public record VitalSignsDto(

        @Schema(description = "Poids en kg", example = "72.5")
        @DecimalMin(value = "0.5") @DecimalMax(value = "500")
        BigDecimal weightKg,

        @Schema(description = "Taille en cm", example = "178")
        @DecimalMin(value = "20") @DecimalMax(value = "260")
        BigDecimal heightCm,

        @Schema(description = "Température en °C", example = "36.8")
        @DecimalMin(value = "25") @DecimalMax(value = "45")
        BigDecimal temperatureCelsius,

        @Schema(description = "Fréquence cardiaque (bpm)", example = "76")
        @Min(20) @Max(300)
        Integer heartRateBpm,

        @Schema(description = "Fréquence respiratoire (par minute)", example = "16")
        @Min(4) @Max(80)
        Integer respiratoryRateBpm,

        @Schema(description = "TA systolique (mmHg)", example = "125")
        @Min(40) @Max(280)
        Integer bloodPressureSystolic,

        @Schema(description = "TA diastolique (mmHg)", example = "82")
        @Min(20) @Max(180)
        Integer bloodPressureDiastolic,

        @Schema(description = "Saturation en O₂ (%)", example = "98")
        @Min(50) @Max(100)
        Integer oxygenSaturationPercent,

        @Schema(description = "Glycémie (mg/dL)", example = "95")
        @DecimalMin(value = "0") @DecimalMax(value = "1000")
        BigDecimal bloodGlucoseMgDl,

        @Schema(description = "Notes libres complémentaires")
        String notes,

        /**
         * IMC calculé (kg/m²) — read-only en API. Calcul effectué côté serveur
         * à partir de weightKg / (heightCm/100)². Null si poids ou taille manquants.
         */
        @Schema(description = "IMC calculé (kg/m²) — read-only", accessMode = Schema.AccessMode.READ_ONLY)
        @JsonProperty(access = Access.READ_ONLY)
        BigDecimal bmi
) {

    /**
     * Factory : construit un {@code VitalSignsDto} à partir des mesures et
     * calcule l'IMC automatiquement. Utilisée par le mapper (toDto).
     */
    public static VitalSignsDto fromEntity(
            BigDecimal weightKg, BigDecimal heightCm, BigDecimal temperatureCelsius,
            Integer heartRateBpm, Integer respiratoryRateBpm,
            Integer bloodPressureSystolic, Integer bloodPressureDiastolic,
            Integer oxygenSaturationPercent, BigDecimal bloodGlucoseMgDl,
            String notes) {
        return new VitalSignsDto(
                weightKg, heightCm, temperatureCelsius,
                heartRateBpm, respiratoryRateBpm,
                bloodPressureSystolic, bloodPressureDiastolic,
                oxygenSaturationPercent, bloodGlucoseMgDl,
                notes,
                computeBmi(weightKg, heightCm)
        );
    }

    /**
     * IMC = poids(kg) / (taille(m))². Retourne {@code null} si une des deux
     * valeurs est absente ou si la taille est ≤ 0.
     */
    public static BigDecimal computeBmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null) {
            return null;
        }
        if (heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightMeters = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal heightSquared = heightMeters.multiply(heightMeters);
        if (heightSquared.signum() == 0) {
            return null;
        }
        return weightKg.divide(heightSquared, 1, RoundingMode.HALF_UP);
    }
}
