import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, finalize, map, of } from 'rxjs';
import { addDays, startOfWeek } from 'date-fns';
import { AppointmentApi } from '@api/appointment.api';
import { MedicalRecordApi } from '@api/medical-record.api';
import { AppointmentDto, AppointmentSearchCriteria } from '@api/models/appointment.model';
import { ConsultationSearchCriteria } from '@api/models/medical-record.model';
import { toLocalDateString, todayLocal } from '@shared/utils/date.utils';

/**
 * État d'un KPI individuel — affiche une valeur, un loading ou une erreur.
 *
 * <p>Pattern "1 KPI = 1 signal" plutôt qu'un état global agrégé : si une API
 * lente ou en erreur ne doit pas bloquer/casser les autres cards.
 */
export interface KpiState {
  readonly loading: boolean;
  readonly value: number | null;
  readonly error: string | null;
}

/** État du planning hebdomadaire — liste de RDV. */
export interface WeekPlanningState {
  readonly loading: boolean;
  readonly events: ReadonlyArray<AppointmentDto>;
  readonly error: string | null;
}

/**
 * État du KPI "Mon prochain RDV" (vue DOCTOR).
 *
 * <p>Contient un AppointmentDto plutôt qu'un nombre — la card affichera une
 * date/heure formatée + nom du patient.
 */
export interface NextAppointmentState {
  readonly loading: boolean;
  readonly appointment: AppointmentDto | null;
  readonly error: string | null;
}

/**
 * Mode du dashboard — détermine quels KPI sont visibles et comment ils sont
 * filtrés. Dérivé du rôle Keycloak de l'utilisateur connecté.
 */
export type DashboardMode = 'ADMIN' | 'DOCTOR' | 'RECEPTIONIST';

/**
 * Scope appliqué aux requêtes du dashboard.
 *
 * <p><b>doctorIdFilter</b> est non-null UNIQUEMENT en mode DOCTOR (pour
 * filtrer les RDV/consultations sur le médecin connecté). En mode ADMIN ou
 * RECEPTIONIST, il vaut null = vue globale.
 */
export interface DashboardScope {
  readonly mode: DashboardMode;
  readonly doctorIdFilter: number | null;
}

const INITIAL_KPI: KpiState = { loading: false, value: null, error: null };
const INITIAL_WEEK: WeekPlanningState = { loading: false, events: [], error: null };
const INITIAL_NEXT: NextAppointmentState = { loading: false, appointment: null, error: null };

/** Scope par défaut — vue globale ADMIN-like, rétro-compatible. */
const DEFAULT_SCOPE: DashboardScope = { mode: 'ADMIN', doctorIdFilter: null };

/**
 * Façade du dashboard — orchestre les appels API parallèles et expose un signal
 * par KPI métier. Les composants de la page consomment directement les signals.
 *
 * <h3>Adaptation par rôle</h3>
 * {@link refresh} accepte un {@link DashboardScope} qui :
 * <ul>
 *   <li>Filtre les KPI par {@code doctorId} si mode DOCTOR</li>
 *   <li>Charge {@code loadNextAppointment} en plus si mode DOCTOR</li>
 *   <li>Charge {@code loadPlannedAppointments} en plus si mode RECEPTIONIST</li>
 * </ul>
 * Sans argument, la facade tombe sur {@link DEFAULT_SCOPE} = ADMIN/global —
 * comportement rétro-compatible avec l'usage initial.
 */
@Injectable({ providedIn: 'root' })
export class DashboardFacade {
  private readonly appointmentApi = inject(AppointmentApi);
  private readonly medicalRecordApi = inject(MedicalRecordApi);

  // ─── Signals KPI dynamiques ───
  private readonly _appointmentsToday = signal<KpiState>(INITIAL_KPI);
  private readonly _patientsWaiting = signal<KpiState>(INITIAL_KPI);
  private readonly _consultationsPending = signal<KpiState>(INITIAL_KPI);

  /** Placeholder fixe — billing-service pas encore livré (4ᵉ KPI ADMIN). */
  private readonly _pendingInvoices = signal<KpiState>({
    loading: false,
    value: 0,
    error: null
  });

  /** RDV en attente de confirmation (status=PLANNED) — 4ᵉ KPI RECEPTIONIST. */
  private readonly _plannedAppointments = signal<KpiState>(INITIAL_KPI);

  /** Prochain RDV du médecin connecté — 4ᵉ KPI DOCTOR. */
  private readonly _nextAppointment = signal<NextAppointmentState>(INITIAL_NEXT);

  private readonly _weekPlanning = signal<WeekPlanningState>(INITIAL_WEEK);

  /**
   * ID du patient sélectionné — utilisé par la carte "Dossier patient" du
   * dashboard. Mis à jour quand l'utilisateur clique un RDV dans le planning.
   */
  private readonly _selectedPatientId = signal<number | null>(null);

