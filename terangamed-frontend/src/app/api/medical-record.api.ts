import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { getApiBaseUrl } from '@core/config/runtime-config';
import { Page, PageRequest, toHttpParams } from './common.types';
import {
  AntecedentDto,
  AntecedentType,
  ConsultationDto,
  ConsultationSearchCriteria,
  CreateAntecedentRequest,
  CreateConsultationRequest,
  CreateMedicalRecordRequest,
  CreatePrescriptionLineRequest,
  CreatePrescriptionRequest,
  MedicalRecordDto,
  PrescriptionDto,
  PrescriptionLineDto,
  UpdateAntecedentRequest,
  UpdateConsultationRequest,
  UpdateMedicalRecordRequest,
  UpdatePrescriptionLineRequest,
  UpdatePrescriptionRequest
} from './models/medical-record.model';

/**
 * Client HTTP — medical-record-service. Couvre les 4 ressources :
 * <ul>
 *   <li>{@code /api/medical-records} — dossier médical (1 par patient)</li>
 *   <li>{@code /api/antecedents} — antécédents (allergies, maladies, etc.)</li>
 *   <li>{@code /api/consultations} — visites avec workflow signature</li>
 *   <li>{@code /api/prescriptions} — ordonnances + lignes médicaments</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class MedicalRecordApi {
  private readonly http = inject(HttpClient);
  private readonly base = getApiBaseUrl();

  // ═════════════════════════════════════════════════════════════════════════
  //   MedicalRecord
  // ═════════════════════════════════════════════════════════════════════════

  findRecordById(id: number): Observable<MedicalRecordDto> {
    return this.http.get<MedicalRecordDto>(`${this.base}/api/medical-records/${id}`);
  }

  findRecordByPatientId(patientId: number): Observable<MedicalRecordDto> {
    return this.http.get<MedicalRecordDto>(
      `${this.base}/api/medical-records/by-patient/${patientId}`
    );
  }

  createRecord(request: CreateMedicalRecordRequest): Observable<MedicalRecordDto> {
    return this.http.post<MedicalRecordDto>(`${this.base}/api/medical-records`, request);
  }

  updateRecord(id: number, request: UpdateMedicalRecordRequest): Observable<MedicalRecordDto> {
    return this.http.put<MedicalRecordDto>(`${this.base}/api/medical-records/${id}`, request);
  }

  softDeleteRecord(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/medical-records/${id}`);
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Antécédents
  // ═════════════════════════════════════════════════════════════════════════

  listAntecedentsByRecord(
    medicalRecordId: number,
    options: { type?: AntecedentType; onlyActive?: boolean } = {}
  ): Observable<AntecedentDto[]> {
    return this.http.get<AntecedentDto[]>(
      `${this.base}/api/antecedents/by-record/${medicalRecordId}`,
      { params: toHttpParams(options as Record<string, unknown>) }
    );
  }

  findAntecedent(id: number): Observable<AntecedentDto> {
    return this.http.get<AntecedentDto>(`${this.base}/api/antecedents/${id}`);
  }

  createAntecedent(request: CreateAntecedentRequest): Observable<AntecedentDto> {
    return this.http.post<AntecedentDto>(`${this.base}/api/antecedents`, request);
  }

  updateAntecedent(id: number, request: UpdateAntecedentRequest): Observable<AntecedentDto> {
    return this.http.put<AntecedentDto>(`${this.base}/api/antecedents/${id}`, request);
  }

  deleteAntecedent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/antecedents/${id}`);
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Consultations
  // ═════════════════════════════════════════════════════════════════════════

  searchConsultations(
    criteria: ConsultationSearchCriteria = {},
    page: PageRequest = {}
  ): Observable<Page<ConsultationDto>> {
    return this.http.get<Page<ConsultationDto>>(`${this.base}/api/consultations`, {
      params: toHttpParams({ ...criteria, ...page })
    });
  }

  findConsultation(id: number): Observable<ConsultationDto> {
    return this.http.get<ConsultationDto>(`${this.base}/api/consultations/${id}`);
  }

  /**
   * Crée une consultation. Le médecin auteur est résolu côté backend depuis
   * le claim {@code sub} du JWT (mapping `doctor.keycloak_subject`) — aucun
   * header d'app requis.
   */
  createConsultation(request: CreateConsultationRequest): Observable<ConsultationDto> {
    return this.http.post<ConsultationDto>(`${this.base}/api/consultations`, request);
  }

  updateConsultation(
    id: number,
    request: UpdateConsultationRequest
  ): Observable<ConsultationDto> {
    return this.http.put<ConsultationDto>(`${this.base}/api/consultations/${id}`, request);
  }

  /**
   * Signe une consultation — passe en statut SIGNED, immutable ensuite.
   * Renvoie le DTO mis à jour avec {@code signed=true} et {@code signedAt/signedBy} renseignés.
   */
  signConsultation(id: number): Observable<ConsultationDto> {
    return this.http.post<ConsultationDto>(`${this.base}/api/consultations/${id}/sign`, {});
  }

  softDeleteConsultation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/consultations/${id}`);
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Prescriptions
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Crée une ordonnance liée à une consultation. Au moins 1 ligne obligatoire.
   * Endpoint : {@code POST /api/prescriptions/by-consultation/:consultationId}.
   */
  createPrescription(
    consultationId: number,
    request: CreatePrescriptionRequest
  ): Observable<PrescriptionDto> {
    return this.http.post<PrescriptionDto>(
      `${this.base}/api/prescriptions/by-consultation/${consultationId}`,
      request
    );
  }

  findPrescription(id: number): Observable<PrescriptionDto> {
    return this.http.get<PrescriptionDto>(`${this.base}/api/prescriptions/${id}`);
  }

  findPrescriptionByConsultation(consultationId: number): Observable<PrescriptionDto> {
    return this.http.get<PrescriptionDto>(
      `${this.base}/api/prescriptions/by-consultation/${consultationId}`
    );
  }

  updatePrescription(
    id: number,
    request: UpdatePrescriptionRequest
  ): Observable<PrescriptionDto> {
    return this.http.put<PrescriptionDto>(`${this.base}/api/prescriptions/${id}`, request);
  }

  deletePrescription(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/prescriptions/${id}`);
  }

  /**
   * Ajoute une ligne médicament à une ordonnance existante.
   * Endpoint : {@code POST /api/prescriptions/:id/lines}.
   */
  addPrescriptionLine(
    prescriptionId: number,
    request: CreatePrescriptionLineRequest
  ): Observable<PrescriptionLineDto> {
    return this.http.post<PrescriptionLineDto>(
      `${this.base}/api/prescriptions/${prescriptionId}/lines`,
      request
    );
  }

  updatePrescriptionLine(
    prescriptionId: number,
    lineId: number,
    request: UpdatePrescriptionLineRequest
  ): Observable<PrescriptionLineDto> {
    return this.http.put<PrescriptionLineDto>(
      `${this.base}/api/prescriptions/${prescriptionId}/lines/${lineId}`,
      request
    );
  }

  deletePrescriptionLine(prescriptionId: number, lineId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/prescriptions/${prescriptionId}/lines/${lineId}`
    );
  }

  /**
   * Récupère un PDF de l'ordonnance (binaire). Pour téléchargement direct.
   */
  getPrescriptionPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/api/prescriptions/${id}/pdf`, {
      responseType: 'blob'
    });
  }
}
