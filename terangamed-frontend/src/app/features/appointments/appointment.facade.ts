import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import { AppointmentApi } from '@api/appointment.api';
import {
  AppointmentDto,
  AppointmentSearchCriteria,
  AppointmentStatus,
  CreateAppointmentRequest,
  UpdateAppointmentRequest
} from '@api/models/appointment.model';
import { PageRequest } from '@api/common.types';

/**
 * État de la liste paginée des rendez-vous.
 */
export interface AppointmentListState {
  readonly loading: boolean;
  readonly appointments: ReadonlyArray<AppointmentDto>;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly page: number;
  readonly size: number;
  readonly error: string | null;
}

/**
 * État du rendez-vous sélectionné (page détail).
 */
export interface AppointmentDetailState {
  readonly loading: boolean;
  readonly appointment: AppointmentDto | null;
  readonly error: string | null;
}

/**
 * État d'une mutation en cours (create / update / transition d'état).
 */
export interface AppointmentMutationState {
  readonly saving: boolean;
  readonly error: string | null;
}

const INITIAL_LIST: AppointmentListState = {
  loading: false,
  appointments: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: 20,
  error: null
};

const INITIAL_DETAIL: AppointmentDetailState = {
  loading: false,
  appointment: null,
  error: null
};

const INITIAL_MUTATION: AppointmentMutationState = {
  saving: false,
  error: null
};

/** Pagination par défaut pour la liste — tri par date de début descendant. */
export const DEFAULT_APPOINTMENT_PAGE_REQUEST: PageRequest = {
  page: 0,
  size: 20,
  sort: 'startTime,desc'
};

/**
 * Façade Rendez-vous — orchestre le CRUD + les transitions d'état au-dessus
 * de {@link AppointmentApi}.
 *
 * <h3>Pattern signals</h3>
 * 3 signaux indépendants ({@link list}, {@link detail}, {@link mutation}) — une
 * erreur sur l'un n'affecte pas les autres vues ouvertes simultanément.
 *
 * <h3>Transitions d'état</h3>
 * Les méthodes {@link confirm}, {@link complete}, {@link cancel} et
 * {@link markNoShow} mettent à jour <b>localement</b> le statut du détail
 * après succès HTTP, pour éviter un re-fetch. Si l'API renvoie une erreur
 * (ex: 400 transition invalide), le signal reste inchangé et l'erreur est
 * propagée via {@code throwError}.
 *
 * <h3>Conflit horaire (409)</h3>
 * Le backend détecte les chevauchements de créneaux pour le même médecin
 * et renvoie 409 sur create/update. Le message traduit "Conflit — créneau
 * déjà occupé" est exposé via {@link mutation}.
 */
@Injectable({ providedIn: 'root' })
export class AppointmentFacade {
  private readonly api = inject(AppointmentApi);

  // ─── Signals d'état ───
  private readonly _list = signal<AppointmentListState>(INITIAL_LIST);
  private readonly _detail = signal<AppointmentDetailState>(INITIAL_DETAIL);
  private readonly _mutation = signal<AppointmentMutationState>(INITIAL_MUTATION);

  /** Mémorise la dernière recherche pour {@link refresh}. */
  private lastCriteria: AppointmentSearchCriteria = {};
  private lastPageRequest: PageRequest = DEFAULT_APPOINTMENT_PAGE_REQUEST;

  // ─── Lectures publiques ───
  readonly list = this._list.asReadonly();
  readonly detail = this._detail.asReadonly();
  readonly mutation = this._mutation.asReadonly();

  /** True si une mutation est en cours. */
  readonly mutating = computed(() => this._mutation().saving);