  /** Scope courant — utilisé pour rejouer refresh sans repasser le param. */
  private currentScope: DashboardScope = DEFAULT_SCOPE;

  // ─── Exposition readonly ───
  readonly appointmentsToday = this._appointmentsToday.asReadonly();
  readonly patientsWaiting = this._patientsWaiting.asReadonly();
  readonly consultationsPending = this._consultationsPending.asReadonly();
  readonly pendingInvoices = this._pendingInvoices.asReadonly();
  readonly plannedAppointments = this._plannedAppointments.asReadonly();
  readonly nextAppointment = this._nextAppointment.asReadonly();
  readonly weekPlanning = this._weekPlanning.asReadonly();
  readonly selectedPatientId = this._selectedPatientId.asReadonly();

  /** True si au moins un KPI est en cours de chargement. */
  readonly anyLoading = computed(
    () =>
      this._appointmentsToday().loading ||
      this._patientsWaiting().loading ||
      this._consultationsPending().loading ||
      this._plannedAppointments().loading ||
      this._nextAppointment().loading
  );

  /**
   * Lance le chargement des KPI + de la semaine courante en parallèle, selon
   * le {@link DashboardScope} fourni. Idempotent.
   *
   * <p>Sans argument → comportement rétro-compatible (vue ADMIN globale).
   */
  refresh(scope: DashboardScope = DEFAULT_SCOPE): void {
    this.currentScope = scope;
    this.loadAppointmentsToday(scope);
    this.loadPatientsWaiting(scope);
    this.loadConsultationsPending(scope);
    this.loadWeek(new Date(), scope);

    // 4ᵉ KPI conditionnel selon le mode
    if (scope.mode === 'DOCTOR') {
      this.loadNextAppointment(scope);
    } else if (scope.mode === 'RECEPTIONIST') {
      this.loadPlannedAppointments();
    }
    // Mode ADMIN → pendingInvoices reste sur son placeholder fixe.
  }

  /** Sélectionne un patient pour la carte "Dossier patient". null = désélectionner. */
  selectPatient(patientId: number | null): void {
    this._selectedPatientId.set(patientId);
  }

