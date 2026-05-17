package com.terangamed.medical.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VitalSignsDtoTest {

    @Test
    void compute_bmi_normal_case() {
        // 72 kg, 1.80 m → 72 / 3.24 = 22.2
        BigDecimal bmi = VitalSignsDto.computeBmi(new BigDecimal("72"), new BigDecimal("180"));
        assertThat(bmi).isEqualByComparingTo("22.2");
    }

    @Test
    void compute_bmi_returns_null_when_weight_missing() {
        assertThat(VitalSignsDto.computeBmi(null, new BigDecimal("180"))).isNull();
    }

    @Test
    void compute_bmi_returns_null_when_height_missing() {
        assertThat(VitalSignsDto.computeBmi(new BigDecimal("70"), null)).isNull();
    }

    @Test
    void compute_bmi_returns_null_when_height_zero_or_negative() {
        assertThat(VitalSignsDto.computeBmi(new BigDecimal("70"), BigDecimal.ZERO)).isNull();
        assertThat(VitalSignsDto.computeBmi(new BigDecimal("70"), new BigDecimal("-10"))).isNull();
    }

    @Test
    void from_entity_factory_includes_bmi() {
        VitalSignsDto dto = VitalSignsDto.fromEntity(
                new BigDecimal("65"), new BigDecimal("165"),
                new BigDecimal("36.8"), 70, 16, 120, 80, 99,
                new BigDecimal("90"), null);
        assertThat(dto.bmi()).isNotNull();
        assertThat(dto.bmi()).isEqualByComparingTo("23.9");
    }
}