  /**
   * Recherche paginée. Mémorise les params pour {@link refresh}.
   */
  search(
    criteria: AppointmentSearchCriteria = {},
    pageRequest: PageRequest = DEFAULT_APPOINTMENT_PAGE_REQUEST
  ): void {
    this.lastCriteria = criteria;
    this.lastPageRequest = pageRequest;
    this._list.update((s) => ({ ...s, loading: true, error: null }));

    this.api
      .search(criteria, pageRequest)
      .pipe(
        catchError((err) => {
          this._list.set({
            ...INITIAL_LIST,
            page: pageRequest.page ?? 0,
            size: pageRequest.size ?? INITIAL_LIST.size,
            error: this.translateError(err, 'list')
          });
          return throwError(() => err);
        }),
        finalize(() => {
          this._list.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe({
        next: (page) => {
          this._list.set({
            loading: false,
            appointments: page.content,
            totalElements: page.totalElements,
            totalPages: page.totalPages,
            page: page.page,
            size: page.size,
            error: null
          });
        },
        error: () => {
          /* déjà géré dans catchError */
        }
      });
  }

  /** Rejoue la dernière recherche avec les mêmes filtres/pagination. */
  refresh(): void {
    this.search(this.lastCriteria, this.lastPageRequest);
  }

  /**
   * Charge le détail d'un rendez-vous.
   */
  loadDetail(id: number): void {
    this._detail.update((s) => ({ ...s, loading: true, error: null }));
    this.api
      .findById(id)
      .pipe(
        catchError((err) => {
          this._detail.set({
            loading: false,
            appointment: null,
            error: this.translateError(err, 'detail')
          });
          return throwError(() => err);
        }),
        finalize(() => {
          this._detail.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe({
        next: (appointment) => {
          this._detail.set({ loading: false, appointment, error: null });
        },
        error: () => {
          /* idem */
        }
      });
  }

  /** Vide le détail (à appeler dans ngOnDestroy de la page détail). */
  clearDetail(): void {
    this._detail.set(INITIAL_DETAIL);
  }

  /**
   * Crée un rendez-vous.
   */
  create(request: CreateAppointmentRequest): Observable<AppointmentDto> {
    this._mutation.set({ saving: true, error: null });
    return this.api.create(request).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /**
   * Met à jour un rendez-vous (PATCH partiel).
   *
   * <p>Si le détail courant correspond à l'id mis à jour, on rafraîchit
   * le signal localement avec la réponse — pas de re-fetch nécessaire.
   */
  update(id: number, request: UpdateAppointmentRequest): Observable<AppointmentDto> {
    this._mutation.set({ saving: true, error: null });
    return this.api.update(id, request).pipe(
      tap((updated) => {
        this._mutation.set({ saving: false, error: null });
        this.patchDetailIfMatching(id, updated);
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /**
   * Transition {@code PLANNED → CONFIRMED}.
   *
   * <p>Backend renvoie {@code void}. On met à jour le détail localement
   * en passant le statut à {@code CONFIRMED} pour éviter un re-fetch.
   */
  confirm(id: number): Observable<void> {
    return this.runTransition(id, 'CONFIRMED', () => this.api.confirm(id));
  }

  /** Transition {@code CONFIRMED → COMPLETED} (consultation terminée). */
  complete(id: number): Observable<void> {
    return this.runTransition(id, 'COMPLETED', () => this.api.complete(id));
  }

  /** Annule un rendez-vous (peu importe l'état actuel s'il n'est pas final). */
  cancel(id: number): Observable<void> {
    return this.runTransition(id, 'CANCELLED', () => this.api.cancel(id));
  }

  /** Marque comme NO_SHOW (patient absent). */
  markNoShow(id: number): Observable<void> {
    return this.runTransition(id, 'NO_SHOW', () => this.api.markNoShow(id));
  }

  /** Supprime un rendez-vous (rare — typiquement on annule au lieu de delete). */
  delete(id: number): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return this.api.delete(id).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        // Si on observait ce RDV en détail, on le clear
        if (this._detail().appointment?.id === id) {
          this._detail.set(INITIAL_DETAIL);
        }
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /** Reset complet — au logout ou pour les tests. */
  reset(): void {
    this._list.set(INITIAL_LIST);
    this._detail.set(INITIAL_DETAIL);
    this._mutation.set(INITIAL_MUTATION);
    this.lastCriteria = {};
    this.lastPageRequest = DEFAULT_APPOINTMENT_PAGE_REQUEST;
  }

  // ─── Helpers privés ───

  /**
   * Lance une transition d'état avec mise à jour optimiste du détail courant.
   * Si le détail correspond à l'id, le statut est patché vers
   * {@code newStatus} après succès HTTP.
   */
  private runTransition(
    id: number,
    newStatus: AppointmentStatus,
    apiCall: () => Observable<void>
  ): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return apiCall().pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        const current = this._detail();
        if (current.appointment?.id === id) {
          this._detail.set({
            ...current,
            appointment: { ...current.appointment, status: newStatus }
          });
        }
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /**
   * Met à jour le détail si on observe le même id que celui mis à jour.
   */
  private patchDetailIfMatching(id: number, updated: AppointmentDto): void {
    const current = this._detail();
    if (current.appointment?.id === id) {
      this._detail.set({ ...current, appointment: updated });
    }
  }

  /**
   * Traduction des erreurs HTTP en messages utilisateur, avec contexte.
   *
   * <p>Le code 409 a une signification spécifique pour les RDV : conflit
   * de créneau (le médecin a déjà un RDV à cette heure). On affiche un
   * message dédié pour aider l'utilisateur à comprendre.
   */
  private translateError(
    err: unknown,
    context: 'list' | 'detail' | 'mutation' = 'detail'
  ): string {
    if (err && typeof err === 'object' && 'status' in err) {
      const status = (err as { status: number }).status;
      if (status === 401) return 'Non authentifié';
      if (status === 403) return 'Accès refusé';
      if (status === 404) {
        return context === 'list'
          ? 'Service rendez-vous indisponible — vérifiez la connexion au backend'
          : 'Rendez-vous introuvable';
      }
      if (status === 409) {
        return context === 'mutation'
          ? 'Conflit — ce créneau est déjà occupé pour ce médecin, ou le rendez-vous a été modifié entre-temps'
          : 'Conflit de données';
      }
      if (status === 400) {
        return 'Transition d\'état invalide — vérifiez le statut actuel du rendez-vous';
      }
      if (status === 0) return 'Serveur injoignable';
      if (status >= 500) return 'Erreur serveur — réessayez plus tard';
      return `Erreur HTTP ${status}`;
    }
    return err instanceof Error ? err.message : 'Erreur inconnue';
  }
}
