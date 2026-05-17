import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import { PatientApi } from '@api/patient.api';
import {
  CreatePatientRequest,
  PatientDto,
  PatientSearchCriteria,
  UpdatePatientRequest
} from '@api/models/patient.model';
import { PageRequest } from '@api/common.types';

/**
 * État de la liste des patients — pagination + filtres serveur.
 */
export interface PatientListState {
  readonly loading: boolean;
  readonly patients: ReadonlyArray<PatientDto>;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly page: number;
  readonly size: number;
  readonly error: string | null;
}

/**
 * État du patient sélectionné (page détail / modal carte).
 */
export interface PatientDetailState {
  readonly loading: boolean;
  readonly patient: PatientDto | null;
  readonly error: string | null;
}

/**
 * État d'une mutation en cours (create/update/archive).
 *
 * <p>Le composant qui déclenche la mutation reçoit aussi un Observable, qu'il
 * peut subscribe pour gérer la navigation post-succès. Ce signal est lu pour
 * afficher un indicateur "saving" global (bouton désactivé, etc.).
 */
export interface PatientMutationState {
  readonly saving: boolean;
  readonly error: string | null;
}

const INITIAL_LIST: PatientListState = {
  loading: false,
  patients: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: 20,
  error: null
};

const INITIAL_DETAIL: PatientDetailState = {
  loading: false,
  patient: null,
  error: null
};

const INITIAL_MUTATION: PatientMutationState = {
  saving: false,
  error: null
};

/** Pagination par défaut — réutilisée par {@link search} si non précisée. */
export const DEFAULT_PATIENT_PAGE_REQUEST: PageRequest = {
  page: 0,
  size: 20,
  sort: 'lastName,asc'
};

/**
 * Façade Patients — orchestre le CRUD au-dessus de {@link PatientApi}.
 *
 * <p>Pattern signals : 1 signal par "vue" indépendante (liste, détail, mutation)
 * pour qu'une opération en erreur ne casse pas une autre vue ouverte
 * simultanément (ex: une création échoue mais la liste reste affichée).
 *
 * <h3>Cycle de vie typique d'une page liste</h3>
 * <ol>
 *   <li>Composant lit la query string → construit criteria + pageRequest</li>
 *   <li>Appelle {@link search} → met à jour {@link list}</li>
 *   <li>Pagination/tri → re-appelle {@link search} avec nouveau pageRequest</li>
 *   <li>Click sur ligne → navigation vers /patients/:id (composant détail)</li>
 * </ol>
 */
@Injectable({ providedIn: 'root' })
export class PatientFacade {
  private readonly api = inject(PatientApi);

  // ─── Signals d'état ───
  private readonly _list = signal<PatientListState>(INITIAL_LIST);
  private readonly _detail = signal<PatientDetailState>(INITIAL_DETAIL);
  private readonly _mutation = signal<PatientMutationState>(INITIAL_MUTATION);

  /** Mémorise la dernière recherche pour permettre un refresh sans dupliquer les params côté composant. */
  private lastCriteria: PatientSearchCriteria = {};
  private lastPageRequest: PageRequest = DEFAULT_PATIENT_PAGE_REQUEST;

  // ─── Lectures publiques ───
  readonly list = this._list.asReadonly();
  readonly detail = this._detail.asReadonly();
  readonly mutation = this._mutation.asReadonly();

  /** True si une mutation est en cours (utile pour désactiver les boutons globaux). */
  readonly mutating = computed(() => this._mutation().saving);

