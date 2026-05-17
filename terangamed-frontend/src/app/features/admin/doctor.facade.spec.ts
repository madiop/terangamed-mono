import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DoctorFacade } from './doctor.facade';
import { DoctorApi } from '@api/doctor.api';
import { DoctorDto } from '@api/models/doctor.model';
import { Page } from '@api/common.types';

function fakeDoctor(overrides: Partial<DoctorDto> = {}): DoctorDto {
  return {
    id: 1,
    licenseNumber: 'MED-000001',
    lastName: 'Sow',
    firstName: 'Awa',
    specialty: 'GENERAL_MEDICINE',
    email: 'awa.sow@terangamed.sn',
    phone: '+221 77 111 22 33',
    officeAddress: 'Cabinet Médical Plateau, Dakar',
    yearsOfExperience: 12,
    consultationFee: 15000,
    consultationFeeCurrency: 'XOF',
    bio: 'Médecin généraliste exerçant à Dakar depuis 12 ans.',
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

describe('DoctorFacade', () => {
  let facade: DoctorFacade;
  let api: {
    search: jest.Mock;
    searchActive: jest.Mock;
    findById: jest.Mock;
    findByLicense: jest.Mock;
    create: jest.Mock;
    update: jest.Mock;
    putOnLeave: jest.Mock;
    retire: jest.Mock;
    reactivate: jest.Mock;
    delete: jest.Mock;
  };

  beforeEach(() => {
    api = {
      search: jest.fn(),
      searchActive: jest.fn(),
      findById: jest.fn(),
      findByLicense: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      putOnLeave: jest.fn(),
      retire: jest.fn(),
      reactivate: jest.fn(),
      delete: jest.fn()
    };

    TestBed.configureTestingModule({
      providers: [DoctorFacade, { provide: DoctorApi, useValue: api }]
    });
    facade = TestBed.inject(DoctorFacade);
  });

  describe('état initial', () => {
    it('liste vide, pas de loading, pas d\'erreur', () => {
      expect(facade.list().doctors).toEqual([]);
      expect(facade.list().loading).toBe(false);
      expect(facade.list().error).toBeNull();
      expect(facade.list().totalElements).toBe(0);
    });

    it('détail vide', () => {
      expect(facade.detail().doctor).toBeNull();
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
      const doctors = [fakeDoctor(), fakeDoctor({ id: 2, lastName: 'Diop' })];
      api.search.mockReturnValue(of(fakePage(doctors, 2)));

      facade.search();

      expect(api.search).toHaveBeenCalledWith(
        {},
        { page: 0, size: 20, sort: 'lastName,asc' }
      );
      expect(facade.list().doctors).toEqual(doctors);
      expect(facade.list().totalElements).toBe(2);
      expect(facade.list().error).toBeNull();
    });

    it('propage les criteria et le pageRequest custom', () => {
      api.search.mockReturnValue(of(fakePage<DoctorDto>([], 0, 1, 50)));

      facade.search(
        { lastName: 'Sow', specialty: 'CARDIOLOGY', status: 'ACTIVE' },
        { page: 1, size: 50, sort: 'createdAt,desc' }
      );

      expect(api.search).toHaveBeenCalledWith(
        { lastName: 'Sow', specialty: 'CARDIOLOGY', status: 'ACTIVE' },
        { page: 1, size: 50, sort: 'createdAt,desc' }
      );
    });

    it('500 → met error traduite, garde doctors=[]', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 500 })));

      facade.search();

      expect(facade.list().error).toBe('Erreur serveur — réessayez plus tard');
      expect(facade.list().doctors).toEqual([]);
      expect(facade.list().loading).toBe(false);
    });

    it('403 → "Accès refusé"', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 403 })));
      facade.search();
      expect(facade.list().error).toBe('Accès refusé');
    });

    it('404 (list) → message dédié sur le service', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 404 })));
      facade.search();
      expect(facade.list().error).toBe(
        'Service médecin indisponible — vérifiez la connexion au backend'
      );
    });
  });

  describe('refresh', () => {
    it('rejoue la dernière recherche avec les mêmes params', () => {
      api.search.mockReturnValue(of(fakePage<DoctorDto>([])));

      facade.search(
        { specialty: 'PEDIATRICS' },
        { page: 2, size: 10, sort: 'lastName,asc' }
      );
      api.search.mockClear();

      facade.refresh();

      expect(api.search).toHaveBeenCalledWith(
        { specialty: 'PEDIATRICS' },
        { page: 2, size: 10, sort: 'lastName,asc' }
      );
    });
  });

  describe('loadDetail', () => {
    it('charge le médecin par id', () => {
      const d = fakeDoctor({ id: 42 });
      api.findById.mockReturnValue(of(d));

      facade.loadDetail(42);

      expect(api.findById).toHaveBeenCalledWith(42);
      expect(facade.detail().doctor).toEqual(d);
      expect(facade.detail().error).toBeNull();
    });

    it('404 → "Médecin introuvable"', () => {
      api.findById.mockReturnValue(throwError(() => ({ status: 404 })));

      facade.loadDetail(999);

      expect(facade.detail().error).toBe('Médecin introuvable');
      expect(facade.detail().doctor).toBeNull();
    });
  });

  describe('clearDetail', () => {
    it('remet l\'état détail à l\'initial', () => {
      api.findById.mockReturnValue(of(fakeDoctor()));
      facade.loadDetail(1);
      facade.clearDetail();
      expect(facade.detail().doctor).toBeNull();
      expect(facade.detail().loading).toBe(false);
    });
  });

  describe('create', () => {
    it('appelle l\'API et passe en mutation idle après succès', (done) => {
      const created = fakeDoctor({ id: 100, licenseNumber: 'MED-000100' });
      api.create.mockReturnValue(of(created));

      facade
        .create({
          lastName: 'Ndiaye',
          firstName: 'Fatou',
          specialty: 'GYNECOLOGY'
        })
        .subscribe((result) => {
          expect(result).toEqual(created);
          expect(facade.mutation().saving).toBe(false);
          expect(facade.mutation().error).toBeNull();
          done();
        });
    });

    it('propage l\'erreur ET met error dans le signal (422)', (done) => {
      api.create.mockReturnValue(throwError(() => ({ status: 422 })));

      facade
        .create({
          lastName: 'X',
          firstName: 'Y',
          specialty: 'OTHER'
        })
        .subscribe({
          next: () => done.fail('ne devrait pas réussir'),
          error: () => {
            expect(facade.mutation().saving).toBe(false);
            expect(facade.mutation().error).toBe(
              'Données invalides — vérifiez les champs du formulaire'
            );
            done();
          }
        });
    });
  });

  describe('update', () => {
    it('met à jour le détail si on observe le même médecin', (done) => {
      const initial = fakeDoctor({ id: 5, firstName: 'Avant', version: 0 });
      const updated = fakeDoctor({ id: 5, firstName: 'Après', version: 1 });
      api.findById.mockReturnValue(of(initial));
      api.update.mockReturnValue(of(updated));

      facade.loadDetail(5);
      expect(facade.detail().doctor?.firstName).toBe('Avant');

      facade.update(5, { firstName: 'Après' }).subscribe(() => {
        expect(facade.detail().doctor?.firstName).toBe('Après');
        expect(facade.detail().doctor?.version).toBe(1);
        done();
      });
    });

    it('ne touche pas au détail si l\'id est différent', (done) => {
      const otherInDetail = fakeDoctor({ id: 99 });
      api.findById.mockReturnValue(of(otherInDetail));
      api.update.mockReturnValue(of(fakeDoctor({ id: 5 })));

      facade.loadDetail(99);
      facade.update(5, { firstName: 'X' }).subscribe(() => {
        expect(facade.detail().doctor?.id).toBe(99);
        done();
      });
    });

    it('409 → message de conflit transition d\'état', (done) => {
      api.update.mockReturnValue(throwError(() => ({ status: 409 })));

      facade.update(1, { firstName: 'X' }).subscribe({
        next: () => done.fail(),
        error: () => {
          expect(facade.mutation().error).toContain('Transition d');
          done();
        }
      });
    });
  });

  describe('putOnLeave', () => {
    it('passe le statut détail à ON_LEAVE après succès', (done) => {
      const d = fakeDoctor({ id: 10, status: 'ACTIVE' });
      api.findById.mockReturnValue(of(d));
      api.putOnLeave.mockReturnValue(of(undefined));

      facade.loadDetail(10);
      facade.putOnLeave(10).subscribe(() => {
        expect(facade.detail().doctor?.status).toBe('ON_LEAVE');
        expect(facade.mutation().saving).toBe(false);
        done();
      });
    });

    it('403 → "Accès refusé"', (done) => {
      api.putOnLeave.mockReturnValue(throwError(() => ({ status: 403 })));
      facade.putOnLeave(1).subscribe({
        next: () => done.fail(),
        error: () => {
          expect(facade.mutation().error).toBe('Accès refusé');
          done();
        }
      });
    });
  });

  describe('retire', () => {
    it('passe le statut détail à RETIRED après succès', (done) => {
      const d = fakeDoctor({ id: 11, status: 'ACTIVE' });
      api.findById.mockReturnValue(of(d));
      api.retire.mockReturnValue(of(undefined));

      facade.loadDetail(11);
      facade.retire(11).subscribe(() => {
        expect(facade.detail().doctor?.status).toBe('RETIRED');
        done();
      });
    });

    it('depuis ON_LEAVE → RETIRED également supporté', (done) => {
      const d = fakeDoctor({ id: 12, status: 'ON_LEAVE' });
      api.findById.mockReturnValue(of(d));
      api.retire.mockReturnValue(of(undefined));

      facade.loadDetail(12);
      facade.retire(12).subscribe(() => {
        expect(facade.detail().doctor?.status).toBe('RETIRED');
        done();
      });
    });
  });

  describe('reactivate', () => {
    it('depuis ON_LEAVE → ACTIVE', (done) => {
      const d = fakeDoctor({ id: 20, status: 'ON_LEAVE' });
      api.findById.mockReturnValue(of(d));
      api.reactivate.mockReturnValue(of(undefined));

      facade.loadDetail(20);
      facade.reactivate(20).subscribe(() => {
        expect(facade.detail().doctor?.status).toBe('ACTIVE');
        done();
      });
    });

    it('depuis RETIRED → ACTIVE (retour de retraite — autorisé en UI)', (done) => {
      const d = fakeDoctor({ id: 21, status: 'RETIRED' });
      api.findById.mockReturnValue(of(d));
      api.reactivate.mockReturnValue(of(undefined));

      facade.loadDetail(21);
      facade.reactivate(21).subscribe(() => {
        expect(facade.detail().doctor?.status).toBe('ACTIVE');
        done();
      });
    });
  });

  describe('delete', () => {
    it('vide le détail si l\'id correspond au médecin chargé', (done) => {
      const d = fakeDoctor({ id: 30 });
      api.findById.mockReturnValue(of(d));
      api.delete.mockReturnValue(of(undefined));

      facade.loadDetail(30);
      expect(facade.detail().doctor?.id).toBe(30);

      facade.delete(30).subscribe(() => {
        expect(facade.detail().doctor).toBeNull();
        done();
      });
    });

    it('ne touche pas au détail si un autre médecin est chargé', (done) => {
      const other = fakeDoctor({ id: 99 });
      api.findById.mockReturnValue(of(other));
      api.delete.mockReturnValue(of(undefined));

      facade.loadDetail(99);
      facade.delete(30).subscribe(() => {
        expect(facade.detail().doctor?.id).toBe(99);
        done();
      });
    });
  });

  describe('reset', () => {
    it('remet tous les états à leur valeur initiale', () => {
      api.search.mockReturnValue(of(fakePage([fakeDoctor()], 1)));
      facade.search();
      facade.reset();

      expect(facade.list().doctors).toEqual([]);
      expect(facade.detail().doctor).toBeNull();
      expect(facade.mutation().saving).toBe(false);
    });
  });

  describe('traduction des erreurs', () => {
    it.each([
      [401, 'Non authentifié'],
      [403, 'Accès refusé'],
      [404, 'Médecin introuvable'],
      [422, 'Données invalides — vérifiez les champs du formulaire'],
      [500, 'Erreur serveur — réessayez plus tard'],
      [502, 'Erreur serveur — réessayez plus tard'],
      [0, 'Serveur injoignable']
    ])('status %s → %s (contexte detail)', (status, expected) => {
      api.findById.mockReturnValue(throwError(() => ({ status })));
      facade.loadDetail(1);
      expect(facade.detail().error).toBe(expected);
    });

    it('status arbitraire (418) → "Erreur HTTP 418"', () => {
      api.findById.mockReturnValue(throwError(() => ({ status: 418 })));
      facade.loadDetail(1);
      expect(facade.detail().error).toBe('Erreur HTTP 418');
    });

    it('erreur sans status (Error JS) → message du Error', () => {
      api.findById.mockReturnValue(throwError(() => new Error('Network failed')));
      facade.loadDetail(1);
      expect(facade.detail().error).toBe('Network failed');
    });
  });
});
