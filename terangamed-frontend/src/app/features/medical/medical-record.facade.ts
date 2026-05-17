import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, forkJoin, map, of, tap, throwError } from 'rxjs';
import { MedicalRecordApi } from '@api/medical-record.api';
import {
  AntecedentDto,
  AntecedentType,
  ConsultationDto,
  ConsultationSearchCriteria,
  CreateAntecedentRequest,
  CreateConsultationRequest,
  CreateMedicalRecordRequest,
  CreatePrescriptionLineRequest,
  CreatePrescriptionRequest,
  MedicalRecordDto,
  PrescriptionDto,
  PrescriptionLineDto,
  UpdateAntecedentRequest,
  UpdateConsultationRequest,
  UpdateMedicalRecordRequest,
  UpdatePrescriptionLineRequest,
  UpdatePrescriptionRequest
} from '@api/models/medical-record.model';
import { PageRequest } from '@api/common.types';

/**
 * État du dossier médical d'un patient (vue agrégée).
 *
 * <p>Composé du {@link MedicalRecordDto} + sa liste d'antécédents et
 * éventuellement les consultations récentes. Tout chargé en parallèle via
 * {@link MedicalRecordFacade#loadRecordByPatient}.
 */
export interface MedicalRecordState {
  readonly loading: boolean;
  readonly record: MedicalRecordDto | null;
  readonly antecedents: ReadonlyArray<AntecedentDto>;
  readonly recentConsultations: ReadonlyArray<ConsultationDto>;
  readonly error: string | null;
  /**
   * True si le patient n'a pas encore de dossier (404 sur findRecordByPatient).
   * Cas valide pour un patient nouveau — l'UI propose alors de le créer.
   */
  readonly recordNotFound: boolean;
}

/**
 * État de la consultation sélectionnée + sa prescription si elle existe.
 */
export interface ConsultationDetailState {
  readonly loading: boolean;
  readonly consultation: ConsultationDto | null;
  readonly prescription: PrescriptionDto | null;
  readonly error: string | null;
}

/**
 * État d'une mutation en cours (create / update / sign / delete).
 */
export interface MedicalMutationState {
  readonly saving: boolean;
  readonly error: string | null;
}

/**
 * État du téléchargement PDF d'une ordonnance.
 *
 * <p>Stocke l'id de l'ordonnance en cours pour permettre à l'UI d'afficher un
 * spinner localisé (on ne bloque pas toute la page — plusieurs prescriptions
 * peuvent coexister dans le dossier patient).
 */
export interface PrescriptionPdfDownloadState {
  readonly prescriptionId: number | null;
  readonly error: string | null;
}

const INITIAL_RECORD: MedicalRecordState = {
  loading: false,
  record: null,
  antecedents: [],
  recentConsultations: [],
  error: null,
  recordNotFound: false
};

const INITIAL_CONSULTATION: ConsultationDetailState = {
  loading: false,
  consultation: null,
  prescription: null,
  error: null
};

const INITIAL_MUTATION: MedicalMutationState = {
  saving: false,
  error: null
};

const INITIAL_PDF_DOWNLOAD: PrescriptionPdfDownloadState = {
  prescriptionId: null,
  error: null
};

const RECENT_CONSULTATIONS_LIMIT = 10;

/**
 * Façade Medical — orchestre le CRUD sur les 4 ressources liées
 * (MedicalRecord, Antécédents, Consultations, Prescriptions).
 *
 * <h3>Pattern</h3>
 * 3 signals indépendants :
 * <ul>
 *   <li>{@link recordState} — dossier patient agrégé (record + antecedents + consultations récentes)</li>
 *   <li>{@link consultationState} — consultation sélectionnée + prescription</li>
 *   <li>{@link mutation} — état save/erreur des mutations</li>
 * </ul>
 *
 * <h3>Cas patient sans dossier</h3>
 * {@link loadRecordByPatient} traite 404 sur le record comme un cas valide
 * ({@code recordNotFound=true}) pour que l'UI puisse proposer la création.
 * Les antécédents et consultations restent [] dans ce cas.
 */
@Injectable({ providedIn: 'root' })
export class MedicalRecordFacade {
  private readonly api = inject(MedicalRecordApi);

