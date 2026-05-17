import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PatientApi } from './patient.api';
import { PatientDto } from './models/patient.model';
import { Page } from './common.types';
import { environment } from '@env/environment';

describe('PatientApi', () => {
  let api: PatientApi;
  let controller: HttpTestingController;
  const base = `${environment.apiBaseUrl}/api/patients`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PatientApi, provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(PatientApi);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('search transmet criteria + pagination en query params', () => {
    api.search({ lastName: 'Diop', status: 'ACTIVE' }, { page: 0, size: 20 }).subscribe();
    const req = controller.expectOne((r) => r.url === base);
    expect(req.request.params.get('lastName')).toBe('Diop');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    } satisfies Page<PatientDto>);
  });

  it('findById appelle GET /api/patients/:id', () => {
    api.findById(42).subscribe();
    controller.expectOne(`${base}/42`).flush({} as PatientDto);
  });

  it('archive appelle POST /api/patients/:id/archive', () => {
    api.archive(42).subscribe();
    const req = controller.expectOne(`${base}/42/archive`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it("create POST le payload tel quel", () => {
    const payload = {
      civility: 'M' as const,
      lastName: 'Diop',
      firstName: 'Aliou',
      birthDate: '1990-01-01',
      gender: 'MALE' as const
    };
    api.create(payload).subscribe();
    const req = controller.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({} as PatientDto);
  });
});
