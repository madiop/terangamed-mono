package com.terangamed.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la structure JPA de {@link BaseAuditEntity} (annotations critiques,
 * types, et conventions de colonne).
 *
 * <p>On évite un test {@code @DataJpaTest} ici — il sera ajouté dans le premier
 * service qui consomme la classe (patient-service, étape 4).
 */
class BaseAuditEntityTest {

    @Test
    void should_be_mapped_superclass() {
        assertThat(BaseAuditEntity.class.isAnnotationPresent(MappedSuperclass.class)).isTrue();
    }

    @Test
    void should_be_abstract() {
        assertThat(Modifier.isAbstract(BaseAuditEntity.class.getModifiers())).isTrue();
    }

    @Test
    void should_register_auditing_entity_listener() {
        EntityListeners listeners = BaseAuditEntity.class.getAnnotation(EntityListeners.class);
        assertThat(listeners).isNotNull();
        assertThat(listeners.value()).contains(AuditingEntityListener.class);
    }

    @Test
    void created_at_should_be_annotated_and_immutable() throws NoSuchFieldException {
        Field field = BaseAuditEntity.class.getDeclaredField("createdAt");

        assertThat(field.getType()).isEqualTo(Instant.class);
        assertThat(field.isAnnotationPresent(CreatedDate.class)).isTrue();

        Column col = field.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.name()).isEqualTo("created_at");
        assertThat(col.nullable()).isFalse();
        assertThat(col.updatable()).isFalse();
    }

    @Test
    void updated_at_should_be_annotated_and_mutable() throws NoSuchFieldException {
        Field field = BaseAuditEntity.class.getDeclaredField("updatedAt");

        assertThat(field.getType()).isEqualTo(Instant.class);
        assertThat(field.isAnnotationPresent(LastModifiedDate.class)).isTrue();

        Column col = field.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.name()).isEqualTo("updated_at");
        assertThat(col.nullable()).isFalse();
        assertThat(col.updatable()).isTrue();
    }

    @Test
    void created_by_should_be_immutable() throws NoSuchFieldException {
        Field field = BaseAuditEntity.class.getDeclaredField("createdBy");

        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(field.isAnnotationPresent(CreatedBy.class)).isTrue();

        Column col = field.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.name()).isEqualTo("created_by");
        assertThat(col.length()).isEqualTo(100);
        assertThat(col.updatable()).isFalse();
    }

    @Test
    void updated_by_should_be_mutable() throws NoSuchFieldException {
        Field field = BaseAuditEntity.class.getDeclaredField("updatedBy");

        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(field.isAnnotationPresent(LastModifiedBy.class)).isTrue();

        Column col = field.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.name()).isEqualTo("updated_by");
        assertThat(col.length()).isEqualTo(100);
    }
}
