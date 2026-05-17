import { Injectable, computed, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, tap } from 'rxjs';
import { DoctorApi } from '@api/doctor.api';
import { DoctorDto } from '@api/models/doctor.model';
import { AuthService } from './auth.service';

/**
 * État de résolution du profil DoctorDto pour l'utilisateur DOCTOR connecté.
 *
 * <ul>
 *   <li>{@code IDLE} — non démarré (pas DOCTOR ou pas encore résolu)</li>
 *   <li>{@code LOADING} — appel API en cours</li>
 *   <li>{@code RESOLVED} — DoctorDto trouvé</li>
 *   <li>{@code NOT_FOUND} — user DOCTOR mais aucun Doctor lié à son compte
 *       Keycloak (admin doit appeler PUT /api/doctors/{id} avec keycloakSubject)</li>
 *   <li>{@code ERROR} — erreur HTTP (réseau, 5xx, etc.)</li>
 * </ul>
 */
export type CurrentDoctorStatus = 'IDLE' | 'LOADING' | 'RESOLVED' | 'NOT_FOUND' | 'ERROR';

export interface CurrentDoctorState {
  readonly status: CurrentDoctorStatus;
  readonly doctor: DoctorDto | null;
  readonly errorMessage: string | null;
}

const INITIAL_STATE: CurrentDoctorState = {
  status: 'IDLE',
  doctor: null,
  errorMessage: null
};

/**
 * Résout le {@link DoctorDto} correspondant au user DOCTOR connecté.
 *
 * <p><b>Stratégie</b> : un seul appel {@code GET /api/doctors/me}. Le backend
 * résout le médecin depuis le claim {@code sub} du JWT via la liaison
 * {@code doctor.keycloak_subject} (colonne V4). 404 signifie que le compte
 * Keycloak du DOCTOR n'est pas (encore) lié à un Doctor en base — c'est à
 * l'admin de faire le {@code PUT /api/doctors/{id}} avec {@code keycloakSubject}.
 *
 * <p>La résolution est <b>lazy</b> : déclenchée à la première lecture de
 * {@link resolve} et mise en cache. Pour forcer une nouvelle résolution
 * (ex: après création d'un Doctor), appeler {@link reset}.
 */
@Injectable({ providedIn: 'root' })
export class CurrentDoctorService {
  private readonly _state = signal<CurrentDoctorState>(INITIAL_STATE);
  readonly state = this._state.asReadonly();

  /** ID du Doctor courant ou null si non résolu. */
  readonly myDoctorId = computed<number | null>(() => this._state().doctor?.id ?? null);

  /** True si une résolution est en cours. */
  readonly loading = computed<boolean>(() => this._state().status === 'LOADING');

  /** True si le user DOCTOR n'a pas de profil correspondant. */
  readonly notFound = computed<boolean>(() => this._state().status === 'NOT_FOUND');

  /**
   * Cache l'observable de résolution pour éviter les appels HTTP redondants
   * pendant qu'une résolution est déjà en cours.
   */
  private resolution$: Observable<DoctorDto | null> | null = null;

  /**
   * Injection par constructeur (plus testable que {@code inject()} en field
   * initializer) — voir current-doctor.service.spec.ts.
   */
  constructor(
    private readonly auth: AuthService,
    private readonly doctorApi: DoctorApi
  ) {}

  /**
   * Lance (ou récupère) la résolution du Doctor courant.
   *
   * <p>Si le user n'est pas DOCTOR, renvoie immédiatement {@code null} sans
   * appel HTTP. Sinon, appelle {@code GET /api/doctors/me} et met à jour le
   * signal d'état (404 → NOT_FOUND, 200 → RESOLVED, autre → ERROR).
   *
   * @returns un observable qui complète avec le DoctorDto trouvé ou null
   */
  resolve(): Observable<DoctorDto | null> {
    // Cas 1 — déjà résolu : on renvoie immédiatement la valeur cached
    const current = this._state();
    if (current.status === 'RESOLVED') {
      return of(current.doctor);
    }

    // Cas 2 — résolution en cours : on share l'observable existant
    if (this.resolution$) {
      return this.resolution$;
    }

    // Cas 3 — pas DOCTOR : pas de résolution nécessaire, état IDLE
    if (!this.auth.hasAnyRole(['DOCTOR'])) {
      this._state.set(INITIAL_STATE);
      return of(null);
    }

    // Cas 4 — résolution effective via /api/doctors/me
    this._state.set({ status: 'LOADING', doctor: null, errorMessage: null });

    this.resolution$ = this.doctorApi.findMe().pipe(
      tap((doctor) => {
        this._state.set({ status: 'RESOLVED', doctor, errorMessage: null });
      }),
      catchError((err: unknown) => {
        if (err && typeof err === 'object' && 'status' in err
            && (err as { status: number }).status === 404) {
          this._state.set({
            status: 'NOT_FOUND',
            doctor: null,
            errorMessage:
              'Aucun profil médecin lié à votre compte. Contactez l\'administrateur ' +
              'pour qu\'il établisse la liaison.'
          });
        } else {
          this._state.set({
            status: 'ERROR',
            doctor: null,
            errorMessage: this.translateError(err)
          });
        }
        return of<DoctorDto | null>(null);
      }),
      finalize(() => {
        // Libère le cache de l'observable une fois la résolution terminée
        // (RESOLVED/NOT_FOUND/ERROR) — sinon les appels suivants ne tenteraient
        // jamais de re-résoudre via reset().
        this.resolution$ = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );

    return this.resolution$;
  }

  /**
   * Reset l'état — la prochaine résolution refera l'appel HTTP. À utiliser
   * après logout ou si l'admin vient de créer/modifier le profil Doctor.
   */
  reset(): void {
    this._state.set(INITIAL_STATE);
    this.resolution$ = null;
  }

  private translateError(err: unknown): string {
    if (err && typeof err === 'object' && 'status' in err) {
      const status = (err as { status: number }).status;
      if (status === 401) return 'Non authentifié';
      if (status === 403) return 'Accès refusé';
      if (status === 0) return 'Serveur injoignable';
      return `Erreur HTTP ${status}`;
    }
    return err instanceof Error ? err.message : 'Erreur inconnue';
  }
}
