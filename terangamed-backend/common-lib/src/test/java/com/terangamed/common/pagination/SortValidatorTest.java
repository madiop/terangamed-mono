package com.terangamed.common.pagination;

import com.terangamed.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortValidatorTest {

    private static final Set<String> ALLOWED = Set.of("lastName", "firstName", "createdAt");

    @Test
    void should_pass_when_sort_field_is_allowed() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("lastName"));

        Pageable result = SortValidator.sanitize(pageable, ALLOWED);

        assertThat(result).isEqualTo(pageable);
    }

    @Test
    void should_pass_with_multiple_allowed_fields() {
        Pageable pageable = PageRequest.of(0, 10,
                Sort.by(Sort.Order.asc("lastName"), Sort.Order.desc("createdAt")));

        Pageable result = SortValidator.sanitize(pageable, ALLOWED);

        assertThat(result).isEqualTo(pageable);
    }

    @Test
    void should_pass_when_pageable_is_unsorted() {
        Pageable pageable = PageRequest.of(0, 10);

        Pageable result = SortValidator.sanitize(pageable, ALLOWED);

        assertThat(result).isEqualTo(pageable);
    }

    @Test
    void should_pass_when_pageable_is_null() {
        Pageable result = SortValidator.sanitize(null, ALLOWED);

        assertThat(result).isNull();
    }

    @Test
    void should_throw_when_sort_field_not_allowed() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> SortValidator.sanitize(pageable, ALLOWED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password")
                .extracting("errorCode")
                .isEqualTo("INVALID_SORT_FIELD");
    }

    @Test
    void should_aggregate_all_invalid_sort_fields() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password", "ssn", "firstName"));

        assertThatThrownBy(() -> SortValidator.sanitize(pageable, ALLOWED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password")
                .hasMessageContaining("ssn");
    }

    @Test
    void should_not_be_instantiable() {
        assertThatThrownBy(() -> {
            var ctor = SortValidator.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
