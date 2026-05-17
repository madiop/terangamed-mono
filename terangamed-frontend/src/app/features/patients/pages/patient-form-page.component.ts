import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { fr } from 'date-fns/locale';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import {
  BloodGroup,
  Civility,
  CreatePatientRequest,
  Gender,
  PatientStatus,
  UpdatePatientRequest
} from '@api/models/patient.model';
import { PatientFacade } from '../patient.facade';

type FormMode = 'CREATE' | 'EDIT';

/**
 * Page formulaire patient — gère création ET édition.
 *
 * <h3>Détection du mode</h3>
 * <ul>
 *   <li>Route {@code /patients/new} → mode CREATE (form vide)</li>
 *   <li>Route {@code /patients/:id/edit} → mode EDIT (form pré-rempli via
 *       {@code facade.loadDetail()})</li>
 * </ul>
 *
 * <h3>Soumission</h3>
 * <ul>
 *   <li>CREATE → {@code facade.create()} puis nav vers le détail du patient créé</li>
 *   <li>EDIT → {@code facade.update()} puis nav vers le détail</li>
 * </ul>
 *
 * <h3>Validation</h3>
 * Reactive Forms avec :
 * <ul>
 *   <li>required : civility, lastName, firstName, birthDate, gender</li>
 *   <li>maxLength selon le backend (cohérent avec @Size sur les DTOs)</li>
 *   <li>email format sur le champ email</li>
 *   <li>past validator sur birthDate (pas dans le futur)</li>
 * </ul>
 *
 * <h3>Optimistic locking</h3>
 * Si le backend renvoie 409 Conflict (entité modifiée par un autre user
 * entre temps), on affiche un message clair avec un bouton "Recharger"
 * qui re-fetch le patient et écrase les modifications locales.
 */
