package com.terangamed.patient.entity;

import com.terangamed.common.audit.BaseAuditEntity;
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

import java.time.LocalDate;

/**
 * Entité JPA représentant un patient du cabinet médical.
 *
 * <p>Hérite de {@link BaseAuditEntity} pour les colonnes d'audit standard
 * (createdAt/updatedAt/createdBy/updatedBy).
 *
 * <p><b>Champ {@code medicalRecordNumber}</b> — identifiant métier unique
 * (format {@code MR-YYYY-NNNNN}), distinct de l'{@code id} technique. Généré
 * par le service métier à la création.
 *
 * <p><b>{@code @Version}</b> — verrouillage optimiste pour éviter les écritures
 * concurrentes silencieuses (deux médecins modifiant le même patient simultanément).
 *
 * <p>Index :
 * <ul>
 *   <li>{@code idx_patient_last_name} sur {@code lower(last_name)} — recherche insensible à la casse</li>
 *   <li>{@code idx_patient_phone}, {@code idx_patient_email} — lookup direct</li>
 *   <li>{@code idx_patient_status} — filtrage par statut</li>
 * </ul>
 */
@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_patient_phone", columnList = "phone"),
        @Index(name = "idx_patient_email", columnList = "email"),
        @Index(name = "idx_patient_birth_date", columnList = "birth_date"),
        @Index(name = "idx_patient_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "medicalRecordNumber", "lastName", "firstName"})
public class Patient extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medical_record_number", nullable = false, unique = true, length = 30)
    private String medicalRecordNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "civility", nullable = false, length = 10)
    private Civility civility;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 10)
    private BloodGroup bloodGroup;

    @Column(name = "allergies", columnDefinition = "TEXT")
    private String allergies;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
