import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import { DoctorApi } from '@api/doctor.api';
import {
  CreateDoctorRequest,
  DoctorDto,
  DoctorSearchCriteria,
  UpdateDoctorRequest
} from '@api/models/doctor.model';
import { PageRequest } from '@api/common.types';

/**
 * État de la liste des médecins — pagination + filtres serveur.
 */
export interface DoctorListState {
  readonly loading: boolean;
  readonly doctors: ReadonlyArray<DoctorDto>;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly page: number;
  readonly size: number;
  readonly error: string | null;
}

/**
 * État du médecin sélectionné (page détail).
 */
export interface DoctorDetailState {
  readonly loading: boolean;
  readonly doctor: DoctorDto | null;
  readonly error: string | null;
}

/**
 * État d'une mutation en cours (create/update/transition d'état).
 *
 * <p>Le composant qui déclenche la mutation reçoit aussi un Observable, qu'il
 * peut subscribe pour gérer la navigation post-succès. Ce signal est lu pour
 * afficher un indicateur "saving" global (bouton désactivé, etc.).
 */
export interface DoctorMutationState {
  readonly saving: boolean;
  readonly error: string | null;
}

const INITIAL_LIST: DoctorListState = {
  loading: false,
  doctors: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: 20,
  error: null
};

const INITIAL_DETAIL: DoctorDetailState = {
  loading: false,
  doctor: null,
  error: null
};

const INITIAL_MUTATION: DoctorMutationState = {
  saving: false,
  error: null
};

/** Pagination par défaut — réutilisée par {@link search} si non précisée. */
export const DEFAULT_DOCTOR_PAGE_REQUEST: PageRequest = {
  page: 0,
  size: 20,
  sort: 'lastName,asc'
};

/**
 * Façade Doctors — orchestre le CRUD au-dessus de {@link DoctorApi}.
 *
 * <p>Pattern signals : 1 signal par "vue" indépendante (liste, détail, mutation)
 * pour qu'une opération en erreur ne casse pas une autre vue ouverte
 * simultanément.
 *
 * <h3>Workflow état médecin</h3>
 * <pre>
 *   ACTIVE ──putOnLeave()──▶ ON_LEAVE ──reactivate()──▶ ACTIVE
 *      │                                                  ▲
 *      │                                                  │
 *      └──retire()──▶ RETIRED ──reactivate()──────────────┘
 * </pre>
 *
 * <p>La <b>suppression physique</b> ({@link delete}) est exposée par l'API mais
 * <b>n'est volontairement pas câblée à l'UI</b> côté admin (étape 9.7) — pour
 * préserver l'historique audit. Un médecin retiré reste dans le système ; on
 * passe par {@code retire} pour le désactiver. La méthode est gardée ici par
 * cohérence avec l'API et pour usage exceptionnel via un futur outil admin.
 *
 * <h3>Refresh liste après mutation</h3>
 * Les transitions d'état ({@link putOnLeave}/{@link retire}/{@link reactivate})
 * mettent à jour le détail chargé si l'id matche, mais ne rafraîchissent
 * <b>pas automatiquement la liste</b>. Le composant liste rappelle
 * {@link refresh} après mutation s'il est encore monté.
 */
@Injectable({ providedIn: 'root' })
export class DoctorFacade {
  private readonly api = inject(DoctorApi);

  // ─── Signals d'état ───
  private readonly _list = signal<DoctorListState>(INITIAL_LIST);
  private readonly _detail = signal<DoctorDetailState>(INITIAL_DETAIL);
  private readonly _mutation = signal<DoctorMutationState>(INITIAL_MUTATION);

