import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { getApiBaseUrl } from '@core/config/runtime-config';
import { Page, PageRequest, toHttpParams } from './common.types';
import {
  CreatePatientRequest,
  PatientDto,
  PatientSearchCriteria,
  UpdatePatientRequest
} from './models/patient.model';

/**
 * Client HTTP — patient-service. Pure couche transport, pas de logique métier.
 */
@Injectable({ providedIn: 'root' })
export class PatientApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${getApiBaseUrl()}/api/patients`;

  search(
    criteria: PatientSearchCriteria = {},
    page: PageRequest = {}
  ): Observable<Page<PatientDto>> {
    return this.http.get<Page<PatientDto>>(this.base, {
      params: toHttpParams({ ...criteria, ...page })
    });
  }

  findById(id: number): Observable<PatientDto> {
    return this.http.get<PatientDto>(`${this.base}/${id}`);
  }

  findByMedicalRecordNumber(mrn: string): Observable<PatientDto> {
    return this.http.get<PatientDto>(`${this.base}/by-mrn/${encodeURIComponent(mrn)}`);
  }

  create(request: CreatePatientRequest): Observable<PatientDto> {
    return this.http.post<PatientDto>(this.base, request);
  }

  update(id: number, request: UpdatePatientRequest): Observable<PatientDto> {
    return this.http.put<PatientDto>(`${this.base}/${id}`, request);
  }

  archive(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/archive`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
