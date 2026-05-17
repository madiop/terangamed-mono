import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { getApiBaseUrl } from '@core/config/runtime-config';
import { Page, PageRequest, toHttpParams } from './common.types';
import {
  AppointmentDto,
  AppointmentSearchCriteria,
  CreateAppointmentRequest,
  UpdateAppointmentRequest
} from './models/appointment.model';

/**
 * Client HTTP — appointment-service. Transitions de statut côté backend :
 * PLANNED → CONFIRMED → COMPLETED (ou CANCELLED / NO_SHOW à différentes étapes).
 */
@Injectable({ providedIn: 'root' })
export class AppointmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${getApiBaseUrl()}/api/appointments`;

  search(
    criteria: AppointmentSearchCriteria = {},
    page: PageRequest = {}
  ): Observable<Page<AppointmentDto>> {
    return this.http.get<Page<AppointmentDto>>(this.base, {
      params: toHttpParams({ ...criteria, ...page })
    });
  }

  findById(id: number): Observable<AppointmentDto> {
    return this.http.get<AppointmentDto>(`${this.base}/${id}`);
  }

  create(request: CreateAppointmentRequest): Observable<AppointmentDto> {
    return this.http.post<AppointmentDto>(this.base, request);
  }

  update(id: number, request: UpdateAppointmentRequest): Observable<AppointmentDto> {
    return this.http.put<AppointmentDto>(`${this.base}/${id}`, request);
  }

  confirm(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/confirm`, {});
  }

  complete(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/complete`, {});
  }

  cancel(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/cancel`, {});
  }

  markNoShow(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/no-show`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
