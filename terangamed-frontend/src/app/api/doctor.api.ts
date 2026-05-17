import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { getApiBaseUrl } from '@core/config/runtime-config';
import { Page, PageRequest, toHttpParams } from './common.types';
import {
  CreateDoctorRequest,
  DoctorDto,
  DoctorSearchCriteria,
  UpdateDoctorRequest
} from './models/doctor.model';

/**
 * Client HTTP — doctor-service. Inclut les transitions d'état métier
 * (putOnLeave, retire, reactivate) — protégées ADMIN côté backend.
 */
@Injectable({ providedIn: 'root' })
export class DoctorApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${getApiBaseUrl()}/api/doctors`;

  search(
    criteria: DoctorSearchCriteria = {},
    page: PageRequest = {}
  ): Observable<Page<DoctorDto>> {
    return this.http.get<Page<DoctorDto>>(this.base, {
      params: toHttpParams({ ...criteria, ...page })
    });
  }

  searchActive(
    criteria: DoctorSearchCriteria = {},
    page: PageRequest = {}
  ): Observable<Page<DoctorDto>> {
    return this.http.get<Page<DoctorDto>>(`${this.base}/active`, {
      params: toHttpParams({ ...criteria, ...page })
    });
  }

  findById(id: number): Observable<DoctorDto> {
    return this.http.get<DoctorDto>(`${this.base}/${id}`);
  }

  /**
   * Profil du médecin connecté — résolu côté backend depuis le claim `sub` du JWT.
   * Retourne 404 si le compte Keycloak n'est pas lié à un Doctor.
   */
  findMe(): Observable<DoctorDto> {
    return this.http.get<DoctorDto>(`${this.base}/me`);
  }

  findByLicense(licenseNumber: string): Observable<DoctorDto> {
    return this.http.get<DoctorDto>(`${this.base}/by-license/${encodeURIComponent(licenseNumber)}`);
  }

  create(request: CreateDoctorRequest): Observable<DoctorDto> {
    return this.http.post<DoctorDto>(this.base, request);
  }

  update(id: number, request: UpdateDoctorRequest): Observable<DoctorDto> {
    return this.http.put<DoctorDto>(`${this.base}/${id}`, request);
  }

  putOnLeave(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/leave`, {});
  }

  retire(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/retire`, {});
  }

  reactivate(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/reactivate`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
