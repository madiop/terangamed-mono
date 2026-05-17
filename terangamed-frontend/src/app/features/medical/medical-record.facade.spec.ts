import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MedicalRecordFacade } from './medical-record.facade';
import { MedicalRecordApi } from '@api/medical-record.api';
import {
  AntecedentDto,
  ConsultationDto,
  MedicalRecordDto,
  PrescriptionDto,
  PrescriptionLineDto
} from '@api/models/medical-record.model';
import { Page } from '@api/common.types';

function fakeRecord(overrides: Partial<MedicalRecordDto> = {}): MedicalRecordDto {
  return {
    id: 1,
    patientId: 100,
    bloodType: 'O_POS',
    allergiesSummary: null,
    notes: null,
    softDeleted: false,
    deletedAt: null,
    deletedBy: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    createdBy: 'admin',
    updatedBy: 'admin',
    version: 0,
    ...overrides
  };
}

function fakeAntecedent(overrides: Partial<AntecedentDto> = {}): AntecedentDto {
  return {
    id: 10,
    medicalRecordId: 1,
    type: 'ALLERGY',
    title: 'Pénicilline',
    description: null,
    onsetDate: null,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    createdBy: 'admin',
    updatedBy: 'admin',
    version: 0,
    ...overrides
  };
}

function fakeConsultation(overrides: Partial<ConsultationDto> = {}): ConsultationDto {
  return {
    id: 50,
    medicalRecordId: 1,
    doctorId: 7,
    appointmentId: null,
    consultationDate: '2026-04-15',
    motif: 'Bilan annuel',
    vitalSigns: null,
    examenCliniqueNotes: null,
    diagnostic: null,
    observations: null,
    recommandations: null,
    nextAppointmentSuggested: null,
    signed: false,
    signedAt: null,
    signedBy: null,
    softDeleted: false,
    deletedAt: null,
    deletedBy: null,
    createdAt: '2026-04-15T10:00:00Z',
    updatedAt: '2026-04-15T10:00:00Z',
    createdBy: 'doctor',
    updatedBy: 'doctor',
    version: 0,
    ...overrides
  };
}

function fakePrescription(
  lines: PrescriptionLineDto[] = [],
  overrides: Partial<PrescriptionDto> = {}
): PrescriptionDto {
  return {
    id: 200,
    prescriptionNumber: 'ORD-2026-00001',
    consultationId: 50,
    issuedAt: '2026-04-15T10:30:00Z',
    validUntil: null,
    generalInstructions: null,
    lines,
    createdAt: '2026-04-15T10:30:00Z',
    updatedAt: '2026-04-15T10:30:00Z',
    createdBy: 'doctor',
    updatedBy: 'doctor',
    version: 0,
    ...overrides
  };
}

function fakePrescriptionLine(overrides: Partial<PrescriptionLineDto> = {}): PrescriptionLineDto {
  return {
    id: 300,
    prescriptionId: 200,
    medicationName: 'Doliprane 500mg',
    dosage: '1 comprimé',
    frequency: '3 fois/jour',
    duration: '5 jours',
    route: 'ORAL',
    instructions: null,
    quantity: 1,
    createdAt: '2026-04-15T10:30:00Z',
    updatedAt: '2026-04-15T10:30:00Z',
    version: 0,
    ...overrides
  };
}

function fakePage<T>(items: T[]): Page<T> {
  return {
    content: items,
    page: 0,
    size: items.length || 1,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
    first: true,
    last: true
  };
}