  /**
   * Recherche paginée. Met à jour {@link list} et mémorise les params pour
   * un éventuel {@link refresh}.
   */
  search(
    criteria: PatientSearchCriteria = {},
    pageRequest: PageRequest = DEFAULT_PATIENT_PAGE_REQUEST
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
            patients: page.content,
            totalElements: page.totalElements,
            totalPages: page.totalPages,
            page: page.page,
            size: page.size,
            error: null
          });
        },
        error: () => {
          /* déjà géré dans catchError — on swallow ici pour ne pas faire crasher l'observable */
        }
      });
  }

  /** Rejoue la dernière recherche avec les mêmes critères/pagination. */
  refresh(): void {
    this.search(this.lastCriteria, this.lastPageRequest);
  }

  /**
   * Charge le détail d'un patient. Met à jour {@link detail}.
   */
  loadDetail(id: number): void {
    this._detail.update((s) => ({ ...s, loading: true, error: null }));
    this.api
      .findById(id)
      .pipe(
        catchError((err) => {
          this._detail.set({
            loading: false,
            patient: null,
            error: this.translateError(err, 'detail')
          });
          return throwError(() => err);
        }),
        finalize(() => {
          this._detail.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe({
        next: (patient) => {
          this._detail.set({ loading: false, patient, error: null });
        },
        error: () => {
          /* idem : déjà persisté dans le signal */
        }
      });
  }

  /** Vide le détail (à appeler dans ngOnDestroy de la page détail). */
  clearDetail(): void {
    this._detail.set(INITIAL_DETAIL);
  }

  /**
   * Crée un patient. Renvoie l'Observable du DTO créé pour que le composant
   * puisse naviguer après succès.
   */
  create(request: CreatePatientRequest): Observable<PatientDto> {
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
   * Met à jour un patient (PATCH partiel). Le composant gère le 409
   * (optimistic locking) via le message dans {@link mutation}.
   */
  update(id: number, request: UpdatePatientRequest): Observable<PatientDto> {
    this._mutation.set({ saving: true, error: null });
    return this.api.update(id, request).pipe(
      tap((updated) => {
        this._mutation.set({ saving: false, error: null });
        // Si on a aussi le détail chargé pour ce même id, on le met à jour
        // pour refléter immédiatement les changements sans re-fetch.
        const current = this._detail();
        if (current.patient?.id === id) {
          this._detail.set({ ...current, patient: updated });
        }
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /**
   * Archive un patient (soft-delete → status=ARCHIVED).
   *
   * <p>Réservé ADMIN côté backend — un appel non autorisé renvoie 403,
   * traduit ici en "Accès refusé". Le composant doit cacher le bouton
   * pour les autres rôles, mais cette défense en profondeur reste utile.
   */
  archive(id: number): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return this.api.archive(id).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        // Refléter le nouveau statut dans le détail si chargé
        const current = this._detail();
        if (current.patient?.id === id) {
          this._detail.set({
            ...current,
            patient: { ...current.patient, status: 'ARCHIVED' }
          });
        }
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /** Reset complet — utile au logout ou pour les tests. */
  reset(): void {
    this._list.set(INITIAL_LIST);
    this._detail.set(INITIAL_DETAIL);
    this._mutation.set(INITIAL_MUTATION);
    this.lastCriteria = {};
    this.lastPageRequest = DEFAULT_PATIENT_PAGE_REQUEST;
  }

  /**
   * Traduction des erreurs HTTP en messages utilisateur, avec contexte.
   *
   * <p>Le code 404 a un sens différent selon l'opération :
   * <ul>
   *   <li>{@code 'list'} : 404 = endpoint introuvable (backend down ou route gateway cassée)</li>
   *   <li>{@code 'detail'} / {@code 'mutation'} : 404 = patient inexistant ou supprimé</li>
   * </ul>
   *
   * <p>Pattern réutilisé dans les autres facades — gardé local pour éviter
   * une dépendance partagée tant que la liste de codes n'est pas standardisée.
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
          ? 'Service patient indisponible — vérifiez la connexion au backend'
          : 'Patient introuvable';
      }
      if (status === 409) return 'Conflit — données modifiées par un autre utilisateur, veuillez recharger';
      if (status === 0) return 'Serveur injoignable';
      if (status >= 500) return 'Erreur serveur — réessayez plus tard';
      return `Erreur HTTP ${status}`;
    }
    return err instanceof Error ? err.message : 'Erreur inconnue';
  }
}