  /** Mémorise la dernière recherche pour permettre un refresh sans dupliquer les params côté composant. */
  private lastCriteria: DoctorSearchCriteria = {};
  private lastPageRequest: PageRequest = DEFAULT_DOCTOR_PAGE_REQUEST;

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
    criteria: DoctorSearchCriteria = {},
    pageRequest: PageRequest = DEFAULT_DOCTOR_PAGE_REQUEST
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
            doctors: page.content,
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
   * Charge le détail d'un médecin. Met à jour {@link detail}.
   */
  loadDetail(id: number): void {
    this._detail.update((s) => ({ ...s, loading: true, error: null }));
    this.api
      .findById(id)
      .pipe(
        catchError((err) => {
          this._detail.set({
            loading: false,
            doctor: null,
            error: this.translateError(err, 'detail')
          });
          return throwError(() => err);
        }),
        finalize(() => {
          this._detail.update((s) => ({ ...s, loading: false }));
        })
      )
      .subscribe({
        next: (doctor) => {
          this._detail.set({ loading: false, doctor, error: null });
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
   * Crée un médecin. Renvoie l'Observable du DTO créé pour que le composant
   * puisse naviguer après succès. Le {@code licenseNumber} est généré
   * côté serveur — non présent dans la {@link CreateDoctorRequest}.
   */
  create(request: CreateDoctorRequest): Observable<DoctorDto> {
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
   * Met à jour un médecin (PATCH partiel). Le composant gère le 409
   * (optimistic locking) via le message dans {@link mutation}.
   *
   * <p>Le {@code status} est <b>volontairement omis</b> côté UI form — les
   * transitions passent par {@link putOnLeave}/{@link retire}/{@link reactivate}.
   * Mais l'API l'accepte si fourni — c'est la responsabilité du composant
   * de ne pas l'inclure.
   */
  update(id: number, request: UpdateDoctorRequest): Observable<DoctorDto> {
    this._mutation.set({ saving: true, error: null });
    return this.api.update(id, request).pipe(
      tap((updated) => {
        this._mutation.set({ saving: false, error: null });
        const current = this._detail();
        if (current.doctor?.id === id) {
          this._detail.set({ ...current, doctor: updated });
        }
      }),
      catchError((err) => {
        this._mutation.set({ saving: false, error: this.translateError(err, 'mutation') });
        return throwError(() => err);
      })
    );
  }

  /**
   * Met un médecin en congé (ACTIVE → ON_LEAVE).
   *
   * <p>Réservé ADMIN côté backend.
   */
  putOnLeave(id: number): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return this.api.putOnLeave(id).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        const current = this._detail();
        if (current.doctor?.id === id) {
          this._detail.set({
            ...current,
            doctor: { ...current.doctor, status: 'ON_LEAVE' }
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
   * Met un médecin en retraite (ACTIVE / ON_LEAVE → RETIRED).
   *
   * <p>Action terminale métier ; toutefois la {@link reactivate} permet le
   * retour de retraite si nécessaire (cf. workflow validé en 9.7 design).
   */
  retire(id: number): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return this.api.retire(id).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        const current = this._detail();
        if (current.doctor?.id === id) {
          this._detail.set({
            ...current,
            doctor: { ...current.doctor, status: 'RETIRED' }
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
   * Réactive un médecin (ON_LEAVE / RETIRED → ACTIVE).
   *
   * <p>Le retour de retraite est autorisé en UI (cf. décision 9.7 design).
   */
  reactivate(id: number): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return this.api.reactivate(id).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        const current = this._detail();
        if (current.doctor?.id === id) {
          this._detail.set({
            ...current,
            doctor: { ...current.doctor, status: 'ACTIVE' }
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
   * Supprime physiquement un médecin (DELETE en base).
   *
   * <p><b>NON exposée à l'UI admin standard</b> — gardée par cohérence avec
   * l'API et pour outil admin futur (cas exceptionnel : erreur de saisie
   * avant que des consultations / RDV ne soient créés). Préférer {@link retire}
   * pour préserver l'historique.
   */
  delete(id: number): Observable<void> {
    this._mutation.set({ saving: true, error: null });
    return this.api.delete(id).pipe(
      tap(() => {
        this._mutation.set({ saving: false, error: null });
        const current = this._detail();
        if (current.doctor?.id === id) {
          this._detail.set(INITIAL_DETAIL);
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
    this.lastPageRequest = DEFAULT_DOCTOR_PAGE_REQUEST;
  }

  /**
   * Traduction des erreurs HTTP en messages utilisateur, avec contexte.
   *
   * <p>Le code 404 a un sens différent selon l'opération :
   * <ul>
   *   <li>{@code 'list'} : 404 = endpoint introuvable (backend down ou route gateway cassée)</li>
   *   <li>{@code 'detail'} / {@code 'mutation'} : 404 = médecin inexistant ou supprimé</li>
   * </ul>
   *
   * <p>Le code 409 (transition d'état refusée — par exemple `retire` sur un
   * RETIRED) est traduit en message générique de conflit ; le composant peut
   * choisir d'afficher un message plus spécifique selon le contexte de l'action.
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
          ? 'Service médecin indisponible — vérifiez la connexion au backend'
          : 'Médecin introuvable';
      }
      if (status === 409) {
        return context === 'mutation'
          ? 'Transition d\'état refusée — l\'état courant ne permet pas cette action, ou les données ont été modifiées par un autre utilisateur. Veuillez recharger.'
          : 'Conflit — données modifiées par un autre utilisateur, veuillez recharger';
      }
      if (status === 422) return 'Données invalides — vérifiez les champs du formulaire';
      if (status === 0) return 'Serveur injoignable';
      if (status >= 500) return 'Erreur serveur — réessayez plus tard';
      return `Erreur HTTP ${status}`;
    }
    return err instanceof Error ? err.message : 'Erreur inconnue';
  }
}
