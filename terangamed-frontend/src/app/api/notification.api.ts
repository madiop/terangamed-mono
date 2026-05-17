import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { getApiBaseUrl } from '@core/config/runtime-config';
import { Page, PageRequest, toHttpParams } from './common.types';
import { NotificationDto, NotificationSearchCriteria } from './models/notification.model';

/**
 * Client HTTP — notification-service. Audit/historique des events Kafka.
 * Endpoints réservés ADMIN côté backend.
 */
@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${getApiBaseUrl()}/api/notifications`;

  search(
    criteria: NotificationSearchCriteria = {},
    page: PageRequest = {}
  ): Observable<Page<NotificationDto>> {
    return this.http.get<Page<NotificationDto>>(this.base, {
      params: toHttpParams({ ...criteria, ...page })
    });
  }

  findById(id: number): Observable<NotificationDto> {
    return this.http.get<NotificationDto>(`${this.base}/${id}`);
  }
}