  /**
   * Charge les RDV d'une semaine donnée (du lundi au dimanche). Filtré par
   * doctorId si scope DOCTOR. Appelée par le composant week-planning sur
   * navigation prev/next/today — réutilise le scope courant si non fourni.
   */
  loadWeek(anyDayInWeek: Date, scope: DashboardScope = this.currentScope): void {
    this._weekPlanning.update((s) => ({ ...s, loading: true, error: null }));
    // weekStartsOn: 1 = lundi (cohérent avec le composant calendrier)
    const monday = startOfWeek(anyDayInWeek, { weekStartsOn: 1 });
    const sunday = addDays(monday, 6);

    const criteria: AppointmentSearchCriteria = {
      fromDate: toLocalDateString(monday),
      toDate: toLocalDateString(sunday),
      ...this.doctorFilterFor(scope)
    };

    this.appointmentApi
      .search(criteria, { page: 0, size: 200 })
      .pipe(
        map((page) => page.content),
        catchError((err) => {
          this._weekPlanning.set({ loading: false, events: [], error: messageOf(err) });
          return of(null);
        }),
        finalize(() => {
          this._weekPlanning.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe((events) => {
        if (events !== null) {
          this._weekPlanning.set({ loading: false, events, error: null });
        }
      });
  }

  // ─── Loaders privés ───
  // Pattern : on ne charge qu'un total via size=1 pour minimiser le payload
  // et lire {@code totalElements} de la réponse Page<T>.

  private loadAppointmentsToday(scope: DashboardScope): void {
    this._appointmentsToday.update((s) => ({ ...s, loading: true, error: null }));
    const today = todayLocal();
    const criteria: AppointmentSearchCriteria = {
      fromDate: today,
      toDate: today,
      ...this.doctorFilterFor(scope)
    };

    this.appointmentApi
      .search(criteria, { page: 0, size: 1 })
      .pipe(
        map((page) => page.totalElements),
        catchError((err) => {
          this._appointmentsToday.set({ loading: false, value: null, error: messageOf(err) });
          return of(null);
        }),
        finalize(() => {
          this._appointmentsToday.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe((value) => {
        if (value !== null) {
          this._appointmentsToday.set({ loading: false, value, error: null });
        }
      });
  }

  private loadPatientsWaiting(scope: DashboardScope): void {
    this._patientsWaiting.update((s) => ({ ...s, loading: true, error: null }));
    const today = todayLocal();
    const criteria: AppointmentSearchCriteria = {
      status: 'CONFIRMED',
      fromDate: today,
      toDate: today,
      ...this.doctorFilterFor(scope)
    };

    this.appointmentApi
      .search(criteria, { page: 0, size: 1 })
      .pipe(
        map((page) => page.totalElements),
        catchError((err) => {
          this._patientsWaiting.set({ loading: false, value: null, error: messageOf(err) });
          return of(null);
        }),
        finalize(() => {
          this._patientsWaiting.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe((value) => {
        if (value !== null) {
          this._patientsWaiting.set({ loading: false, value, error: null });
        }
      });
  }

  private loadConsultationsPending(scope: DashboardScope): void {
    this._consultationsPending.update((s) => ({ ...s, loading: true, error: null }));
    const criteria: ConsultationSearchCriteria = {
      signed: false,
      ...(scope.mode === 'DOCTOR' && scope.doctorIdFilter !== null
        ? { doctorId: scope.doctorIdFilter }
        : {})
    };

    this.medicalRecordApi
      .searchConsultations(criteria, { page: 0, size: 1 })
      .pipe(
        map((page) => page.totalElements),
        catchError((err) => {
          this._consultationsPending.set({
            loading: false,
            value: null,
            error: messageOf(err)
          });
          return of(null);
        }),
        finalize(() => {
          this._consultationsPending.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe((value) => {
        if (value !== null) {
          this._consultationsPending.set({ loading: false, value, error: null });
        }
      });
  }

  /**
   * RECEPTIONIST — RDV à confirmer (status=PLANNED) à partir d'aujourd'hui.
   * Pas de filtrage par doctor (vue globale).
   */
  private loadPlannedAppointments(): void {
    this._plannedAppointments.update((s) => ({ ...s, loading: true, error: null }));
    const criteria: AppointmentSearchCriteria = {
      status: 'PLANNED',
      fromDate: todayLocal()
    };

    this.appointmentApi
      .search(criteria, { page: 0, size: 1 })
      .pipe(
        map((page) => page.totalElements),
        catchError((err) => {
          this._plannedAppointments.set({ loading: false, value: null, error: messageOf(err) });
          return of(null);
        }),
        finalize(() => {
          this._plannedAppointments.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe((value) => {
        if (value !== null) {
          this._plannedAppointments.set({ loading: false, value, error: null });
        }
      });
  }

  /**
   * DOCTOR — prochain RDV à venir (peu importe le statut, on prend le plus
   * proche dans le futur, hors COMPLETED/CANCELLED). Tri ascendant + size 1.
   *
   * <p>Implémenté en client-side : on récupère size=10 puis on filtre les
   * statuts à exclure et on garde le premier. Le backend ne supporte pas
   * encore "status NOT IN (...)" — à factoriser plus tard si besoin.
   */
  private loadNextAppointment(scope: DashboardScope): void {
    this._nextAppointment.update((s) => ({ ...s, loading: true, error: null }));
    if (scope.doctorIdFilter === null) {
      // Cas DOCTOR sans profil résolu — on n'a pas d'ID, on bypass.
      this._nextAppointment.set(INITIAL_NEXT);
      return;
    }

    const criteria: AppointmentSearchCriteria = {
      doctorId: scope.doctorIdFilter,
      fromDate: todayLocal()
    };

    this.appointmentApi
      .search(criteria, { page: 0, size: 10, sort: 'startTime,asc' })
      .pipe(
        map((page) =>
          page.content.find((a) => a.status !== 'COMPLETED' && a.status !== 'CANCELLED') ?? null
        ),
        catchError((err) => {
          this._nextAppointment.set({
            loading: false,
            appointment: null,
            error: messageOf(err)
          });
          return of(null);
        }),
        finalize(() => {
          this._nextAppointment.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe((appointment) => {
        if (appointment !== null || !this._nextAppointment().error) {
          this._nextAppointment.set({ loading: false, appointment, error: null });
        }
      });
  }

  /**
   * Construit le fragment {@code { doctorId }} si on est en mode DOCTOR avec
   * un ID résolu, sinon objet vide. Spread à fusionner dans un criteria.
   */
  private doctorFilterFor(scope: DashboardScope): { doctorId?: number } {
    return scope.mode === 'DOCTOR' && scope.doctorIdFilter !== null
      ? { doctorId: scope.doctorIdFilter }
      : {};
  }
}

/** Extrait un message lisible depuis une erreur HTTP. */
function messageOf(err: unknown): string {
  if (err && typeof err === 'object' && 'status' in err) {
    const e = err as { status: number; message?: string };
    if (e.status === 401) {
      return 'Non authentifié';
    }
    if (e.status === 403) {
      return 'Accès refusé';
    }
    if (e.status === 0) {
      return 'Serveur injoignable';
    }
    return e.message ?? `Erreur HTTP ${e.status}`;
  }
  return err instanceof Error ? err.message : 'Erreur inconnue';
}
