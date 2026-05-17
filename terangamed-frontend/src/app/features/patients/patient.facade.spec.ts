import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PatientFacade } from './patient.facade';
import { PatientApi } from '@api/patient.api';
import { PatientDto } from '@api/models/patient.model';
import { Page } from '@api/common.types';

function fakePatient(overrides: Partial<PatientDto> = {}): PatientDto {
  return {
    id: 1,
    medicalRecordNumber: 'MRN-001',
    civility: 'M',
    lastName: 'Diop',
    firstName: 'Mamadou',
    birthDate: '1985-06-15',
    gender: 'MALE',
    phone: '+221 77 123 45 67',
    email: 'mamadou.diop@example.sn',
    addressLine1: '123 Rue Felix Faure',
    addressLine2: null,
    postalCode: '12500',
    city: 'Dakar',
    country: 'Sénégal',
    bloodGroup: 'O_POS',
    allergies: null,
    emergencyContactName: 'Aïssatou Diop',
    emergencyContactPhone: '+221 77 987 65 43',
    status: 'ACTIVE',
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    createdBy: 'admin',
    updatedBy: 'admin',
    version: 0,
    ...overrides
  };
}

function fakePage<T>(items: T[], total = items.length, page = 0, size = 20): Page<T> {
  return {
    content: items,
    page,
    size,
    totalElements: total,
    totalPages: Math.max(1, Math.ceil(total / size)),
    first: page === 0,
    last: page >= Math.ceil(total / size) - 1
  };
}

