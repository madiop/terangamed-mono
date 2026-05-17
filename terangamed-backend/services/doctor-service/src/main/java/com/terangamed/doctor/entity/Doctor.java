package com.terangamed.doctor.entity;

import com.terangamed.common.audit.BaseAuditEntity;
import com.terangamed.common.finance.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité JPA représentant un médecin du cabinet médical.
 *
 * <p>{@code licenseNumber} (n° d'ordre médical) est l'identifiant métier unique
 * — format {@code MED-YYYY-NNNNN}. Généré par le service à la création.
 */
@Entity
@Table(name = "doctors", indexes = {
        @Index(name = "idx_doctor_email", columnList = "email"),
        @Index(name = "idx_doctor_specialty", columnList = "specialty"),
        @Index(name = "idx_doctor_status", columnList = "status"),
        @Index(name = "idx_doctor_keycloak_subject", columnList = "keycloak_subject")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "licenseNumber", "lastName", "firstName", "specialty"})
public class Doctor extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_number", nullable = false, unique = true, length = 30)
    private String licenseNumber;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialty", nullable = false, length = 30)
    private Specialty specialty;

    @Column(name = "email", length = 100, unique = true)
    private String email;

    /**
     * Identifiant Keycloak (claim {@code sub} du JWT) du compte utilisateur lié
     * à ce médecin. Permet à {@code GET /api/doctors/me} de résoudre le profil
     * Doctor à partir du JWT, sans dépendre de l'email (qui peut diverger entre
     * Keycloak et la fiche Doctor).
     *
     * <p>Nullable : tous les médecins n'ont pas de compte Keycloak (anciens
     * dossiers, RETIRED, etc.). Unicité garantie par un index partiel quand
     * renseigné (cf. migration V4).
     */
    @Column(name = "keycloak_subject", columnDefinition = "uuid")
    private UUID keycloakSubject;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "office_address", length = 250)
    private String officeAddress;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_fee_currency", length = 10)
    private Currency consultationFeeCurrency;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DoctorStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