  // ─── Signals ───
  private readonly _recordState = signal<MedicalRecordState>(INITIAL_RECORD);
  private readonly _consultationState = signal<ConsultationDetailState>(INITIAL_CONSULTATION);
  private readonly _mutation = signal<MedicalMutationState>(INITIAL_MUTATION);
  private readonly _pdfDownload = signal<PrescriptionPdfDownloadState>(INITIAL_PDF_DOWNLOAD);

  readonly recordState = this._recordState.asReadonly();
  readonly consultationState = this._consultationState.asReadonly();
  readonly mutation = this._mutation.asReadonly();
  readonly pdfDownload = this._pdfDownload.asReadonly();

  /** True si une mutation est en cours (utile pour désactiver les boutons). */
  readonly mutating = computed(() => this._mutation().saving);

  /** True si un PDF est en cours de téléchargement pour cet id. */
  isDownloadingPdf(prescriptionId: number): boolean {
    return this._pdfDownload().prescriptionId === prescriptionId;
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Dossier médical (agrégé)
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Charge le dossier complet d'un patient :
   * MedicalRecord + antécédents + consultations récentes (10 dernières).
   *
   * <p>404 sur le record est traité comme "pas encore de dossier" — l'UI
   * affiche alors un message + bouton "Créer le dossier médical".
   */
  loadRecordByPatient(patientId: number): void {
    this._recordState.update((s) => ({ ...s, loading: true, error: null, recordNotFound: false }));

    this.api
      .findRecordByPatientId(patientId)
      .pipe(
        // 404 = patient sans dossier → on ne propage pas l'erreur, on marque
        // recordNotFound et on retourne null
        catchError((err) => {
          if (err && typeof err === 'object' && 'status' in err && err.status === 404) {
            this._recordState.set({
              loading: false,
              record: null,
              antecedents: [],
              recentConsultations: [],
              error: null,
              recordNotFound: true
            });
            return of<MedicalRecordDto | null>(null);
          }
          this._recordState.set({
            ...INITIAL_RECORD,
            error: this.translateError(err, 'detail')
          });
          return throwError(() => err);
        })
      )
      .subscribe({
        next: (record) => {
          if (!record) return; // 404 déjà géré

          // Record OK — charger antécédents + consultations en parallèle
          forkJoin({
            antecedents: this.api
              .listAntecedentsByRecord(record.id)
              .pipe(catchError(() => of<AntecedentDto[]>([]))),
            consultations: this.api
              .searchConsultations(
                { patientId },
                { page: 0, size: RECENT_CONSULTATIONS_LIMIT, sort: 'consultationDate,desc' }
              )
              .pipe(
                map((page) => page.content),
                catchError(() => of<readonly ConsultationDto[]>([]))
              )
          }).subscribe({
            next: ({ antecedents, consultations }) => {
              this._recordState.set({
                loading: false,
                record,
                antecedents,
                recentConsultations: consultations,
                error: null,
                recordNotFound: false
              });
            },
            error: (err) => {
              // En cas d'erreur sur antecedents/consultations, on garde au moins le record
              this._recordState.set({
                loading: false,
                record,
                antecedents: [],
                recentConsultations: [],
                error: this.translateError(err, 'detail'),
                recordNotFound: false
              });
            }
          });
        },
        error: () => {
          /* Déjà géré dans catchError ci-dessus */
        }
      });
  }

  /** Vide l'état dossier (à appeler dans ngOnDestroy de la page). */
  clearRecord(): void {
    this._recordState.set(INITIAL_RECORD);
  }

  /**
   * Crée le MedicalRecord d'un patient (cas où {@code recordNotFound=true}).
   * Recharge ensuite le dossier complet pour avoir l'état cohérent.
   */
  createRecord(request: CreateMedicalRecordRequest): Observable<MedicalRecordDto> {
    return this.runMutation(this.api.createRecord(request)).pipe(
      tap((created) => this.loadRecordByPatient(created.patientId))
    );
  }

  /** Met à jour les infos médicales générales (groupe sanguin, allergies, notes). */
  updateRecord(
    id: number,
    request: UpdateMedicalRecordRequest
  ): Observable<MedicalRecordDto> {
    return this.runMutation(this.api.updateRecord(id, request)).pipe(
      tap((updated) => {
        const current = this._recordState();
        if (current.record?.id === id) {
          this._recordState.set({ ...current, record: updated });
        }
      })
    );
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Antécédents
  // ═════════════════════════════════════════════════════════════════════════

  createAntecedent(request: CreateAntecedentRequest): Observable<AntecedentDto> {
    return this.runMutation(this.api.createAntecedent(request)).pipe(
      tap((created) => {
        const current = this._recordState();
        if (current.record?.id === request.medicalRecordId) {
          this._recordState.set({
            ...current,
            antecedents: [...current.antecedents, created]
          });
        }
      })
    );
  }

  updateAntecedent(
    id: number,
    request: UpdateAntecedentRequest
  ): Observable<AntecedentDto> {
    return this.runMutation(this.api.updateAntecedent(id, request)).pipe(
      tap((updated) => {
        const current = this._recordState();
        const idx = current.antecedents.findIndex((a) => a.id === id);
        if (idx >= 0) {
          const next = [...current.antecedents];
          next[idx] = updated;
          this._recordState.set({ ...current, antecedents: next });
        }
      })
    );
  }

  deleteAntecedent(id: number): Observable<void> {
    return this.runMutation(this.api.deleteAntecedent(id)).pipe(
      tap(() => {
        const current = this._recordState();
        this._recordState.set({
          ...current,
          antecedents: current.antecedents.filter((a) => a.id !== id)
        });
      })
    );
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Consultations
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Charge le détail d'une consultation + sa prescription si elle existe.
   * 404 sur la prescription est normal (consultation sans ordonnance) et
   * absorbé silencieusement.
   */
  loadConsultationDetail(id: number): void {
    this._consultationState.update((s) => ({ ...s, loading: true, error: null }));

    forkJoin({
      consultation: this.api.findConsultation(id),
      prescription: this.api
        .findPrescriptionByConsultation(id)
        .pipe(catchError(() => of<PrescriptionDto | null>(null)))
    })
      .pipe(
        catchError((err) => {
          this._consultationState.set({
            loading: false,
            consultation: null,
            prescription: null,
            error: this.translateError(err, 'detail')
          });
          return throwError(() => err);
        }),
        finalize(() => {
          this._consultationState.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe({
        next: ({ consultation, prescription }) => {
          this._consultationState.set({
            loading: false,
            consultation,
            prescription,
            error: null
          });
        },
        error: () => {
          /* déjà géré */
        }
      });
  }

  clearConsultation(): void {
    this._consultationState.set(INITIAL_CONSULTATION);
  }

  /**
   * Crée une consultation. Le médecin auteur est résolu côté backend depuis
   * le claim {@code sub} du JWT — pas besoin de passer un doctorId.
   */
  createConsultation(request: CreateConsultationRequest): Observable<ConsultationDto> {
    return this.runMutation(this.api.createConsultation(request));
  }

  updateConsultation(
    id: number,
    request: UpdateConsultationRequest
  ): Observable<ConsultationDto> {
    return this.runMutation(this.api.updateConsultation(id, request)).pipe(
      tap((updated) => this.patchConsultationIfMatching(id, updated))
    );
  }

  /**
   * Signe une consultation → passage en {@code signed=true}, immutable.
   * Met à jour le détail courant si on observe la même consultation.
   */
  signConsultation(id: number): Observable<ConsultationDto> {
    return this.runMutation(this.api.signConsultation(id)).pipe(
      tap((signed) => this.patchConsultationIfMatching(id, signed))
    );
  }

  /** Soft-delete d'une consultation (admin uniquement côté backend). */
  softDeleteConsultation(id: number): Observable<void> {
    return this.runMutation(this.api.softDeleteConsultation(id)).pipe(
      tap(() => {
        const current = this._consultationState();
        if (current.consultation?.id === id) {
          this._consultationState.set(INITIAL_CONSULTATION);
        }
      })
    );
  }

  /** Helper de search (utilisé par la liste consultations). */
  searchConsultations(
    criteria: ConsultationSearchCriteria = {},
    page: PageRequest = { page: 0, size: 20, sort: 'consultationDate,desc' }
  ): Observable<readonly ConsultationDto[]> {
    return this.api.searchConsultations(criteria, page).pipe(map((p) => p.content));
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Prescriptions
  // ═════════════════════════════════════════════════════════════════════════

  createPrescription(
    consultationId: number,
    request: CreatePrescriptionRequest
  ): Observable<PrescriptionDto> {
    return this.runMutation(this.api.createPrescription(consultationId, request)).pipe(
      tap((created) => {
        const current = this._consultationState();
        if (current.consultation?.id === consultationId) {
          this._consultationState.set({ ...current, prescription: created });
        }
      })
    );
  }

  updatePrescription(
    id: number,
    request: UpdatePrescriptionRequest
  ): Observable<PrescriptionDto> {
    return this.runMutation(this.api.updatePrescription(id, request)).pipe(
      tap((updated) => {
        const current = this._consultationState();
        if (current.prescription?.id === id) {
          this._consultationState.set({ ...current, prescription: updated });
        }
      })
    );
  }

  deletePrescription(id: number): Observable<void> {
    return this.runMutation(this.api.deletePrescription(id)).pipe(
      tap(() => {
        const current = this._consultationState();
        if (current.prescription?.id === id) {
          this._consultationState.set({ ...current, prescription: null });
        }
      })
    );
  }

  /**
   * Télécharge le PDF d'une ordonnance — déclenche un download navigateur
   * (filename = `{prescriptionNumber}.pdf`, cohérent avec le Content-Disposition
   * renvoyé par le backend).
   *
   * <p>Met à jour {@link pdfDownload} pour permettre à l'UI d'afficher un
   * spinner localisé via {@link isDownloadingPdf}. Les erreurs HTTP sont
   * traduites en messages user-friendly (404/403/503/timeout) puis exposées
   * dans le même signal — le composant les affiche dans la card prescription.
   *
   * <p>Le PDF est récupéré en {@code Blob}, puis on déclenche le download via
   * un Object URL temporaire (révoqué après le click). Cette approche évite
   * d'ouvrir un nouvel onglet (UX préférée) et fonctionne offline si le service
   * worker met le blob en cache (V2).
   */
  downloadPrescriptionPdf(prescription: PrescriptionDto): Observable<Blob> {
    this._pdfDownload.set({ prescriptionId: prescription.id, error: null });
    return this.api.getPrescriptionPdf(prescription.id).pipe(
      tap((blob) => {
        triggerBlobDownload(blob, `${prescription.prescriptionNumber}.pdf`);
        this._pdfDownload.set(INITIAL_PDF_DOWNLOAD);
      }),
      catchError((err) => {
        this._pdfDownload.set({
          prescriptionId: null,
          error: this.translatePdfError(err)
        });
        return throwError(() => err);
      })
    );
  }

  /** Efface l'erreur de download PDF (utilisé après affichage). */
  clearPdfDownloadError(): void {
    this._pdfDownload.update((s) => ({ ...s, error: null }));
  }

  addPrescriptionLine(
    prescriptionId: number,
    request: CreatePrescriptionLineRequest
  ): Observable<PrescriptionLineDto> {
    return this.runMutation(this.api.addPrescriptionLine(prescriptionId, request)).pipe(
      tap((line) => {
        const current = this._consultationState();
        if (current.prescription?.id === prescriptionId) {
          this._consultationState.set({
            ...current,
            prescription: {
              ...current.prescription,
              lines: [...current.prescription.lines, line]
            }
          });
        }
      })
    );
  }

  updatePrescriptionLine(
    prescriptionId: number,
    lineId: number,
    request: UpdatePrescriptionLineRequest
  ): Observable<PrescriptionLineDto> {
    return this.runMutation(
      this.api.updatePrescriptionLine(prescriptionId, lineId, request)
    ).pipe(
      tap((updated) => {
        const current = this._consultationState();
        if (current.prescription?.id === prescriptionId) {
          const lines = current.prescription.lines.map((l) =>
            l.id === lineId ? updated : l
          );
          this._consultationState.set({
            ...current,
            prescription: { ...current.prescription, lines }
          });
        }
      })
    );
  }

  deletePrescriptionLine(prescriptionId: number, lineId: number): Observable<void> {
    return this.runMutation(this.api.deletePrescriptionLine(prescriptionId, lineId)).pipe(
      tap(() => {
        const current = this._consultationState();
        if (current.prescription?.id === prescriptionId) {
          this._consultationState.set({
            ...current,
            prescription: {
              ...current.prescription,
              lines: current.prescription.lines.filter((l) => l.id !== lineId)
            }
          });
        }
      })
    );
  }

  // ─── Reset complet ───
  reset(): void {
    this._recordState.set(INITIAL_RECORD);
    this._consultationState.set(INITIAL_CONSULTATION);
    this._mutation.set(INITIAL_MUTATION);
    this._pdfDownload.set(INITIAL_PDF_DOWNLOAD);
  }

  // ═════════════════════════════════════════════════════════════════════════
  //   Helpers privés
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Wrapper de mutation : passe l'état en saving=true, intercepte les erreurs
   * pour les exposer dans le signal {@link mutation}, et reset à idle après
   * le succès. L'erreur est aussi propagée via {@code throwError} pour que
   * le composant puisse réagir (navigation, message, etc.).
   */
  private runMutation<T>(call$: Observable<T>): Observable<T> {
    this._mutation.set({ saving: true, error: null });
    return call$.pipe(
      tap(() => this._mutation.set({ saving: false, error: null })),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /** Met à jour le détail consultation si on observe la même id. */
  private patchConsultationIfMatching(id: number, updated: ConsultationDto): void {
    const current = this._consultationState();
    if (current.consultation?.id === id) {
      this._consultationState.set({ ...current, consultation: updated });
    }
  }

  /**
   * Traduction spécifique pour les erreurs de téléchargement PDF.
   * Distingue les cas 503 (storage MinIO down) et 403 (PATIENT non concerné).
   */
  private translatePdfError(err: unknown): string {
    if (err && typeof err === 'object' && 'status' in err) {
      const status = (err as { status: number }).status;
      if (status === 401) return 'Non authentifié — reconnectez-vous';
      if (status === 403) return 'Accès refusé — ordonnance non accessible';
      if (status === 404) return 'Ordonnance introuvable';
      if (status === 503) return 'Service indisponible (storage ou résolution patient/médecin) — réessayez';
      if (status === 0) return 'Serveur injoignable';
      if (status >= 500) return 'Erreur serveur lors de la génération du PDF';
      return `Erreur HTTP ${status} lors du téléchargement`;
    }
    return 'Téléchargement impossible';
  }

  /**
   * Traduction des erreurs HTTP avec contexte. Le 409 a un sens spécifique
   * pour le module medical : tentative de signer/modifier une consultation
   * déjà signée.
   */
  private translateError(
    err: unknown,
    context: 'list' | 'detail' | 'mutation' = 'detail'
  ): string {
    if (err && typeof err === 'object' && 'status' in err) {
      const status = (err as { status: number }).status;
      if (status === 401) return 'Non authentifié';
      if (status === 403) return 'Accès refusé — opération réservée aux médecins';
      if (status === 404) {
        return context === 'list'
          ? 'Service dossier médical indisponible — vérifiez la connexion au backend'
          : 'Élément introuvable';
      }
      if (status === 409) {
        return context === 'mutation'
          ? 'Conflit — la consultation est déjà signée ou les données ont été modifiées entre-temps'
          : 'Conflit de données';
      }
      if (status === 400) {
        return 'Requête invalide — vérifiez les champs obligatoires';
      }
      if (status === 0) return 'Serveur injoignable';
      if (status >= 500) return 'Erreur serveur — réessayez plus tard';
      return `Erreur HTTP ${status}`;
    }
    return err instanceof Error ? err.message : 'Erreur inconnue';
  }
}

/**
 * Déclenche un download navigateur à partir d'un Blob :
 * crée un Object URL temporaire, simule un click sur un `<a download>`, puis
 * révoque l'URL pour libérer la mémoire (sinon le Blob reste référencé jusqu'au
 * unload de la page — fuite mémoire sur les sessions longues).
 *
 * <p>Le {@code setTimeout(0)} avant {@code revokeObjectURL} laisse le navigateur
 * terminer le téléchargement avant la révocation — sans ce délai, Safari et
 * Firefox peuvent annuler le download sur des fichiers > quelques Mo.
 */
function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