describe('PatientFacade', () => {
  let facade: PatientFacade;
  let api: {
    search: jest.Mock;
    findById: jest.Mock;
    create: jest.Mock;
    update: jest.Mock;
    archive: jest.Mock;
  };

  beforeEach(() => {
    api = {
      search: jest.fn(),
      findById: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      archive: jest.fn()
    };

    TestBed.configureTestingModule({
      providers: [PatientFacade, { provide: PatientApi, useValue: api }]
    });
    facade = TestBed.inject(PatientFacade);
  });

  describe('état initial', () => {
    it('liste vide, pas de loading, pas d\'erreur', () => {
      expect(facade.list().patients).toEqual([]);
      expect(facade.list().loading).toBe(false);
      expect(facade.list().error).toBeNull();
      expect(facade.list().totalElements).toBe(0);
    });

    it('détail vide', () => {
      expect(facade.detail().patient).toBeNull();
      expect(facade.detail().loading).toBe(false);
    });

    it('mutation idle', () => {
      expect(facade.mutation().saving).toBe(false);
      expect(facade.mutation().error).toBeNull();
      expect(facade.mutating()).toBe(false);
    });
  });

  describe('search', () => {
    it('charge la 1ère page avec les valeurs par défaut', () => {
      const patients = [fakePatient(), fakePatient({ id: 2, lastName: 'Sall' })];
      api.search.mockReturnValue(of(fakePage(patients, 2)));

      facade.search();

      expect(api.search).toHaveBeenCalledWith(
        {},
        { page: 0, size: 20, sort: 'lastName,asc' }
      );
      expect(facade.list().patients).toEqual(patients);
      expect(facade.list().totalElements).toBe(2);
      expect(facade.list().error).toBeNull();
    });

    it('propage les criteria et le pageRequest custom', () => {
      api.search.mockReturnValue(of(fakePage<PatientDto>([], 0, 1, 50)));

      facade.search(
        { lastName: 'Diop', status: 'ACTIVE' },
        { page: 1, size: 50, sort: 'createdAt,desc' }
      );

      expect(api.search).toHaveBeenCalledWith(
        { lastName: 'Diop', status: 'ACTIVE' },
        { page: 1, size: 50, sort: 'createdAt,desc' }
      );
    });

    it('met loading=true pendant la requête puis false', () => {
      api.search.mockReturnValue(of(fakePage<PatientDto>([])));
      facade.search();
      expect(facade.list().loading).toBe(false); // synchrone : déjà terminé
    });

    it('500 → met error traduite, garde patients=[]', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 500 })));

      facade.search();

      expect(facade.list().error).toBe('Erreur serveur — réessayez plus tard');
      expect(facade.list().patients).toEqual([]);
      expect(facade.list().loading).toBe(false);
    });

    it('403 → "Accès refusé"', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 403 })));
      facade.search();
      expect(facade.list().error).toBe('Accès refusé');
    });
  });

  describe('refresh', () => {
    it('rejoue la dernière recherche avec les mêmes params', () => {
      api.search.mockReturnValue(of(fakePage<PatientDto>([])));

      facade.search({ city: 'Thiès' }, { page: 2, size: 10, sort: 'lastName,asc' });
      api.search.mockClear();

      facade.refresh();

      expect(api.search).toHaveBeenCalledWith(
        { city: 'Thiès' },
        { page: 2, size: 10, sort: 'lastName,asc' }
      );
    });
  });

  describe('loadDetail', () => {
    it('charge le patient par id', () => {
      const p = fakePatient({ id: 42 });
      api.findById.mockReturnValue(of(p));

      facade.loadDetail(42);

      expect(api.findById).toHaveBeenCalledWith(42);
      expect(facade.detail().patient).toEqual(p);
      expect(facade.detail().error).toBeNull();
    });

    it('404 → "Patient introuvable"', () => {
      api.findById.mockReturnValue(throwError(() => ({ status: 404 })));

      facade.loadDetail(999);

      expect(facade.detail().error).toBe('Patient introuvable');
      expect(facade.detail().patient).toBeNull();
    });
  });

  describe('clearDetail', () => {
    it('remet l\'état détail à l\'initial', () => {
      api.findById.mockReturnValue(of(fakePatient()));
      facade.loadDetail(1);
      facade.clearDetail();
      expect(facade.detail().patient).toBeNull();
      expect(facade.detail().loading).toBe(false);
    });
  });

  describe('create', () => {
    it('appelle l\'API et passe en mutation idle après succès', (done) => {
      const created = fakePatient({ id: 100 });
      api.create.mockReturnValue(of(created));

      facade.create({
        civility: 'M',
        lastName: 'Ndiaye',
        firstName: 'Ousmane',
        birthDate: '1990-01-01',
        gender: 'MALE'
      }).subscribe((result) => {
        expect(result).toEqual(created);
        expect(facade.mutation().saving).toBe(false);
        expect(facade.mutation().error).toBeNull();
        done();
      });
    });

    it('propage l\'erreur ET met error dans le signal', (done) => {
      api.create.mockReturnValue(throwError(() => ({ status: 400, message: 'Bad request' })));

      facade.create({
        civility: 'M',
        lastName: 'X',
        firstName: 'Y',
        birthDate: '1990-01-01',
        gender: 'MALE'
      }).subscribe({
        next: () => done.fail('ne devrait pas réussir'),
        error: () => {
          expect(facade.mutation().saving).toBe(false);
          expect(facade.mutation().error).toBe('Erreur HTTP 400');
          done();
        }
      });
    });
  });

  describe('update', () => {
    it('met à jour le détail si on observe le même patient', (done) => {
      const initial = fakePatient({ id: 5, firstName: 'Avant' });
      const updated = fakePatient({ id: 5, firstName: 'Après', version: 1 });
      api.findById.mockReturnValue(of(initial));
      api.update.mockReturnValue(of(updated));

      facade.loadDetail(5);
      expect(facade.detail().patient?.firstName).toBe('Avant');

      facade.update(5, { firstName: 'Après' }).subscribe(() => {
        expect(facade.detail().patient?.firstName).toBe('Après');
        expect(facade.detail().patient?.version).toBe(1);
        done();
      });
    });

    it('ne touche pas au détail si l\'id est différent', (done) => {
      const otherInDetail = fakePatient({ id: 99 });
      api.findById.mockReturnValue(of(otherInDetail));
      api.update.mockReturnValue(of(fakePatient({ id: 5 })));

      facade.loadDetail(99);
      facade.update(5, { firstName: 'X' }).subscribe(() => {
        expect(facade.detail().patient?.id).toBe(99);
        done();
      });
    });

    it('409 → "Conflit — données modifiées..."', (done) => {
      api.update.mockReturnValue(throwError(() => ({ status: 409 })));

      facade.update(1, { firstName: 'X' }).subscribe({
        next: () => done.fail(),
        error: () => {
          expect(facade.mutation().error).toContain('Conflit');
          done();
        }
      });
    });
  });

  describe('archive', () => {
    it('marque le détail comme ARCHIVED après succès', (done) => {
      const p = fakePatient({ id: 10, status: 'ACTIVE' });
      api.findById.mockReturnValue(of(p));
      api.archive.mockReturnValue(of(undefined));

      facade.loadDetail(10);
      facade.archive(10).subscribe(() => {
        expect(facade.detail().patient?.status).toBe('ARCHIVED');
        done();
      });
    });

    it('403 (DOCTOR/RECEPTIONIST tentant l\'archivage) → "Accès refusé"', (done) => {
      api.archive.mockReturnValue(throwError(() => ({ status: 403 })));

      facade.archive(1).subscribe({
        next: () => done.fail(),
        error: () => {
          expect(facade.mutation().error).toBe('Accès refusé');
          done();
        }
      });
    });
  });

  describe('reset', () => {
    it('remet tous les états à leur valeur initiale', () => {
      api.search.mockReturnValue(of(fakePage([fakePatient()], 1)));
      facade.search();
      facade.reset();

      expect(facade.list().patients).toEqual([]);
      expect(facade.detail().patient).toBeNull();
      expect(facade.mutation().saving).toBe(false);
    });
  });

  describe('traduction des erreurs', () => {
    it.each([
      [401, 'Non authentifié'],
      [403, 'Accès refusé'],
      [404, 'Patient introuvable'],
      [409, expect.stringContaining('Conflit')],
      [0, 'Serveur injoignable'],
      [500, 'Erreur serveur — réessayez plus tard'],
      [503, 'Erreur serveur — réessayez plus tard'],
      [400, 'Erreur HTTP 400']
    ])('status %i → %s', (status, expectedMessage) => {
      api.findById.mockReturnValue(throwError(() => ({ status })));
      facade.loadDetail(1);
      expect(facade.detail().error).toEqual(expectedMessage);
    });
  });
});
