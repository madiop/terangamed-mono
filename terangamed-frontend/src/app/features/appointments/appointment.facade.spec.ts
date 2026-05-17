import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AppointmentFacade } from './appointment.facade';
import { AppointmentApi } from '@api/appointment.api';
import { AppointmentDto, AppointmentStatus } from '@api/models/appointment.model';
import { Page } from '@api/common.types';

function fakeAppointment(overrides: Partial<AppointmentDto> = {}): AppointmentDto {
  return {
    id: 1,
    patientId: 10,
    doctorId: 20,
    patientNameSnapshot: 'Diop Fatou',
    doctorNameSnapshot: 'Dr Sall',
    startTime: '2026-04-15T10:00:00Z',
    endTime: '2026-04-15T10:30:00Z',
    durationMinutes: 30,
    reason: 'Contrôle annuel',
    notes: null,
    status: 'PLANNED',
    createdAt: '2026-04-10T12:00:00Z',
    updatedAt: '2026-04-10T12:00:00Z',
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

describe('AppointmentFacade', () => {
  let facade: AppointmentFacade;
  let api: {
    search: jest.Mock;
    findById: jest.Mock;
    create: jest.Mock;
    update: jest.Mock;
    confirm: jest.Mock;
    complete: jest.Mock;
    cancel: jest.Mock;
    markNoShow: jest.Mock;
    delete: jest.Mock;
  };

  beforeEach(() => {
    api = {
      search: jest.fn(),
      findById: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      confirm: jest.fn(),
      complete: jest.fn(),
      cancel: jest.fn(),
      markNoShow: jest.fn(),
      delete: jest.fn()
    };
    TestBed.configureTestingModule({
      providers: [AppointmentFacade, { provide: AppointmentApi, useValue: api }]
    });
    facade = TestBed.inject(AppointmentFacade);
  });

  describe('état initial', () => {
    it('liste vide / detail vide / mutation idle', () => {
      expect(facade.list().appointments).toEqual([]);
      expect(facade.list().error).toBeNull();
      expect(facade.detail().appointment).toBeNull();
      expect(facade.mutation().saving).toBe(false);
      expect(facade.mutating()).toBe(false);
    });
  });

  describe('search', () => {
    it('charge la 1ère page avec valeurs par défaut (sort=startTime,desc)', () => {
      const items = [fakeAppointment(), fakeAppointment({ id: 2 })];
      api.search.mockReturnValue(of(fakePage(items, 2)));

      facade.search();

      expect(api.search).toHaveBeenCalledWith(
        {},
        { page: 0, size: 20, sort: 'startTime,desc' }
      );
      expect(facade.list().appointments).toEqual(items);
      expect(facade.list().totalElements).toBe(2);
    });

    it('propage les criteria (status, doctor, dateRange)', () => {
      api.search.mockReturnValue(of(fakePage<AppointmentDto>([])));

      facade.search(
        { status: 'CONFIRMED', doctorId: 5, fromDate: '2026-04-15', toDate: '2026-04-20' },
        { page: 1, size: 50, sort: 'startTime,asc' }
      );

      expect(api.search).toHaveBeenCalledWith(
        { status: 'CONFIRMED', doctorId: 5, fromDate: '2026-04-15', toDate: '2026-04-20' },
        { page: 1, size: 50, sort: 'startTime,asc' }
      );
    });

    it('500 → message d\'erreur, liste vide', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 500 })));
      facade.search();
      expect(facade.list().error).toBe('Erreur serveur — réessayez plus tard');
      expect(facade.list().appointments).toEqual([]);
    });

    it('404 sur list → "Service rendez-vous indisponible"', () => {
      api.search.mockReturnValue(throwError(() => ({ status: 404 })));
      facade.search();
      expect(facade.list().error).toContain('Service rendez-vous indisponible');
    });
  });

  describe('refresh', () => {
    it('rejoue la dernière recherche', () => {
      api.search.mockReturnValue(of(fakePage<AppointmentDto>([])));

      facade.search({ doctorId: 7 }, { page: 2, size: 10, sort: 'startTime,desc' });
      api.search.mockClear();

      facade.refresh();

      expect(api.search).toHaveBeenCalledWith(
        { doctorId: 7 },
        { page: 2, size: 10, sort: 'startTime,desc' }
      );
    });
  });

  describe('loadDetail', () => {
    it('charge le RDV par id', () => {
      const a = fakeAppointment({ id: 42 });
      api.findById.mockReturnValue(of(a));

      facade.loadDetail(42);

      expect(api.findById).toHaveBeenCalledWith(42);
      expect(facade.detail().appointment).toEqual(a);
    });

    it('404 → "Rendez-vous introuvable"', () => {
      api.findById.mockReturnValue(throwError(() => ({ status: 404 })));
      facade.loadDetail(999);
      expect(facade.detail().error).toBe('Rendez-vous introuvable');
    });
  });

  describe('clearDetail', () => {
    it('remet l\'état détail à l\'initial', () => {
      api.findById.mockReturnValue(of(fakeAppointment()));
      facade.loadDetail(1);
      facade.clearDetail();
      expect(facade.detail().appointment).toBeNull();
    });
  });

  describe('create', () => {
    it('appelle l\'API et passe en idle après succès', (done) => {
      const created = fakeAppointment({ id: 100 });
      api.create.mockReturnValue(of(created));

      facade
        .create({
          patientId: 10,
          doctorId: 20,
          startTime: '2026-04-15T10:00:00Z',
          durationMinutes: 30
        })
        .subscribe((result) => {
          expect(result).toEqual(created);
          expect(facade.mutation().saving).toBe(false);
          expect(facade.mutation().error).toBeNull();
          done();
        });
    });

    it('409 → "Conflit — ce créneau est déjà occupé..."', (done) => {
      api.create.mockReturnValue(throwError(() => ({ status: 409 })));

      facade
        .create({
          patientId: 10,
          doctorId: 20,
          startTime: '2026-04-15T10:00:00Z',
          durationMinutes: 30
        })
        .subscribe({
          next: () => done.fail('ne devrait pas réussir'),
          error: () => {
            expect(facade.mutation().error).toContain('Conflit');
            expect(facade.mutation().error).toContain('créneau');
            done();
          }
        });
    });
  });

  describe('update', () => {
    it('met à jour le détail si on observe le même id', (done) => {
      const initial = fakeAppointment({ id: 5, durationMinutes: 30 });
      const updated = fakeAppointment({ id: 5, durationMinutes: 45, version: 1 });
      api.findById.mockReturnValue(of(initial));
      api.update.mockReturnValue(of(updated));

      facade.loadDetail(5);
      expect(facade.detail().appointment?.durationMinutes).toBe(30);

      facade.update(5, { durationMinutes: 45 }).subscribe(() => {
        expect(facade.detail().appointment?.durationMinutes).toBe(45);
        expect(facade.detail().appointment?.version).toBe(1);
        done();
      });
    });

    it('ne touche pas au détail si l\'id est différent', (done) => {
      api.findById.mockReturnValue(of(fakeAppointment({ id: 99 })));
      api.update.mockReturnValue(of(fakeAppointment({ id: 5 })));

      facade.loadDetail(99);
      facade.update(5, { reason: 'X' }).subscribe(() => {
        expect(facade.detail().appointment?.id).toBe(99);
        done();
      });
    });
  });

  describe('transitions état', () => {
    function setupDetailWith(status: AppointmentStatus): AppointmentDto {
      const a = fakeAppointment({ id: 7, status });
      api.findById.mockReturnValue(of(a));
      facade.loadDetail(7);
      return a;
    }

    it('confirm() patche le status local en CONFIRMED', (done) => {
      setupDetailWith('PLANNED');
      api.confirm.mockReturnValue(of(undefined));

      facade.confirm(7).subscribe(() => {
        expect(facade.detail().appointment?.status).toBe('CONFIRMED');
        expect(api.confirm).toHaveBeenCalledWith(7);
        done();
      });
    });

    it('complete() patche en COMPLETED', (done) => {
      setupDetailWith('CONFIRMED');
      api.complete.mockReturnValue(of(undefined));

      facade.complete(7).subscribe(() => {
        expect(facade.detail().appointment?.status).toBe('COMPLETED');
        done();
      });
    });

    it('cancel() patche en CANCELLED', (done) => {
      setupDetailWith('PLANNED');
      api.cancel.mockReturnValue(of(undefined));

      facade.cancel(7).subscribe(() => {
        expect(facade.detail().appointment?.status).toBe('CANCELLED');
        done();
      });
    });

    it('markNoShow() patche en NO_SHOW', (done) => {
      setupDetailWith('CONFIRMED');
      api.markNoShow.mockReturnValue(of(undefined));

      facade.markNoShow(7).subscribe(() => {
        expect(facade.detail().appointment?.status).toBe('NO_SHOW');
        done();
      });
    });

    it('400 sur transition invalide → message clair, statut inchangé', (done) => {
      setupDetailWith('COMPLETED');
      api.confirm.mockReturnValue(throwError(() => ({ status: 400 })));

      facade.confirm(7).subscribe({
        next: () => done.fail(),
        error: () => {
          expect(facade.mutation().error).toContain('Transition d\'état invalide');
          // Le statut local n'a PAS été modifié (l'erreur a court-circuité le tap)
          expect(facade.detail().appointment?.status).toBe('COMPLETED');
          done();
        }
      });
    });

    it('ne touche pas au détail si transition concerne un autre id', (done) => {
      const a = fakeAppointment({ id: 99, status: 'PLANNED' });
      api.findById.mockReturnValue(of(a));
      facade.loadDetail(99);
      api.confirm.mockReturnValue(of(undefined));

      facade.confirm(7).subscribe(() => {
        expect(facade.detail().appointment?.status).toBe('PLANNED');
        expect(facade.detail().appointment?.id).toBe(99);
        done();
      });
    });
  });

  describe('delete', () => {
    it('clear le détail si on observe l\'id supprimé', (done) => {
      api.findById.mockReturnValue(of(fakeAppointment({ id: 8 })));
      api.delete.mockReturnValue(of(undefined));

      facade.loadDetail(8);
      facade.delete(8).subscribe(() => {
        expect(facade.detail().appointment).toBeNull();
        done();
      });
    });

    it('garde le détail si l\'id supprimé est différent', (done) => {
      api.findById.mockReturnValue(of(fakeAppointment({ id: 99 })));
      api.delete.mockReturnValue(of(undefined));

      facade.loadDetail(99);
      facade.delete(8).subscribe(() => {
        expect(facade.detail().appointment?.id).toBe(99);
        done();
      });
    });
  });

  describe('reset', () => {
    it('remet tous les états à leur valeur initiale', () => {
      api.search.mockReturnValue(of(fakePage([fakeAppointment()], 1)));
      facade.search();
      facade.reset();

      expect(facade.list().appointments).toEqual([]);
      expect(facade.detail().appointment).toBeNull();
      expect(facade.mutation().saving).toBe(false);
    });
  });

  describe('traduction des erreurs', () => {
    it.each([
      [401, 'Non authentifié'],
      [403, 'Accès refusé'],
      [404, 'Rendez-vous introuvable'],
      [400, expect.stringContaining('Transition')],
      [409, expect.stringContaining('Conflit')],
      [0, 'Serveur injoignable'],
      [500, 'Erreur serveur — réessayez plus tard']
    ])('status %i sur loadDetail → %s', (status, expectedMessage) => {
      api.findById.mockReturnValue(throwError(() => ({ status })));
      facade.loadDetail(1);
      expect(facade.detail().error).toEqual(expectedMessage);
    });
  });
});