@Component({
  selector: 'tm-patient-form-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    provideDateFnsAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: fr }
  ],
  template: `
    <div class="patient-form-page">
      <tm-page-header
        [title]="mode() === 'CREATE' ? 'Nouveau patient' : 'Modifier le patient'"
        [subtitle]="subtitle()"
      />

      @if (loadingDetail()) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement du patient…</p>
        </div>
      } @else {
        @if (loadDetailError(); as err) {
          <div class="error-banner" role="alert">
            <span class="material-icons-round">error_outline</span>
            <p>{{ err }}</p>
            <button type="button" class="btn btn-link" (click)="goBackToList()">
              Retour à la liste
            </button>
          </div>
        } @else {
        @if (submitError(); as err) {
          <div class="error-banner" role="alert">
            <span class="material-icons-round">error_outline</span>
            <p>{{ err }}</p>
            @if (isConflict()) {
              <button type="button" class="btn btn-link" (click)="reloadFromServer()">
                Recharger depuis le serveur
              </button>
            }
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="patient-form">
          <!-- ─── Identité ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">person</span>
              Identité
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Civilité *</mat-label>
                <mat-select formControlName="civility">
                  <mat-option value="M">Monsieur</mat-option>
                  <mat-option value="MME">Madame</mat-option>
                  <mat-option value="MLLE">Mademoiselle</mat-option>
                  <mat-option value="DR">Docteur</mat-option>
                  <mat-option value="AUTRE">Autre</mat-option>
                </mat-select>
                @if (showError('civility')) {
                  <mat-error>Champ obligatoire</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Genre *</mat-label>
                <mat-select formControlName="gender">
                  <mat-option value="MALE">Homme</mat-option>
                  <mat-option value="FEMALE">Femme</mat-option>
                </mat-select>
                @if (showError('gender')) {
                  <mat-error>Champ obligatoire</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Nom *</mat-label>
                <input matInput formControlName="lastName" maxlength="100" />
                @if (showError('lastName')) {
                  <mat-error>{{ errorOf('lastName') }}</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Prénom *</mat-label>
                <input matInput formControlName="firstName" maxlength="100" />
                @if (showError('firstName')) {
                  <mat-error>{{ errorOf('firstName') }}</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Date de naissance *</mat-label>
                <input matInput [matDatepicker]="picker" formControlName="birthDate" [max]="today" />
                <mat-datepicker-toggle matIconSuffix [for]="picker" />
                <mat-datepicker #picker startView="multi-year" />
                @if (showError('birthDate')) {
                  <mat-error>{{ errorOf('birthDate') }}</mat-error>
                }
              </mat-form-field>

              @if (mode() === 'EDIT') {
                <mat-form-field appearance="outline">
                  <mat-label>Statut</mat-label>
                  <mat-select formControlName="status">
                    <mat-option value="ACTIVE">Actif</mat-option>
                    <mat-option value="INACTIVE">Inactif</mat-option>
                    <!-- ARCHIVED uniquement via l'action dédiée, pas via le form -->
                  </mat-select>
                </mat-form-field>
              }
            </div>
          </fieldset>

          <!-- ─── Contact ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">contact_phone</span>
              Contact
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Téléphone</mat-label>
                <input matInput formControlName="phone" maxlength="20" placeholder="0177000001" />
                @if (showError('phone')) {
                  <mat-error>{{ errorOf('phone') }}</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email" maxlength="100" />
                @if (showError('email')) {
                  <mat-error>{{ errorOf('email') }}</mat-error>
                }
              </mat-form-field>
            </div>
          </fieldset>

          <!-- ─── Adresse ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">place</span>
              Adresse
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline" class="span-2">
                <mat-label>Ligne 1</mat-label>
                <input matInput formControlName="addressLine1" maxlength="200" />
              </mat-form-field>

              <mat-form-field appearance="outline" class="span-2">
                <mat-label>Ligne 2</mat-label>
                <input matInput formControlName="addressLine2" maxlength="200" />
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Code postal</mat-label>
                <input matInput formControlName="postalCode" maxlength="20" />
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Ville</mat-label>
                <input matInput formControlName="city" maxlength="100" />
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Pays</mat-label>
                <input matInput formControlName="country" maxlength="100" placeholder="Sénégal" />
              </mat-form-field>
            </div>
          </fieldset>

          <!-- ─── Médical ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">favorite</span>
              Informations médicales
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Groupe sanguin</mat-label>
                <mat-select formControlName="bloodGroup">
                  <mat-option [value]="null">Inconnu</mat-option>
                  <mat-option value="A_POS">A+</mat-option>
                  <mat-option value="A_NEG">A−</mat-option>
                  <mat-option value="B_POS">B+</mat-option>
                  <mat-option value="B_NEG">B−</mat-option>
                  <mat-option value="AB_POS">AB+</mat-option>
                  <mat-option value="AB_NEG">AB−</mat-option>
                  <mat-option value="O_POS">O+</mat-option>
                  <mat-option value="O_NEG">O−</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline" class="span-2">
                <mat-label>Allergies connues</mat-label>
                <textarea matInput formControlName="allergies" rows="2"
                          placeholder="Texte libre — ex: Pénicilline, fruits à coque…"></textarea>
              </mat-form-field>
            </div>
          </fieldset>

          <!-- ─── Contact d'urgence ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">emergency</span>
              Contact d'urgence
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Nom du contact</mat-label>
                <input matInput formControlName="emergencyContactName" maxlength="200" />
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Téléphone du contact</mat-label>
                <input matInput formControlName="emergencyContactPhone" maxlength="20" />
              </mat-form-field>
            </div>
          </fieldset>

          <!-- Actions -->
          <div class="form-actions">
            <button type="button" class="btn btn-outline" (click)="onCancel()" [disabled]="saving()">
              Annuler
            </button>
            <button type="submit" class="btn btn-primary" [disabled]="form.invalid || saving()">
              @if (saving()) {
                <span class="material-icons-round spin">progress_activity</span>
              } @else {
                <span class="material-icons-round">{{ mode() === 'CREATE' ? 'add' : 'save' }}</span>
              }
              {{ mode() === 'CREATE' ? 'Créer le patient' : 'Enregistrer' }}
            </button>
          </div>
        </form>
        }
      }
    </div>
  `,
  styles: [
    `
      .patient-form-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .loading-state,
      .error-banner {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px;
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .loading-state {
        justify-content: center;
        flex-direction: column;
        padding: 48px 24px;
      }
      .loading-state .material-icons-round,
      .error-banner .material-icons-round {
        font-size: 24px;
      }
      .loading-state .material-icons-round {
        font-size: 32px;
        color: var(--color-text-muted);
      }
      .error-banner {
        background: #fef2f2;
        border-left: 4px solid #ef4444;
        color: #991b1b;
      }
      .error-banner p {
        flex: 1;
        margin: 0;
      }
      .error-banner .btn-link {
        background: none;
        border: none;
        color: #991b1b;
        text-decoration: underline;
        cursor: pointer;
        font-weight: 600;
        padding: 0;
      }
      .spin {
        animation: tm-spin 0.9s linear infinite;
      }
      @keyframes tm-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .patient-form {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .form-section {
        border: none;
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 20px 24px;
        margin: 0;
      }
      .form-section legend {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 15px;
        font-weight: 600;
        margin-bottom: 16px;
        color: var(--color-text);
        padding: 0;
      }
      .form-section legend .material-icons-round {
        font-size: 20px;
        color: var(--color-primary, #2963b0);
      }

      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px 16px;

        @media (max-width: 700px) {
          grid-template-columns: 1fr;
        }
      }
      .span-2 {
        grid-column: span 2;
        @media (max-width: 700px) {
          grid-column: span 1;
        }
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        padding: 16px 0;
      }
      .form-actions .btn {
        min-width: 140px;
      }
      .form-actions .spin {
        font-size: 18px;
      }

      ::ng-deep .patient-form .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class PatientFormPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly facade = inject(PatientFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  /** ID patient en mode EDIT, undefined en mode CREATE. */
  private patientId: number | undefined;

  /** Version JPA — utilisée pour optimistic locking au prochain submit. */
  private patientVersion: number | undefined;

  protected readonly mode = signal<FormMode>('CREATE');
  protected readonly saving = signal(false);
  protected readonly submitError = signal<string | null>(null);

  /**
   * Loading dérivé de facade.detail() en mode EDIT uniquement.
   * <p><b>Pourquoi computed et pas signal ?</b> Les writes de signaux dans
   * un {@code effect()} sont interdits par défaut en Angular 17 (silencieusement
   * ignorés). Pour éviter ce piège, on dérive l'état directement de la facade.
   */
  protected readonly loadingDetail = computed(() =>
    this.mode() === 'EDIT' && this.facade.detail().loading
  );

  /** Erreur dérivée de facade.detail().error — uniquement en mode EDIT. */
  protected readonly loadDetailError = computed(() =>
    this.mode() === 'EDIT' ? this.facade.detail().error : null
  );

  /** True si la dernière erreur de submit est un 409 (Conflict). */
  protected readonly isConflict = computed(() =>
    !!this.submitError()?.toLowerCase().includes('conflit')
  );

  protected readonly subtitle = computed(() =>
    this.mode() === 'CREATE'
      ? 'Renseignez les informations du nouveau patient'
      : `Mise à jour du patient #${this.patientId ?? ''}`
  );

  /** Date d'aujourd'hui — borne sup du datepicker birthDate (pas de futur). */
  protected readonly today = new Date();

  protected readonly form: FormGroup = this.fb.group({
    civility: this.fb.control<Civility | null>(null, Validators.required),
    lastName: this.fb.control<string>('', [Validators.required, Validators.maxLength(100)]),
    firstName: this.fb.control<string>('', [Validators.required, Validators.maxLength(100)]),
    birthDate: this.fb.control<Date | null>(null, Validators.required),
    gender: this.fb.control<Gender | null>(null, Validators.required),
    phone: this.fb.control<string>('', [Validators.maxLength(20)]),
    email: this.fb.control<string>('', [Validators.email, Validators.maxLength(100)]),
    addressLine1: this.fb.control<string>('', [Validators.maxLength(200)]),
    addressLine2: this.fb.control<string>('', [Validators.maxLength(200)]),
    postalCode: this.fb.control<string>('', [Validators.maxLength(20)]),
    city: this.fb.control<string>('', [Validators.maxLength(100)]),
    country: this.fb.control<string>('', [Validators.maxLength(100)]),
    bloodGroup: this.fb.control<BloodGroup | null>(null),
    allergies: this.fb.control<string>(''),
    emergencyContactName: this.fb.control<string>('', [Validators.maxLength(200)]),
    emergencyContactPhone: this.fb.control<string>('', [Validators.maxLength(20)]),
    status: this.fb.control<PatientStatus | null>(null) // visible uniquement en EDIT
  });

  /**
   * Effect qui patche le form quand le détail patient arrive (mode EDIT).
   *
   * <p>L'effect ne fait QUE des opérations non-signal (form.patchValue,
   * affectation de patientVersion). Le state UI (loading/error) est dérivé
   * en {@link computed} ci-dessus — pas d'écriture de signal dans l'effect.
   */
  private readonly _detailPatchEffect = effect(() => {
    const detail = this.facade.detail();
    if (this.mode() === 'EDIT' && detail.patient) {
      this.patientVersion = detail.patient.version;
      this.form.patchValue(
        {
          civility: detail.patient.civility,
          lastName: detail.patient.lastName,
          firstName: detail.patient.firstName,
          birthDate: detail.patient.birthDate ? new Date(detail.patient.birthDate) : null,
          gender: detail.patient.gender,
          phone: detail.patient.phone ?? '',
          email: detail.patient.email ?? '',
          addressLine1: detail.patient.addressLine1 ?? '',
          addressLine2: detail.patient.addressLine2 ?? '',
          postalCode: detail.patient.postalCode ?? '',
          city: detail.patient.city ?? '',
          country: detail.patient.country ?? '',
          bloodGroup: detail.patient.bloodGroup ?? null,
          allergies: detail.patient.allergies ?? '',
          emergencyContactName: detail.patient.emergencyContactName ?? '',
          emergencyContactPhone: detail.patient.emergencyContactPhone ?? '',
          status: detail.patient.status
        },
        { emitEvent: false }
      );
    }
  });

  ngOnInit(): void {
    // Détection du mode via la présence de l'ID dans la route
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      if (idParam) {
        const id = Number(idParam);
        if (Number.isNaN(id) || id <= 0) {
          void this.router.navigate(['/patients']);
          return;
        }
        this.patientId = id;
        this.mode.set('EDIT');
        // loadingDetail / loadDetailError sont des computed dérivés de
        // facade.detail() — pas besoin de les set manuellement.
        this.facade.loadDetail(id);
      } else {
        // Mode création
        this.mode.set('CREATE');
        this.patientId = undefined;
        this.patientVersion = undefined;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.facade.clearDetail();
  }

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.submitError.set(null);

    if (this.mode() === 'CREATE') {
      const request = this.toCreateRequest();
      this.facade.create(request).subscribe({
        next: (created) => {
          this.saving.set(false);
          void this.router.navigate(['/patients', created.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    } else {
      // EDIT
      if (this.patientId === undefined) return;
      const request = this.toUpdateRequest();
      this.facade.update(this.patientId, request).subscribe({
        next: (updated) => {
          this.saving.set(false);
          void this.router.navigate(['/patients', updated.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    }
  }

  onCancel(): void {
    if (this.mode() === 'EDIT' && this.patientId !== undefined) {
      void this.router.navigate(['/patients', this.patientId]);
    } else {
      void this.router.navigate(['/patients']);
    }
  }

  goBackToList(): void {
    void this.router.navigate(['/patients']);
  }

  /** Recharge le patient depuis le serveur (utile après 409). */
  reloadFromServer(): void {
    if (this.patientId !== undefined) {
      this.submitError.set(null);
      // loadingDetail = computed(facade.detail().loading) → passera à true
      // automatiquement quand facade.loadDetail() update son signal interne.
      this.facade.loadDetail(this.patientId);
    }
  }

  // ─── Helpers de validation pour le template ───

  showError(controlName: string): boolean {
    const c = this.form.get(controlName);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  errorOf(controlName: string): string {
    const c = this.form.get(controlName);
    if (!c?.errors) return '';
    if (c.errors['required']) return 'Champ obligatoire';
    if (c.errors['email']) return 'Email invalide';
    if (c.errors['maxlength']) {
      return `Maximum ${c.errors['maxlength'].requiredLength} caractères`;
    }
    return 'Valeur invalide';
  }

  // ─── Construction des DTOs ───

  /**
   * Mode CREATE — construit un {@link CreatePatientRequest} en filtrant les
   * chaînes vides (le backend les distingue de null).
   */
  private toCreateRequest(): CreatePatientRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    return {
      civility: v['civility'] as Civility,
      lastName: (v['lastName'] as string).trim(),
      firstName: (v['firstName'] as string).trim(),
      birthDate: this.formatBirthDate(v['birthDate'] as Date),
      gender: v['gender'] as Gender,
      ...this.optionalString(v, 'phone'),
      ...this.optionalString(v, 'email'),
      ...this.optionalString(v, 'addressLine1'),
      ...this.optionalString(v, 'addressLine2'),
      ...this.optionalString(v, 'postalCode'),
      ...this.optionalString(v, 'city'),
      ...this.optionalString(v, 'country'),
      ...(v['bloodGroup'] ? { bloodGroup: v['bloodGroup'] as BloodGroup } : {}),
      ...this.optionalString(v, 'allergies'),
      ...this.optionalString(v, 'emergencyContactName'),
      ...this.optionalString(v, 'emergencyContactPhone')
    };
  }

  /**
   * Mode EDIT — construit un {@link UpdatePatientRequest}. Tout champ non
   * modifié reste néanmoins dans la requête (partial update côté backend
   * ignore les valeurs identiques de toute façon).
   */
  private toUpdateRequest(): UpdatePatientRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    return {
      civility: v['civility'] as Civility,
      lastName: (v['lastName'] as string).trim(),
      firstName: (v['firstName'] as string).trim(),
      birthDate: this.formatBirthDate(v['birthDate'] as Date),
      gender: v['gender'] as Gender,
      phone: this.normalizeOrEmpty(v['phone']),
      email: this.normalizeOrEmpty(v['email']),
      addressLine1: this.normalizeOrEmpty(v['addressLine1']),
      addressLine2: this.normalizeOrEmpty(v['addressLine2']),
      postalCode: this.normalizeOrEmpty(v['postalCode']),
      city: this.normalizeOrEmpty(v['city']),
      country: this.normalizeOrEmpty(v['country']),
      bloodGroup: (v['bloodGroup'] as BloodGroup) ?? undefined,
      allergies: this.normalizeOrEmpty(v['allergies']),
      emergencyContactName: this.normalizeOrEmpty(v['emergencyContactName']),
      emergencyContactPhone: this.normalizeOrEmpty(v['emergencyContactPhone']),
      status: (v['status'] as PatientStatus) ?? undefined
    };
  }

  /** Renvoie {key: trimmedValue} si la valeur est non-vide, sinon objet vide. */
  private optionalString(
    v: Record<string, unknown>,
    key: string
  ): Record<string, string> {
    const raw = v[key];
    if (typeof raw === 'string') {
      const trimmed = raw.trim();
      if (trimmed) return { [key]: trimmed };
    }
    return {};
  }

  /** Convertit "" en undefined pour partial update, trim sinon. */
  private normalizeOrEmpty(raw: unknown): string | undefined {
    if (typeof raw !== 'string') return undefined;
    const trimmed = raw.trim();
    return trimmed === '' ? '' : trimmed;
  }

  /** Date → ISO YYYY-MM-DD (LocalDate côté backend Java). */
  private formatBirthDate(d: Date): string {
    if (!(d instanceof Date) || Number.isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
