import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CurrentDoctorService } from './current-doctor.service';
import { AuthService } from './auth.service';
import { DoctorApi } from '@api/doctor.api';
import { DoctorDto } from '@api/models/doctor.model';
import { AuthUser } from './auth.types';

function fakeUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    sub: 'sub-123',
    username: 'dr.diop',
    email: 'mamadou.diop@terangamed.sn',
    firstName: 'Mamadou',
    lastName: 'Diop',
    roles: ['DOCTOR'],
    ...overrides
  };
}

function fakeDoctor(overrides: Partial<DoctorDto> = {}): DoctorDto {
  return {
    id: 42,
    licenseNumber: 'SN-001',
    lastName: 'Diop',
    firstName: 'Mamadou',
    specialty: 'GENERAL_MEDICINE',
    email: 'mamadou.diop@terangamed.sn',
    phone: null,
    officeAddress: null,
    yearsOfExperience: 10,
    consultationFee: 25000,
    consultationFeeCurrency: 'XOF',
    bio: null,
    status: 'ACTIVE',
    keycloakSubject: 'sub-123',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    createdBy: null,
    updatedBy: null,
    version: 0,
    ...overrides
  };
}

describe('CurrentDoctorService', () => {
  let service: CurrentDoctorService;
  let auth: { currentUser: jest.Mock; hasAnyRole: jest.Mock };
  let doctorApi: { findMe: jest.Mock };

  beforeEach(() => {
    auth = {
      currentUser: jest.fn().mockReturnValue(fakeUser()),
      hasAnyRole: jest.fn((roles: string[]) => roles.includes('DOCTOR'))
    };
    doctorApi = { findMe: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        CurrentDoctorService,
        { provide: AuthService, useValue: auth },
        { provide: DoctorApi, useValue: doctorApi }
      ]
    });
    service = TestBed.inject(CurrentDoctorService);
  });

  describe('état initial', () => {
    it('démarre en IDLE / null / pas de loading', () => {
      expect(service.state().status).toBe('IDLE');
      expect(service.myDoctorId()).toBeNull();
      expect(service.loading()).toBe(false);
      expect(service.notFound()).toBe(false);
    });
  });

  describe('résolution réussie', () => {
    it('charge le DoctorDto via /api/doctors/me', (done) => {
      const doctor = fakeDoctor();
      doctorApi.findMe.mockReturnValue(of(doctor));

      service.resolve().subscribe((result) => {
        expect(result).toEqual(doctor);
        expect(service.state().status).toBe('RESOLVED');
        expect(service.myDoctorId()).toBe(42);
        expect(doctorApi.findMe).toHaveBeenCalledTimes(1);
        done();
      });
    });

    it('met cache : la 2e résolution ne refait pas d\'appel HTTP', (done) => {
      const doctor = fakeDoctor();
      doctorApi.findMe.mockReturnValue(of(doctor));

      service.resolve().subscribe(() => {
        service.resolve().subscribe((result) => {
          expect(result).toEqual(doctor);
          expect(doctorApi.findMe).toHaveBeenCalledTimes(1);
          done();
        });
      });
    });
  });

  describe('cas NOT_FOUND', () => {
    it('404 sur /me → NOT_FOUND avec message admin', (done) => {
      doctorApi.findMe.mockReturnValue(throwError(() => ({ status: 404 })));

      service.resolve().subscribe((result) => {
        expect(result).toBeNull();
        expect(service.state().status).toBe('NOT_FOUND');
        expect(service.notFound()).toBe(true);
        expect(service.state().errorMessage).toContain('administrateur');
        done();
      });
    });
  });

  describe('user non DOCTOR', () => {
    it('renvoie null sans appeler l\'API ni changer d\'état', (done) => {
      auth.hasAnyRole.mockReturnValue(false);
      auth.currentUser.mockReturnValue(fakeUser({ roles: ['ADMIN'] }));

      service.resolve().subscribe((result) => {
        expect(result).toBeNull();
        expect(service.state().status).toBe('IDLE');
        expect(doctorApi.findMe).not.toHaveBeenCalled();
        done();
      });
    });
  });

  describe('gestion d\'erreur', () => {
    it('500 → état ERROR avec message traduit', (done) => {
      doctorApi.findMe.mockReturnValue(throwError(() => ({ status: 500 })));

      service.resolve().subscribe((result) => {
        expect(result).toBeNull();
        expect(service.state().status).toBe('ERROR');
        expect(service.state().errorMessage).toBe('Erreur HTTP 500');
        done();
      });
    });

    it('0 (réseau) → "Serveur injoignable"', (done) => {
      doctorApi.findMe.mockReturnValue(throwError(() => ({ status: 0 })));

      service.resolve().subscribe(() => {
        expect(service.state().errorMessage).toBe('Serveur injoignable');
        done();
      });
    });

    it('403 → "Accès refusé"', (done) => {
      doctorApi.findMe.mockReturnValue(throwError(() => ({ status: 403 })));

      service.resolve().subscribe(() => {
        expect(service.state().errorMessage).toBe('Accès refusé');
        done();
      });
    });
  });

  describe('reset', () => {
    it('remet l\'état à IDLE et permet une nouvelle résolution', (done) => {
      const doctor = fakeDoctor();
      doctorApi.findMe.mockReturnValue(of(doctor));

      service.resolve().subscribe(() => {
        expect(service.state().status).toBe('RESOLVED');
        service.reset();
        expect(service.state().status).toBe('IDLE');
        expect(service.myDoctorId()).toBeNull();

        // Une nouvelle résolution refait bien l'appel HTTP
        service.resolve().subscribe(() => {
          expect(doctorApi.findMe).toHaveBeenCalledTimes(2);
          done();
        });
      });
    });
  });
});
