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
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import {
  CreateDoctorRequest,
  Currency,
  Specialty,
  UpdateDoctorRequest
} from '@api/models/doctor.model';
import { DoctorFacade } from '../doctor.facade';
import { DoctorStatusBadgeComponent } from '../components/doctor-status-badge.component';

type FormMode = 'CREATE' | 'EDIT';

const SPECIALTY_LABEL: Record<Specialty, string> = {
  GENERAL_MEDICINE: 'Médecine générale',
  CARDIOLOGY: 'Cardiologie',
  DERMATOLOGY: 'Dermatologie',
  PEDIATRICS: 'Pédiatrie',
  GYNECOLOGY: 'Gynécologie',
  DENTISTRY: 'Dentisterie',
  OPHTHALMOLOGY: 'Ophtalmologie',
  PSYCHIATRY: 'Psychiatrie',
  ORTHOPEDICS: 'Orthopédie',
  OTHER: 'Autre'
};

const CURRENCY_LABEL: Record<Currency, string> = {
  XOF: 'F CFA (XOF)',
  XAF: 'F CFA (XAF)',
  EUR: 'Euro (€)',
  USD: 'Dollar US ($)'
};

/**
 * Page formulaire médecin — gère création ET édition.
 *
 * <h3>Détection du mode</h3>
 * <ul>
 *   <li>Route {@code /admin/staff/new} → mode CREATE (form vide)</li>
 *   <li>Route {@code /admin/staff/:id/edit} → mode EDIT (form pré-rempli via
 *       {@code facade.loadDetail()})</li>
 * </ul>
 *
 * <h3>Soumission</h3>
 * <ul>
 *   <li>CREATE → {@code facade.create()} puis nav vers le détail du médecin créé</li>
 *   <li>EDIT → {@code facade.update()} puis nav vers le détail</li>
 * </ul>
 *
 * <h3>Champs spécifiques</h3>
 * <ul>
 *   <li><b>licenseNumber</b> : généré côté serveur — absent du form en CREATE,
 *       affiché en read-only (mat-form-field disabled) en EDIT</li>
 *   <li><b>status</b> : volontairement <b>non éditable</b> via ce form. Les
 *       transitions ({@code putOnLeave}, {@code retire}, {@code reactivate})
 *       passent par les dialogs dédiés depuis la page détail (9.7d) — c'est
 *       le workflow officiel. On affiche le statut courant + lien vers la page
 *       détail pour orienter l'admin.</li>
 *   <li><b>consultationFee + currency</b> : devise par défaut XOF (Sénégal)</li>
 * </ul>
 *
 * <h3>Permissions</h3>
 * Module entier ADMIN-only (cf. roleGuard sur {@code /admin}).
 *
 * <h3>Optimistic locking</h3>
 * 409 Conflict → message clair + bouton "Recharger" qui re-fetch le médecin
 * et écrase les modifications locales.
 */