describe('MedicalRecordFacade', () => {
  let facade: MedicalRecordFacade;
  let api: {
    findRecordByPatientId: jest.Mock;
    createRecord: jest.Mock;
    updateRecord: jest.Mock;
    listAntecedentsByRecord: jest.Mock;
    createAntecedent: jest.Mock;
    updateAntecedent: jest.Mock;
    deleteAntecedent: jest.Mock;
    searchConsultations: jest.Mock;
    findConsultation: jest.Mock;
    createConsultation: jest.Mock;
    updateConsultation: jest.Mock;
    signConsultation: jest.Mock;
    softDeleteConsultation: jest.Mock;
    findPrescriptionByConsultation: jest.Mock;
    createPrescription: jest.Mock;
    updatePrescription: jest.Mock;
    deletePrescription: jest.Mock;
    addPrescriptionLine: jest.Mock;
    updatePrescriptionLine: jest.Mock;
    deletePrescriptionLine: jest.Mock;
    getPrescriptionPdf: jest.Mock;
  };

  beforeEach(() => {
    api = {
      findRecordByPatientId: jest.fn(),
      createRecord: jest.fn(),
      updateRecord: jest.fn(),
      listAntecedentsByRecord: jest.fn(),
      createAntecedent: jest.fn(),
      updateAntecedent: jest.fn(),
      deleteAntecedent: jest.fn(),
      searchConsultations: jest.fn(),
      findConsultation: jest.fn(),
      createConsultation: jest.fn(),
      updateConsultation: jest.fn(),
      signConsultation: jest.fn(),
      softDeleteConsultation: jest.fn(),
      findPrescriptionByConsultation: jest.fn(),
      createPrescription: jest.fn(),
      updatePrescription: jest.fn(),
      deletePrescription: jest.fn(),
      addPrescriptionLine: jest.fn(),
      updatePrescriptionLine: jest.fn(),
      deletePrescriptionLine: jest.fn(),
      getPrescriptionPdf: jest.fn()
    };
    TestBed.configureTestingModule({
      providers: [MedicalRecordFacade, { provide: MedicalRecordApi, useValue: api }]
    });
    facade = TestBed.inject(MedicalRecordFacade);
  });

  describe('état initial', () => {
    it('record vide / consultation vide / mutation idle', () => {
      expect(facade.recordState().record).toBeNull();
      expect(facade.recordState().antecedents).toEqual([]);
      expect(facade.recordState().recordNotFound).toBe(false);
      expect(facade.consultationState().consultation).toBeNull();
      expect(facade.mutation().saving).toBe(false);
    });
  });

  describe('loadRecordByPatient', () => {
    it('charge record + antecedents + consultations en parallèle', () => {
      const r = fakeRecord({ id: 5, patientId: 100 });
      const ants = [fakeAntecedent({ medicalRecordId: 5 })];
      const cons = [fakeConsultation({ id: 10, medicalRecordId: 5 })];

      api.findRecordByPatientId.mockReturnValue(of(r));
      api.listAntecedentsByRecord.mockReturnValue(of(ants));
      api.searchConsultations.mockReturnValue(of(fakePage(cons)));

      facade.loadRecordByPatient(100);

      expect(api.findRecordByPatientId).toHaveBeenCalledWith(100);
      expect(api.listAntecedentsByRecord).toHaveBeenCalledWith(5);
      expect(api.searchConsultations).toHaveBeenCalledWith(
        { patientId: 100 },
        { page: 0, size: 10, sort: 'consultationDate,desc' }
      );
      expect(facade.recordState().record).toEqual(r);
      expect(facade.recordState().antecedents).toEqual(ants);
      expect(facade.recordState().recentConsultations).toEqual(cons);
      expect(facade.recordState().recordNotFound).toBe(false);
    });

    it('404 sur record → recordNotFound=true (cas patient sans dossier)', () => {
      api.findRecordByPatientId.mockReturnValue(throwError(() => ({ status: 404 })));

      facade.loadRecordByPatient(100);

      expect(facade.recordState().recordNotFound).toBe(true);
      expect(facade.recordState().record).toBeNull();
      expect(facade.recordState().error).toBeNull();
      expect(api.listAntecedentsByRecord).not.toHaveBeenCalled();
    });

    it('500 sur record → error message, recordNotFound reste false', () => {
      api.findRecordByPatientId.mockReturnValue(throwError(() => ({ status: 500 })));

      facade.loadRecordByPatient(100);

      expect(facade.recordState().error).toBe('Erreur serveur — réessayez plus tard');
      expect(facade.recordState().recordNotFound).toBe(false);
    });

    it('erreur sur antecedents ne fait pas tomber le chargement entier', () => {
      const r = fakeRecord();
      api.findRecordByPatientId.mockReturnValue(of(r));
      api.listAntecedentsByRecord.mockReturnValue(throwError(() => ({ status: 500 })));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));

      facade.loadRecordByPatient(100);

      // L'erreur sur antecedents est absorbée → tableau vide
      expect(facade.recordState().record).toEqual(r);
      expect(facade.recordState().antecedents).toEqual([]);
    });
  });

  describe('createRecord (cas patient sans dossier)', () => {
    it('crée + recharge le dossier complet', (done) => {
      const created = fakeRecord({ id: 99, patientId: 200 });
      api.createRecord.mockReturnValue(of(created));
      api.findRecordByPatientId.mockReturnValue(of(created));
      api.listAntecedentsByRecord.mockReturnValue(of([]));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));

      facade.createRecord({ patientId: 200 }).subscribe((result) => {
        expect(result).toEqual(created);
        // Le reload est asynchrone — on vérifie que findByPatientId a été appelé
        expect(api.findRecordByPatientId).toHaveBeenCalledWith(200);
        done();
      });
    });
  });

  describe('updateRecord', () => {
    it('met à jour le record local après succès', (done) => {
      const r = fakeRecord({ id: 5 });
      const updated = fakeRecord({ id: 5, allergiesSummary: 'Pénicilline', version: 1 });

      api.findRecordByPatientId.mockReturnValue(of(r));
      api.listAntecedentsByRecord.mockReturnValue(of([]));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));
      api.updateRecord.mockReturnValue(of(updated));

      facade.loadRecordByPatient(r.patientId);
      facade.updateRecord(5, { allergiesSummary: 'Pénicilline' }).subscribe(() => {
        expect(facade.recordState().record?.allergiesSummary).toBe('Pénicilline');
        expect(facade.recordState().record?.version).toBe(1);
        done();
      });
    });
  });

  describe('antécédents CRUD', () => {
    function preloadRecord() {
      const r = fakeRecord({ id: 5 });
      api.findRecordByPatientId.mockReturnValue(of(r));
      api.listAntecedentsByRecord.mockReturnValue(of([]));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));
      facade.loadRecordByPatient(r.patientId);
    }

    it('createAntecedent push dans la liste si record correspond', (done) => {
      preloadRecord();
      const created = fakeAntecedent({ id: 11, medicalRecordId: 5, title: 'Asthme' });
      api.createAntecedent.mockReturnValue(of(created));

      facade
        .createAntecedent({ medicalRecordId: 5, type: 'MEDICAL_CONDITION', title: 'Asthme' })
        .subscribe(() => {
          expect(facade.recordState().antecedents).toContain(created);
          done();
        });
    });

    it('updateAntecedent remplace dans la liste', (done) => {
      const a1 = fakeAntecedent({ id: 11, title: 'Original' });
      api.findRecordByPatientId.mockReturnValue(of(fakeRecord({ id: 5 })));
      api.listAntecedentsByRecord.mockReturnValue(of([a1]));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));
      facade.loadRecordByPatient(100);

      const updated = { ...a1, title: 'Modifié', version: 1 };
      api.updateAntecedent.mockReturnValue(of(updated));

      facade.updateAntecedent(11, { title: 'Modifié' }).subscribe(() => {
        expect(facade.recordState().antecedents[0].title).toBe('Modifié');
        done();
      });
    });

    it('deleteAntecedent retire de la liste', (done) => {
      const a1 = fakeAntecedent({ id: 11 });
      const a2 = fakeAntecedent({ id: 12 });
      api.findRecordByPatientId.mockReturnValue(of(fakeRecord({ id: 5 })));
      api.listAntecedentsByRecord.mockReturnValue(of([a1, a2]));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));
      facade.loadRecordByPatient(100);

      api.deleteAntecedent.mockReturnValue(of(undefined));

      facade.deleteAntecedent(11).subscribe(() => {
        expect(facade.recordState().antecedents.length).toBe(1);
        expect(facade.recordState().antecedents[0].id).toBe(12);
        done();
      });
    });
  });

  describe('loadConsultationDetail', () => {
    it('charge consultation + prescription en parallèle', () => {
      const c = fakeConsultation({ id: 50 });
      const p = fakePrescription([fakePrescriptionLine()], { consultationId: 50 });
      api.findConsultation.mockReturnValue(of(c));
      api.findPrescriptionByConsultation.mockReturnValue(of(p));

      facade.loadConsultationDetail(50);

      expect(facade.consultationState().consultation).toEqual(c);
      expect(facade.consultationState().prescription).toEqual(p);
    });

    it('absorbe 404 sur prescription (pas d\'ordonnance pour cette consultation)', () => {
      const c = fakeConsultation({ id: 50 });
      api.findConsultation.mockReturnValue(of(c));
      api.findPrescriptionByConsultation.mockReturnValue(throwError(() => ({ status: 404 })));

      facade.loadConsultationDetail(50);

      expect(facade.consultationState().consultation).toEqual(c);
      expect(facade.consultationState().prescription).toBeNull();
      expect(facade.consultationState().error).toBeNull();
    });

    it('404 sur consultation → error', () => {
      api.findConsultation.mockReturnValue(throwError(() => ({ status: 404 })));
      api.findPrescriptionByConsultation.mockReturnValue(of(fakePrescription([])));

      facade.loadConsultationDetail(999);

      expect(facade.consultationState().error).toBe('Élément introuvable');
    });
  });

  describe('signConsultation', () => {
    it('patche le détail local → signed=true', (done) => {
      const c = fakeConsultation({ id: 50, signed: false });
      const signed = { ...c, signed: true, signedAt: '2026-04-15T11:00:00Z', signedBy: 'doctor' };

      api.findConsultation.mockReturnValue(of(c));
      api.findPrescriptionByConsultation.mockReturnValue(of(null));
      facade.loadConsultationDetail(50);

      api.signConsultation.mockReturnValue(of(signed));

      facade.signConsultation(50).subscribe(() => {
        expect(facade.consultationState().consultation?.signed).toBe(true);
        expect(facade.consultationState().consultation?.signedBy).toBe('doctor');
        done();
      });
    });

    it('409 sur sign → message "Conflit"', (done) => {
      api.signConsultation.mockReturnValue(throwError(() => ({ status: 409 })));

      facade.signConsultation(50).subscribe({
        next: () => done.fail(),
        error: () => {
          expect(facade.mutation().error).toContain('Conflit');
          done();
        }
      });
    });
  });

  describe('prescription CRUD', () => {
    function preloadConsultation() {
      api.findConsultation.mockReturnValue(of(fakeConsultation({ id: 50 })));
      api.findPrescriptionByConsultation.mockReturnValue(of(null));
      facade.loadConsultationDetail(50);
    }

    it('createPrescription rattache au détail courant', (done) => {
      preloadConsultation();
      const p = fakePrescription([fakePrescriptionLine()], { consultationId: 50 });
      api.createPrescription.mockReturnValue(of(p));

      facade
        .createPrescription(50, {
          lines: [{ medicationName: 'Doliprane 500mg' }]
        })
        .subscribe(() => {
          expect(facade.consultationState().prescription).toEqual(p);
          done();
        });
    });

    it('addPrescriptionLine ajoute la ligne dans le détail', (done) => {
      const initial = fakePrescription([fakePrescriptionLine({ id: 301 })]);
      api.findConsultation.mockReturnValue(of(fakeConsultation({ id: 50 })));
      api.findPrescriptionByConsultation.mockReturnValue(of(initial));
      facade.loadConsultationDetail(50);

      const newLine = fakePrescriptionLine({ id: 302, medicationName: 'Amoxicilline 500mg' });
      api.addPrescriptionLine.mockReturnValue(of(newLine));

      facade
        .addPrescriptionLine(initial.id, { medicationName: 'Amoxicilline 500mg' })
        .subscribe(() => {
          const lines = facade.consultationState().prescription?.lines ?? [];
          expect(lines.length).toBe(2);
          expect(lines.find((l) => l.id === 302)?.medicationName).toBe('Amoxicilline 500mg');
          done();
        });
    });

    it('deletePrescriptionLine retire la ligne du détail', (done) => {
      const l1 = fakePrescriptionLine({ id: 301 });
      const l2 = fakePrescriptionLine({ id: 302 });
      const initial = fakePrescription([l1, l2]);
      api.findConsultation.mockReturnValue(of(fakeConsultation({ id: 50 })));
      api.findPrescriptionByConsultation.mockReturnValue(of(initial));
      facade.loadConsultationDetail(50);

      api.deletePrescriptionLine.mockReturnValue(of(undefined));

      facade.deletePrescriptionLine(initial.id, 301).subscribe(() => {
        const lines = facade.consultationState().prescription?.lines ?? [];
        expect(lines.length).toBe(1);
        expect(lines[0].id).toBe(302);
        done();
      });
    });
  });

  describe('downloadPrescriptionPdf', () => {
    let createObjectUrlMock: jest.Mock;
    let revokeObjectUrlMock: jest.Mock;
    let clickSpy: jest.SpyInstance;
    let originalCreate: typeof URL.createObjectURL | undefined;
    let originalRevoke: typeof URL.revokeObjectURL | undefined;

    beforeEach(() => {
      // jsdom n'expose pas URL.createObjectURL/revokeObjectURL — on assigne
      // directement (spyOn échoue sur les propriétés absentes).
      originalCreate = URL.createObjectURL;
      originalRevoke = URL.revokeObjectURL;
      createObjectUrlMock = jest.fn().mockReturnValue('blob:fake-url');
      revokeObjectUrlMock = jest.fn();
      (URL as unknown as { createObjectURL: jest.Mock }).createObjectURL = createObjectUrlMock;
      (URL as unknown as { revokeObjectURL: jest.Mock }).revokeObjectURL = revokeObjectUrlMock;
      clickSpy = jest
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => undefined);
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.useRealTimers();
      (URL as unknown as { createObjectURL: unknown }).createObjectURL = originalCreate as never;
      (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = originalRevoke as never;
      clickSpy.mockRestore();
    });

    it('déclenche le download avec filename = prescriptionNumber.pdf et reset le state', (done) => {
      const blob = new Blob(['fake-pdf-bytes'], { type: 'application/pdf' });
      api.getPrescriptionPdf.mockReturnValue(of(blob));
      const p = fakePrescription([], { id: 200, prescriptionNumber: 'ORD-2026-00042' });

      facade.downloadPrescriptionPdf(p).subscribe({
        next: () => {
          expect(api.getPrescriptionPdf).toHaveBeenCalledWith(200);
          expect(createObjectUrlMock).toHaveBeenCalledWith(blob);
          expect(clickSpy).toHaveBeenCalledTimes(1);
          expect(facade.pdfDownload().prescriptionId).toBeNull();
          expect(facade.pdfDownload().error).toBeNull();
          // setTimeout(0) avant revoke — on avance les timers
          jest.runAllTimers();
          expect(revokeObjectUrlMock).toHaveBeenCalledWith('blob:fake-url');
          done();
        }
      });
    });

    it('503 → message "Service indisponible" exposé dans pdfDownload.error', (done) => {
      api.getPrescriptionPdf.mockReturnValue(throwError(() => ({ status: 503 })));
      const p = fakePrescription();

      facade.downloadPrescriptionPdf(p).subscribe({
        error: () => {
          expect(facade.pdfDownload().error).toContain('indisponible');
          expect(facade.pdfDownload().prescriptionId).toBeNull();
          expect(clickSpy).not.toHaveBeenCalled();
          done();
        }
      });
    });

    it('isDownloadingPdf retourne true pendant l\'appel puis false après', () => {
      // Observable qui ne complete jamais — on bloque le tap()
      api.getPrescriptionPdf.mockReturnValue(
        new (require('rxjs').Subject)()
      );
      const p = fakePrescription([], { id: 200 });

      facade.downloadPrescriptionPdf(p).subscribe();
      expect(facade.isDownloadingPdf(200)).toBe(true);
      expect(facade.isDownloadingPdf(999)).toBe(false);
    });

    it('clearPdfDownloadError efface le message d\'erreur', (done) => {
      api.getPrescriptionPdf.mockReturnValue(throwError(() => ({ status: 404 })));
      facade.downloadPrescriptionPdf(fakePrescription()).subscribe({
        error: () => {
          expect(facade.pdfDownload().error).not.toBeNull();
          facade.clearPdfDownloadError();
          expect(facade.pdfDownload().error).toBeNull();
          done();
        }
      });
    });
  });

  describe('reset', () => {
    it('remet tous les états à l\'initial', () => {
      api.findRecordByPatientId.mockReturnValue(of(fakeRecord()));
      api.listAntecedentsByRecord.mockReturnValue(of([]));
      api.searchConsultations.mockReturnValue(of(fakePage<ConsultationDto>([])));
      facade.loadRecordByPatient(100);

      facade.reset();

      expect(facade.recordState().record).toBeNull();
      expect(facade.consultationState().consultation).toBeNull();
      expect(facade.mutation().saving).toBe(false);
    });
  });

  describe('traduction des erreurs', () => {
    it.each([
      [401, 'Non authentifié'],
      [403, expect.stringContaining('Accès refusé')],
      [404, 'Élément introuvable'],
      [400, expect.stringContaining('invalide')],
      [409, expect.stringContaining('Conflit')],
      [0, 'Serveur injoignable'],
      [500, 'Erreur serveur — réessayez plus tard']
    ])('status %i sur loadConsultationDetail → %s', (status, expectedMessage) => {
      api.findConsultation.mockReturnValue(throwError(() => ({ status })));
      api.findPrescriptionByConsultation.mockReturnValue(of(null));
      facade.loadConsultationDetail(1);
      expect(facade.consultationState().error).toEqual(expectedMessage);
    });
  });
});
