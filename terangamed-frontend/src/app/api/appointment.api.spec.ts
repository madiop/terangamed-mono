import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppointmentApi } from './appointment.api';
import { environment } from '@env/environment';

describe('AppointmentApi', () => {
  let api: AppointmentApi;
  let controller: HttpTestingController;
  const base = `${environment.apiBaseUrl}/api/appointments`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AppointmentApi, provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(AppointmentApi);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('search avec dates → query params formatés', () => {
    api
      .search({ doctorId: 101, fromDate: '2026-04-01', toDate: '2026-04-30' }, { page: 0, size: 20 })
      .subscribe();
    const req = controller.expectOne((r) => r.url === base);
    expect(req.request.params.get('doctorId')).toBe('101');
    expect(req.request.params.get('fromDate')).toBe('2026-04-01');
    expect(req.request.params.get('toDate')).toBe('2026-04-30');
    req.flush({});
  });

  it('confirm POST /api/appointments/:id/confirm', () => {
    api.confirm(7).subscribe();
    const req = controller.expectOne(`${base}/7/confirm`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('markNoShow POST /no-show', () => {
    api.markNoShow(7).subscribe();
    const req = controller.expectOne(`${base}/7/no-show`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
