package com.terangamed.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityRolesTest {

    @Test
    void constants_should_match_expected_role_names() {
        assertThat(SecurityRoles.ADMIN).isEqualTo("ADMIN");
        assertThat(SecurityRoles.DOCTOR).isEqualTo("DOCTOR");
        assertThat(SecurityRoles.RECEPTIONIST).isEqualTo("RECEPTIONIST");
    }

    @Test
    void spel_expressions_should_be_well_formed() {
        assertThat(SecurityRoles.HAS_ADMIN).isEqualTo("hasRole('ADMIN')");
        assertThat(SecurityRoles.HAS_DOCTOR).isEqualTo("hasRole('DOCTOR')");
        assertThat(SecurityRoles.HAS_RECEPTIONIST).isEqualTo("hasRole('RECEPTIONIST')");
        assertThat(SecurityRoles.HAS_ANY_STAFF)
                .contains("ADMIN", "DOCTOR", "RECEPTIONIST")
                .startsWith("hasAnyRole(");
        assertThat(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
                .isEqualTo("hasAnyRole('ADMIN','DOCTOR')");
        assertThat(SecurityRoles.HAS_ADMIN_OR_RECEPTIONIST)
                .isEqualTo("hasAnyRole('ADMIN','RECEPTIONIST')");
    }

    @Test
    void utility_class_should_not_be_instantiable() {
        assertThatThrownBy(() -> {
            var ctor = SecurityRoles.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
