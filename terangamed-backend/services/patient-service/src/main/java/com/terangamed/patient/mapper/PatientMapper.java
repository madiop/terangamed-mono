package com.terangamed.patient.mapper;

import com.terangamed.patient.dto.CreatePatientRequest;
import com.terangamed.patient.dto.PatientDto;
import com.terangamed.patient.dto.UpdatePatientRequest;
import com.terangamed.patient.entity.Patient;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper MapStruct entre les DTOs et l'entité {@link Patient}.
 *
 * <p><b>Choix techniques</b> :
 * <ul>
 *   <li>{@code componentModel = "spring"} — l'implémentation générée est un Spring Bean
 *       injecté via constructeur dans {@code PatientService}</li>
 *   <li>{@code unmappedTargetPolicy = IGNORE} — quand on ajoutera de nouveaux
 *       champs à {@link Patient} mais pas encore dans la DTO, le build ne casse pas
 *       (les champs sont juste ignorés). À durcir en {@code ERROR} en prod si besoin.</li>
 *   <li>{@code nullValuePropertyMappingStrategy = IGNORE} — pour
 *       {@link #updateEntity(UpdatePatientRequest, Patient)} : tout champ {@code null}
 *       dans la DTO laisse l'entité inchangée (sémantique partial update)</li>
 * </ul>
 *
 * <p><b>Note sur les champs d'audit</b> : {@code createdAt}, {@code updatedAt},
 * {@code createdBy}, {@code updatedBy} sont hérités de {@code BaseAuditEntity}.
 * <ul>
 *   <li>Pour {@link #toEntity(CreatePatientRequest)} : MapStruct passe par
 *       {@code Patient.PatientBuilder} (généré par Lombok {@code @Builder}) qui ne
 *       contient PAS ces champs hérités → MapStruct ne les voit pas, ne tente pas
 *       de les mapper. Pas besoin de directives {@code @Mapping ignore=true}
 *       (qui causeraient au contraire une erreur "Unknown property in builder").</li>
 *   <li>Pour {@link #toDto(Patient)} : ils sont mappés via les getters Lombok
 *       générés sur {@code BaseAuditEntity} ({@code @Getter}).</li>
 *   <li>Pour {@link #updateEntity} : ils ne sont pas dans {@link UpdatePatientRequest}
 *       donc MapStruct ne les touche pas. {@code BaseAuditEntity} n'a de toute façon
 *       pas de setters — ces colonnes sont peuplées par {@code AuditingEntityListener}.</li>
 * </ul>
 *
 * <p>L'implémentation est générée à la compilation par le processeur d'annotations
 * MapStruct dans {@code target/generated-sources/annotations/} et le bean Spring
 * s'appelle {@code patientMapperImpl}. Pour les tests unitaires hors Spring,
 * on instancie via {@code Mappers.getMapper(PatientMapper.class)}.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PatientMapper {

    /**
     * Convertit l'entité en DTO pour les réponses API.
     */
    PatientDto toDto(Patient entity);

    /**
     * Convertit la requête de création en entité. Le service positionnera ensuite :
     * <ul>
     *   <li>{@code medicalRecordNumber} (généré),</li>
     *   <li>{@code status = ACTIVE}.</li>
     * </ul>
     *
     * <p>Seuls les champs présents dans {@code Patient.PatientBuilder} sont
     * référencés ci-dessous. Les champs d'audit hérités sont gérés séparément
     * (cf. Javadoc de classe).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medicalRecordNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    Patient toEntity(CreatePatientRequest request);

    /**
     * Applique les changements de la DTO sur l'entité existante. Les champs
     * {@code null} dans la requête sont ignorés
     * ({@link NullValuePropertyMappingStrategy#IGNORE}).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medicalRecordNumber", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdatePatientRequest request, @MappingTarget Patient entity);
}