@Component({
  selector: 'tm-doctor-form-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    DoctorStatusBadgeComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="doctor-form-page">
      <tm-page-header
        [title]="mode() === 'CREATE' ? 'Ajouter un médecin' : 'Modifier le médecin'"
        [subtitle]="subtitle()"
      />

      @if (loadingDetail()) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement du médecin…</p>
        </div>
      } @else if (loadDetailError()) {
        <div class="error-banner" role="alert">
          <span class="material-icons-round">error_outline</span>
          <p>{{ loadDetailError() }}</p>
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

        <form [formGroup]="form" (ngSubmit)="onSubmit()" class="doctor-form">
          <!-- ─── Identité ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">person</span>
              Identité
            </legend>
            <div class="form-grid">
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
            </div>
          </fieldset>

          <!-- ─── Profession ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">medical_services</span>
              Profession
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Spécialité *</mat-label>
                <mat-select formControlName="specialty">
                  @for (key of specialtyKeys; track key) {
                    <mat-option [value]="key">{{ specialtyLabel(key) }}</mat-option>
                  }
                </mat-select>
                @if (showError('specialty')) {
                  <mat-error>Champ obligatoire</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Années d'expérience</mat-label>
                <input
                  matInput
                  type="number"
                  min="0"
                  max="70"
                  formControlName="yearsOfExperience"
                  placeholder="0–70"
                />
                @if (showError('yearsOfExperience')) {
                  <mat-error>{{ errorOf('yearsOfExperience') }}</mat-error>
                }
              </mat-form-field>

              @if (mode() === 'EDIT') {
                <mat-form-field appearance="outline" class="span-2 license-field">
                  <mat-label>N° licence (généré)</mat-label>
                  <input matInput [value]="licenseNumber()" disabled />
                  <mat-hint>Identifiant attribué par le serveur — non modifiable.</mat-hint>
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
                <mat-label>Email</mat-label>
                <input matInput type="email" formControlName="email" maxlength="100" />
                @if (showError('email')) {
                  <mat-error>{{ errorOf('email') }}</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Téléphone</mat-label>
                <input
                  matInput
                  formControlName="phone"
                  maxlength="20"
                  placeholder="+221 77 123 45 67"
                />
              </mat-form-field>

              <mat-form-field appearance="outline" class="span-2">
                <mat-label>Adresse du cabinet</mat-label>
                <input
                  matInput
                  formControlName="officeAddress"
                  maxlength="500"
                  placeholder="123 Avenue Cheikh Anta Diop, Dakar"
                />
              </mat-form-field>
            </div>
          </fieldset>

          <!-- ─── Tarification ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">payments</span>
              Tarification
            </legend>
            <div class="form-grid">
              <mat-form-field appearance="outline">
                <mat-label>Tarif consultation</mat-label>
                <input
                  matInput
                  type="number"
                  min="0"
                  step="500"
                  formControlName="consultationFee"
                  placeholder="15000"
                />
                @if (showError('consultationFee')) {
                  <mat-error>{{ errorOf('consultationFee') }}</mat-error>
                }
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Devise</mat-label>
                <mat-select formControlName="consultationFeeCurrency">
                  @for (key of currencyKeys; track key) {
                    <mat-option [value]="key">{{ currencyLabel(key) }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </div>
          </fieldset>

          <!-- ─── Bio ─── -->
          <fieldset class="form-section">
            <legend>
              <span class="material-icons-round">description</span>
              Présentation
            </legend>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Biographie</mat-label>
              <textarea
                matInput
                formControlName="bio"
                rows="4"
                maxlength="2000"
                placeholder="Parcours, formations, langues parlées, etc. — affiché aux patients."
              ></textarea>
              @if (showError('bio')) {
                <mat-error>{{ errorOf('bio') }}</mat-error>
              }
            </mat-form-field>
          </fieldset>

          <!-- ─── Statut (info en EDIT seulement) ─── -->
          @if (mode() === 'EDIT' && currentStatus(); as st) {
            <aside class="card status-info-card">
              <div class="status-info-row">
                <div class="status-info-text">
                  <p class="status-info-title">
                    <span class="material-icons-round">info</span>
                    Statut courant
                  </p>
                  <p class="status-info-help text-muted">
                    Le statut se modifie via les actions dédiées sur la page
                    détail (Mettre en congé / Retraiter / Réactiver) — pas
                    depuis ce formulaire, pour préserver le workflow.
                  </p>
                </div>
                <div class="status-info-actions">
                  <tm-doctor-status-badge [status]="st" [showIcon]="true" />
                  <button type="button" class="btn btn-link" (click)="goToDetail()">
                    Modifier le statut →
                  </button>
                </div>
              </div>
            </aside>
          }

          <!-- ─── Actions ─── -->
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
              {{ mode() === 'CREATE' ? 'Ajouter le médecin' : 'Enregistrer' }}
            </button>
          </div>
        </form>
      }
    </div>
  `,
  styles: [
    `
      .doctor-form-page {
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
        animation: tm-doctor-form-spin 0.9s linear infinite;
      }
      @keyframes tm-doctor-form-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .doctor-form {
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
      }
      @media (max-width: 700px) {
        .form-grid {
          grid-template-columns: 1fr;
        }
      }
      .span-2 {
        grid-column: span 2;
      }
      @media (max-width: 700px) {
        .span-2 {
          grid-column: span 1;
        }
      }
      .full-width {
        width: 100%;
      }
      .license-field {
        font-family: var(--font-mono, ui-monospace, monospace);
      }

      /* Statut info card (EDIT) */
      .status-info-card {
        background: rgba(41, 99, 176, 0.04);
        border: 1px solid rgba(41, 99, 176, 0.2);
        border-radius: var(--radius);
        padding: 16px 20px;
      }
      .status-info-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
      }
      .status-info-text {
        flex: 1;
        min-width: 280px;
      }
      .status-info-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-weight: 600;
        margin: 0 0 4px;
      }
      .status-info-title .material-icons-round {
        font-size: 18px;
        color: var(--color-primary, #2963b0);
      }
      .status-info-help {
        margin: 0;
        font-size: 13px;
        line-height: 1.5;
      }
      .status-info-actions {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-shrink: 0;
      }
      .status-info-actions .btn-link {
        background: none;
        border: none;
        color: var(--color-primary, #2963b0);
        cursor: pointer;
        text-decoration: underline;
        font-size: 13px;
        font-weight: 600;
        padding: 0;
      }
      .text-muted {
        color: var(--color-text-muted);
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        padding: 16px 0;
      }
      .form-actions .btn {
        min-width: 160px;
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .form-actions .spin {
        font-size: 18px;
      }

      ::ng-deep .doctor-form .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class DoctorFormPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly facade = inject(DoctorFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  /** ID médecin en mode EDIT, undefined en mode CREATE. */
  private doctorId: number | undefined;

  /** Version JPA — utilisée pour optimistic locking au prochain submit. */
  private doctorVersion: number | undefined;

  protected readonly mode = signal<FormMode>('CREATE');
  protected readonly saving = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly specialtyKeys: Specialty[] = [
    'GENERAL_MEDICINE',
    'CARDIOLOGY',
    'DERMATOLOGY',
    'PEDIATRICS',
    'GYNECOLOGY',
    'DENTISTRY',
    'OPHTHALMOLOGY',
    'PSYCHIATRY',
    'ORTHOPEDICS',
    'OTHER'
  ];
  protected readonly currencyKeys: Currency[] = ['XOF', 'XAF', 'EUR', 'USD'];

  /**
   * Loading dérivé de facade.detail() en mode EDIT uniquement.
   *
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
  protected readonly isConflict = computed(() => {
    const err = this.submitError()?.toLowerCase() ?? '';
    return err.includes('conflit') || err.includes('transition');
  });

  protected readonly subtitle = computed(() =>
    this.mode() === 'CREATE'
      ? 'Renseignez les informations du nouveau médecin'
      : `Mise à jour du médecin #${this.doctorId ?? ''}`
  );

  /** N° licence affiché en read-only en mode EDIT — vient de la facade. */
  protected readonly licenseNumber = computed(
    () => this.facade.detail().doctor?.licenseNumber ?? ''
  );

  /** Statut courant — affiché dans la carte info en EDIT. */
  protected readonly currentStatus = computed(
    () => this.facade.detail().doctor?.status ?? null
  );

  protected readonly form: FormGroup = this.fb.group({
    lastName: this.fb.control<string>('', [Validators.required, Validators.maxLength(100)]),
    firstName: this.fb.control<string>('', [Validators.required, Validators.maxLength(100)]),
    specialty: this.fb.control<Specialty | null>(null, Validators.required),
    yearsOfExperience: this.fb.control<number | null>(null, [
      Validators.min(0),
      Validators.max(70)
    ]),
    email: this.fb.control<string>('', [Validators.email, Validators.maxLength(100)]),
    phone: this.fb.control<string>('', [Validators.maxLength(20)]),
    officeAddress: this.fb.control<string>('', [Validators.maxLength(500)]),
    consultationFee: this.fb.control<number | null>(null, [Validators.min(0)]),
    consultationFeeCurrency: this.fb.control<Currency | null>('XOF'),
    bio: this.fb.control<string>('', [Validators.maxLength(2000)])
  });

  /**
   * Effect qui patche le form quand le détail médecin arrive (mode EDIT).
   *
   * <p>L'effect ne fait QUE des opérations non-signal (form.patchValue,
   * affectation de doctorVersion). Le state UI (loading/error) est dérivé
   * en {@link computed} ci-dessus — pas d'écriture de signal dans l'effect.
   */
  private readonly _detailPatchEffect = effect(() => {
    const detail = this.facade.detail();
    if (this.mode() === 'EDIT' && detail.doctor) {
      this.doctorVersion = detail.doctor.version;
      this.form.patchValue(
        {
          lastName: detail.doctor.lastName,
          firstName: detail.doctor.firstName,
          specialty: detail.doctor.specialty,
          yearsOfExperience: detail.doctor.yearsOfExperience ?? null,
          email: detail.doctor.email ?? '',
          phone: detail.doctor.phone ?? '',
          officeAddress: detail.doctor.officeAddress ?? '',
          consultationFee: detail.doctor.consultationFee ?? null,
          consultationFeeCurrency: detail.doctor.consultationFeeCurrency ?? 'XOF',
          bio: detail.doctor.bio ?? ''
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
          void this.router.navigate(['/admin/staff']);
          return;
        }
        this.doctorId = id;
        this.mode.set('EDIT');
        this.facade.loadDetail(id);
      } else {
        // Mode création
        this.mode.set('CREATE');
        this.doctorId = undefined;
        this.doctorVersion = undefined;
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
          void this.router.navigate(['/admin/staff', created.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    } else {
      // EDIT
      if (this.doctorId === undefined) return;
      const request = this.toUpdateRequest();
      this.facade.update(this.doctorId, request).subscribe({
        next: (updated) => {
          this.saving.set(false);
          void this.router.navigate(['/admin/staff', updated.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    }
  }

  onCancel(): void {
    if (this.mode() === 'EDIT' && this.doctorId !== undefined) {
      void this.router.navigate(['/admin/staff', this.doctorId]);
    } else {
      void this.router.navigate(['/admin/staff']);
    }
  }

  goBackToList(): void {
    void this.router.navigate(['/admin/staff']);
  }

  goToDetail(): void {
    if (this.doctorId !== undefined) {
      void this.router.navigate(['/admin/staff', this.doctorId]);
    }
  }

  /** Recharge le médecin depuis le serveur (utile après 409). */
  reloadFromServer(): void {
    if (this.doctorId !== undefined) {
      this.submitError.set(null);
      this.facade.loadDetail(this.doctorId);
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
    if (c.errors['min']) return `Valeur minimale : ${c.errors['min'].min}`;
    if (c.errors['max']) return `Valeur maximale : ${c.errors['max'].max}`;
    return 'Valeur invalide';
  }

  protected specialtyLabel(s: Specialty): string {
    return SPECIALTY_LABEL[s] ?? s;
  }

  protected currencyLabel(c: Currency): string {
    return CURRENCY_LABEL[c] ?? c;
  }

  // ─── Construction des DTOs ───

  /**
   * Mode CREATE — construit un {@link CreateDoctorRequest}.
   * Le {@code licenseNumber} est généré côté serveur — pas dans le request.
   * Le {@code status} n'est pas envoyé — défaut serveur ACTIVE.
   */
  private toCreateRequest(): CreateDoctorRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    return {
      lastName: (v['lastName'] as string).trim(),
      firstName: (v['firstName'] as string).trim(),
      specialty: v['specialty'] as Specialty,
      ...this.optionalString(v, 'email'),
      ...this.optionalString(v, 'phone'),
      ...this.optionalString(v, 'officeAddress'),
      ...this.optionalString(v, 'bio'),
      ...this.optionalNumber(v, 'yearsOfExperience'),
      ...this.optionalNumber(v, 'consultationFee'),
      ...(v['consultationFeeCurrency']
        ? { consultationFeeCurrency: v['consultationFeeCurrency'] as Currency }
        : {})
    };
  }

  /**
   * Mode EDIT — construit un {@link UpdateDoctorRequest}.
   * Le champ {@code status} n'est <b>pas inclus</b> — la transition d'état
   * passe par les endpoints dédiés ({@code putOnLeave}, {@code retire},
   * {@code reactivate}).
   */
  private toUpdateRequest(): UpdateDoctorRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    return {
      lastName: (v['lastName'] as string).trim(),
      firstName: (v['firstName'] as string).trim(),
      specialty: v['specialty'] as Specialty,
      email: this.normalizeOrEmpty(v['email']),
      phone: this.normalizeOrEmpty(v['phone']),
      officeAddress: this.normalizeOrEmpty(v['officeAddress']),
      bio: this.normalizeOrEmpty(v['bio']),
      yearsOfExperience: this.toNumberOrUndefined(v['yearsOfExperience']),
      consultationFee: this.toNumberOrUndefined(v['consultationFee']),
      consultationFeeCurrency: (v['consultationFeeCurrency'] as Currency) ?? undefined
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

  /** Renvoie {key: number} si valeur convertible, sinon objet vide. */
  private optionalNumber(
    v: Record<string, unknown>,
    key: string
  ): Record<string, number> {
    const n = this.toNumberOrUndefined(v[key]);
    return n != null ? { [key]: n } : {};
  }

  private toNumberOrUndefined(raw: unknown): number | undefined {
    if (raw === null || raw === undefined || raw === '') return undefined;
    const n = Number(raw);
    return Number.isNaN(n) ? undefined : n;
  }

  /** Convertit "" en "" et trim sinon (cohérent avec patient form). */
  private normalizeOrEmpty(raw: unknown): string | undefined {
    if (typeof raw !== 'string') return undefined;
    const trimmed = raw.trim();
    return trimmed === '' ? '' : trimmed;
  }
}
