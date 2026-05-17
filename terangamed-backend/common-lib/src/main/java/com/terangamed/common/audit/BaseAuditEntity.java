package com.terangamed.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Superclasse JPA fournissant les colonnes d'audit standard à toutes les entités métier.
 *
 * <p>Champs auto-renseignés par {@link AuditingEntityListener} (Spring Data) :
 * <ul>
 *   <li>{@code created_at} / {@code updated_at} — horodatage UTC ({@link Instant})</li>
 *   <li>{@code created_by} / {@code updated_by} — username extrait du JWT
 *       via {@link JwtAuditorAware}</li>
 * </ul>
 *
 * <p><b>Pré-requis dans le microservice consommateur</b> :
 * <ol>
 *   <li>Avoir {@code spring-boot-starter-data-jpa} (transitif via {@code common-lib})</li>
 *   <li>Activer l'auditing sur la classe principale :
 *     <pre>
 *     &#064;SpringBootApplication
 *     &#064;EnableJpaAuditing(auditorAwareRef = "terangamedAuditorAware")
 *     public class PatientServiceApplication { ... }
 *     </pre>
 *     Le bean {@code terangamedAuditorAware} est fourni automatiquement par
 *     {@link com.terangamed.common.config.JpaAuditingAutoConfiguration}.
 *   </li>
 * </ol>
 *
 * <p><b>Usage métier</b> :
 * <pre>
 * &#064;Entity
 * &#064;Table(name = "patients")
 * public class Patient extends BaseAuditEntity {
 *     &#064;Id &#064;GeneratedValue private Long id;
 *     // ...
 * }
 * </pre>
 *
 * <p>Côté schéma SQL (Flyway), prévoir les colonnes correspondantes :
 * <pre>
 *   created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 *   updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 *   created_by VARCHAR(100),
 *   updated_by VARCHAR(100)
 * </pre>
 */
/*
 * NOTE — pourquoi @Setter en plus de @Getter ?
 *
 * <p>En production, ces champs sont peuplés automatiquement par Spring Data
 * Auditing ({@link AuditingEntityListener}) et n'ont théoriquement pas besoin
 * de setters publics. Mais les tests unitaires (Mockito) ont besoin de pouvoir
 * fixer {@code createdBy} pour valider la logique métier qui en dépend
 * (ex: ConsultationService bloque l'update si {@code currentUser != createdBy}).
 *
 * <p>Sans cette annotation, les builders Lombok @Builder des sous-classes
 * (Consultation, Patient, Doctor, etc.) ne peuvent pas chaîner {@code .createdBy(…)}
 * car @Builder ne traverse pas les fields hérités. Les setters publics offrent
 * un échappatoire propre via {@code entity.setCreatedBy("…")} après {@code build()}.
 *
 * <p>Risque : un appel inopiné en prod overrirait le createdBy auto. Acceptable
 * vu que (a) Spring re-set ces fields à chaque save persist, (b) la convention
 * du projet est de ne jamais setter manuellement les fields audit en code métier.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
