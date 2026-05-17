import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DashboardFacade } from './dashboard.facade';
import { AppointmentApi } from '@api/appointment.api';
import { MedicalRecordApi } from '@api/medical-record.api';
import { Page } from '@api/common.types';
import { AppointmentDto } from '@api/models/appointment.model';
import { ConsultationDto } from '@api/models/medical-record.model';

function fakePage<T>(total: number): Page<T> {
  return {
    content: [],
    page: 0,
    size: 1,
    totalElements: total,
    totalPages: total > 0 ? 1 : 0,
    first: true,
    last: true
  };
}

describe('DashboardFacade', () => {
  let facade: DashboardFacade;
  let appointmentApi: { search: jest.Mock };
  let medicalRecordApi: { searchConsultations: jest.Mock };

  beforeEach(() => {
    appointmentApi = { search: jest.fn() };
    medicalRecordApi = { searchConsultations: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        DashboardFacade,
        { provide: AppointmentApi, useValue: appointmentApi },
        { provide: MedicalRecordApi, useValue: medicalRecordApi }
      ]
    });
    facade = TestBed.inject(DashboardFacade);
  });

  describe('état initial', () => {
    it('tous les KPI sont à null/idle', () => {
      expect(facade.appointmentsToday().value).toBeNull();
      expect(facade.appointmentsToday().loading).toBe(false);
      expect(facade.patientsWaiting().value).toBeNull();
      expect(facade.consultationsPending().value).toBeNull();
      expect(facade.pendingInvoices().value).toBe(0);
      expect(facade.anyLoading()).toBe(false);
    });
  });

  describe('refresh — appels parallèles', () => {
    it('charge les 3 KPI dynamiques', () => {
      // 3 appels à appointmentApi.search dans refresh() :
      // 1) loadAppointmentsToday  2) loadPatientsWaiting  3) loadWeek
      // mockReturnValueOnce queue les 2 premiers, mockReturnValue couvre loadWeek.
      appointmentApi.search
        .mockReturnValueOnce(of(fakePage<AppointmentDto>(8)))
        .mockReturnValueOnce(of(fakePage<AppointmentDto>(2)))
        .mockReturnValue(of(fakePage<AppointmentDto>(0)));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(1)));

      facade.refresh();

      expect(facade.appointmentsToday().value).toBe(8);
      expect(facade.patientsWaiting().value).toBe(2);
      expect(facade.consultationsPending().value).toBe(1);
      expect(facade.appointmentsToday().error).toBeNull();
    });

    it('passe today comme fromDate=toDate sur appointments', () => {
      appointmentApi.search.mockReturnValue(of(fakePage<AppointmentDto>(0)));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      const firstCall = appointmentApi.search.mock.calls[0][0];
      expect(firstCall).toMatchObject({
        fromDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        toDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/)
      });
      expect(firstCall.fromDate).toBe(firstCall.toDate);
    });

    it('le 2e appel filtre status=CONFIRMED (patients en attente)', () => {
      appointmentApi.search.mockReturnValue(of(fakePage<AppointmentDto>(0)));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      const secondCall = appointmentApi.search.mock.calls[1][0];
      expect(secondCall).toMatchObject({ status: 'CONFIRMED' });
    });

    it('cherche les consultations non signées', () => {
      appointmentApi.search.mockReturnValue(of(fakePage<AppointmentDto>(0)));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      expect(medicalRecordApi.searchConsultations).toHaveBeenCalledWith(
        { signed: false },
        { page: 0, size: 1 }
      );
    });
  });

  describe('gestion des erreurs', () => {
    it('une erreur sur appointments ne casse pas les autres KPI', () => {
      // Idem : 3 appels search, on couvre les 2 premiers + un fallback pour loadWeek.
      appointmentApi.search
        .mockReturnValueOnce(throwError(() => ({ status: 500, message: 'Boom' })))
        .mockReturnValueOnce(of(fakePage<AppointmentDto>(2)))
        .mockReturnValue(of(fakePage<AppointmentDto>(0)));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(1)));

      facade.refresh();

      expect(facade.appointmentsToday().error).toBeTruthy();
      expect(facade.appointmentsToday().value).toBeNull();
      expect(facade.patientsWaiting().value).toBe(2);
      expect(facade.consultationsPending().value).toBe(1);
    });

    it('traduit 401 en "Non authentifié"', () => {
      appointmentApi.search.mockReturnValue(throwError(() => ({ status: 401 })));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      expect(facade.appointmentsToday().error).toBe('Non authentifié');
    });

    it('traduit 403 en "Accès refusé"', () => {
      appointmentApi.search.mockReturnValue(throwError(() => ({ status: 403 })));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      expect(facade.appointmentsToday().error).toBe('Accès refusé');
    });

    it('traduit 0 (network) en "Serveur injoignable"', () => {
      appointmentApi.search.mockReturnValue(throwError(() => ({ status: 0 })));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      expect(facade.appointmentsToday().error).toBe('Serveur injoignable');
    });
  });

  describe('placeholder factures', () => {
    it('reste à 0 même après refresh (billing pas encore implémenté)', () => {
      appointmentApi.search.mockReturnValue(of(fakePage<AppointmentDto>(0)));
      medicalRecordApi.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>(0)));

      facade.refresh();

      expect(facade.pendingInvoices().value).toBe(0);
      expect(facade.pendingInvoices().error).toBeNull();
    });
  });

  describe('selectPatient', () => {
    it('démarre à null', () => {
      expect(facade.selectedPatientId()).toBeNull();
    });

    it('set un patientId', () => {
      facade.selectPatient(42);
      expect(facade.selectedPatientId()).toBe(42);
    });

    it('peut désélectionner avec null', () => {
      facade.selectPatient(42);
      facade.selectPatient(null);
      expect(facade.selectedPatientId()).toBeNull();
    });
  });

  describe('loadWeek', () => {
    function pageWith<T>(events: T[]): Page<T> {
      return {
        content: events,
        page: 0,
        size: events.length,
        totalElements: events.length,
        totalPages: 1,
        first: true,
        last: true
      };
    }

    it('charge les RDV de la semaine du lundi au dimanche', () => {
      const fakeAppt = { id: 1, patientNameSnapshot: 'X' } as unknown as AppointmentDto;
      appointmentApi.search.mockReturnValue(of(pageWith([fakeAppt])));

      // Mardi 14 avril 2026
      facade.loadWeek(new Date('2026-04-14T10:00:00'));

      expect(appointmentApi.search).toHaveBeenCalledWith(
        expect.objectContaining({
          fromDate: '2026-04-13', // lundi
          toDate: '2026-04-19'    // dimanche
        }),
        expect.objectContaining({ page: 0, size: 200 })
      );
      expect(facade.weekPlanning().events).toHaveLength(1);
      expect(facade.weekPlanning().error).toBeNull();
    });

    it('met error si l\'API échoue, events restent vides', () => {
      appointmentApi.search.mockReturnValue(throwError(() => ({ status: 500 })));
      facade.loadWeek(new Date('2026-04-14'));
      expect(facade.weekPlanning().error).toBeTruthy();
      expect(facade.weekPlanning().events).toEqual([]);
    });
  });

  describe('refresh par scope (variantes par rôle)', () => {
    function pageWith<T>(events: T[], total = events.length): Page<T> {
      return {
        content: events,
        page: 0,
        size: events.length || 1,
        totalElements: total,
        totalPages: total > 0 ? 1 : 0,
        first: true,
        last: true
      };
    }

    describe('mode DOCTOR', () => {
      it('propage doctorId aux 3 KPI dynamiques + planning + nextAppointment', () => {
        appointmentApi.search.mockReturnValue(of(pageWith<AppointmentDto>([], 5)));
        medicalRecordApi.searchConsultations.mockReturnValue(of(pageWith<ConsultationDto>([], 2)));

        facade.refresh({ mode: 'DOCTOR', doctorIdFilter: 42 });

        // 4 appels appointmentApi.search : today / waiting / week / nextAppointment
        const calls = appointmentApi.search.mock.calls;
        expect(calls.length).toBe(4);
        // Tous les criteria doivent porter doctorId=42
        expect(calls[0][0]).toMatchObject({ doctorId: 42 });
        expect(calls[1][0]).toMatchObject({ doctorId: 42, status: 'CONFIRMED' });
        expect(calls[2][0]).toMatchObject({ doctorId: 42 }); // loadWeek
        expect(calls[3][0]).toMatchObject({ doctorId: 42 }); // nextAppointment

        // Consultations aussi filtrées sur le doctor
        expect(medicalRecordApi.searchConsultations).toHaveBeenCalledWith(
          { signed: false, doctorId: 42 },
          { page: 0, size: 1 }
        );
      });

      it('charge nextAppointment et ignore les RDV COMPLETED/CANCELLED', () => {
        const past = { id: 1, status: 'COMPLETED' } as unknown as AppointmentDto;
        const cancelled = { id: 2, status: 'CANCELLED' } as unknown as AppointmentDto;
        const upcoming = { id: 3, status: 'CONFIRMED' } as unknown as AppointmentDto;

        appointmentApi.search
          // 3 KPI génériques
          .mockReturnValueOnce(of(pageWith<AppointmentDto>([], 0)))
          .mockReturnValueOnce(of(pageWith<AppointmentDto>([], 0)))
          .mockReturnValueOnce(of(pageWith<AppointmentDto>([], 0)))
          // nextAppointment — renvoie une liste avec passé/annulé en tête
          .mockReturnValueOnce(of(pageWith<AppointmentDto>([past, cancelled, upcoming])));
        medicalRecordApi.searchConsultations.mockReturnValue(of(pageWith<ConsultationDto>([], 0)));

        facade.refresh({ mode: 'DOCTOR', doctorIdFilter: 42 });

        expect(facade.nextAppointment().appointment).toBe(upcoming);
        expect(facade.nextAppointment().error).toBeNull();
      });

      it('si doctorIdFilter=null, ne charge pas nextAppointment', () => {
        appointmentApi.search.mockReturnValue(of(pageWith<AppointmentDto>([], 0)));
        medicalRecordApi.searchConsultations.mockReturnValue(of(pageWith<ConsultationDto>([], 0)));

        facade.refresh({ mode: 'DOCTOR', doctorIdFilter: null });

        // 3 appels (sans nextAppointment)
        expect(appointmentApi.search.mock.calls.length).toBe(3);
        expect(facade.nextAppointment().appointment).toBeNull();
      });
    });

    describe('mode RECEPTIONIST', () => {
      it('charge plannedAppointments en plus des 3 KPI génériques', () => {
        appointmentApi.search.mockReturnValue(of(pageWith<AppointmentDto>([], 7)));
        medicalRecordApi.searchConsultations.mockReturnValue(of(pageWith<ConsultationDto>([], 0)));

        facade.refresh({ mode: 'RECEPTIONIST', doctorIdFilter: null });

        // 4 appels search : today / waiting / week / plannedAppointments
        const calls = appointmentApi.search.mock.calls;
        expect(calls.length).toBe(4);
        // Le 4ᵉ filtre PLANNED + fromDate aujourd'hui
        expect(calls[3][0]).toMatchObject({ status: 'PLANNED' });
        expect(calls[3][0].fromDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        expect(facade.plannedAppointments().value).toBe(7);
      });

      it('aucun KPI n\'est filtré par doctorId (vue globale)', () => {
        appointmentApi.search.mockReturnValue(of(pageWith<AppointmentDto>([], 0)));
        medicalRecordApi.searchConsultations.mockReturnValue(of(pageWith<ConsultationDto>([], 0)));

        facade.refresh({ mode: 'RECEPTIONIST', doctorIdFilter: null });

        for (const call of appointmentApi.search.mock.calls) {
          expect(call[0].doctorId).toBeUndefined();
        }
        expect(medicalRecordApi.searchConsultations).toHaveBeenCalledWith(
          { signed: false }, // pas de doctorId
          expect.any(Object)
        );
      });
    });

    describe('mode ADMIN', () => {
      it('vue globale + pendingInvoices en placeholder, pas de doctorId', () => {
        appointmentApi.search.mockReturnValue(of(pageWith<AppointmentDto>([], 0)));
        medicalRecordApi.searchConsultations.mockReturnValue(of(pageWith<ConsultationDto>([], 0)));

        facade.refresh({ mode: 'ADMIN', doctorIdFilter: null });

        // 3 appels search (pas de loadNextAppointment ni loadPlannedAppointments)
        expect(appointmentApi.search.mock.calls.length).toBe(3);
        // pendingInvoices reste à son placeholder
        expect(facade.pendingInvoices().value).toBe(0);
        expect(facade.pendingInvoices().error).toBeNull();
      });
    });
  });
});
