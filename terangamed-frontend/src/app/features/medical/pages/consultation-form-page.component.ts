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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatButtonModule } from '@angular/material/button';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { fr } from 'date-fns/locale';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { CurrentDoctorService } from '@core/auth/current-doctor.service';
import { MedicalRecordApi } from '@api/medical-record.api';
import { PatientApi } from '@api/patient.api';
import {
  CreateConsultationRequest,
  MedicalRecordDto,
  UpdateConsultationRequest,
  VitalSignsDto
} from '@api/models/medical-record.model';
import { MedicalRecordFacade } from '../medical-record.facade';

type FormMode = 'CREATE' | 'EDIT';

/**
 * Page formulaire consultation — gère création ET édition.
 *
 * <h3>Détection du mode</h3>
 * <ul>
 *   <li>{@code /consultations/new?patientId=:id[&appointmentId=:id]} → CREATE</li>
 *   <li>{@code /consultations/:id/edit} → EDIT (uniquement si DRAFT — sinon redirige détail)</li>
 * </ul>
 *
 * <h3>MedicalRecordId résolu en CREATE</h3>
 * Le patient ID arrive via query param. On résout son MedicalRecord via API
 * (si pas de record → redirection vers la création de dossier).
 *
 * <h3>Médecin auteur en CREATE</h3>
 * Le backend résout l'auteur depuis le claim {@code sub} du JWT
 * (mapping {@code doctor.keycloak_subject}). Le front utilise quand même
 * {@link CurrentDoctorService} pour pré-valider que le compte est bien lié
 * et afficher une erreur claire avant submit si ce n'est pas le cas.
 *
 * <h3>Sections form</h3>
 * 1. Date + motif (obligatoires)<br>
 * 2. Signes vitaux (expandable, tous optionnels)<br>
 * 3. Examen clinique (textarea)<br>
 * 4. Diagnostic + observations + recommandations + prochain RDV<br>
 *
 * <h3>Actions</h3>
 * <ul>
 *   <li><b>Enregistrer brouillon</b> : create/update DRAFT, retour détail</li>
 *   <li><b>Annuler</b> : retour</li>
 * </ul>
 * (La signature se fait depuis la page détail.)
 */
@Component({
  selector: 'tm-consultation-form-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatExpansionModule,
    MatButtonModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    provideDateFnsAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: fr }
  ],
  template: `
    <div class="consultation-form-page">
      <tm-page-header
        [title]="mode() === 'CREATE' ? 'Nouvelle consultation' : 'Modifier la consultation'"
        [subtitle]="subtitle()"
      />

      @if (loading()) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement…</p>
        </div>
      } @else {
        @if (loadError(); as err) {
          <div class="error-banner" role="alert">
            <span class="material-icons-round">error_outline</span>
            <p>{{ err }}</p>
            <button type="button" class="btn btn-link" (click)="goBack()">
              Retour
            </button>
          </div>
        } @else {
          @if (submitError(); as err) {
            <div class="error-banner" role="alert">
              <span class="material-icons-round">error_outline</span>
              <p>{{ err }}</p>
            </div>
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="consultation-form">
            <!-- ─── Date & motif ─── -->
            <fieldset class="form-section">
              <legend>
                <span class="material-icons-round">event</span>
                Informations
              </legend>
              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Date de consultation *</mat-label>
                  <input
                    matInput
                    [matDatepicker]="datePicker"
                    formControlName="consultationDate"
                  />
                  <mat-datepicker-toggle matIconSuffix [for]="datePicker" />
                  <mat-datepicker #datePicker />
                  @if (showError('consultationDate')) {
                    <mat-error>Date obligatoire</mat-error>
                  }
                </mat-form-field>

                <mat-form-field appearance="outline" class="span-2">
                  <mat-label>Motif *</mat-label>
                  <input
                    matInput
                    formControlName="motif"
                    maxlength="500"
                    placeholder="Ex: Bilan annuel, douleurs lombaires…"
                  />
                  @if (showError('motif')) {
                    <mat-error>{{ errorOf('motif') }}</mat-error>
                  }
                </mat-form-field>
              </div>
            </fieldset>

            <!-- ─── Signes vitaux (expandable) ─── -->
            <mat-accordion>
              <mat-expansion-panel formGroupName="vitalSigns">
                <mat-expansion-panel-header>
                  <mat-panel-title>
                    <span class="material-icons-round">monitor_heart</span>
                    Signes vitaux
                  </mat-panel-title>
                  <mat-panel-description>
                    Tous les champs sont optionnels
                  </mat-panel-description>
                </mat-expansion-panel-header>

                <div class="form-grid vitals-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>Poids (kg)</mat-label>
                    <input matInput type="number" formControlName="weightKg" step="0.1" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Taille (cm)</mat-label>
                    <input matInput type="number" formControlName="heightCm" step="0.1" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Température (°C)</mat-label>
                    <input matInput type="number" formControlName="temperatureCelsius" step="0.1" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Fréquence cardiaque (bpm)</mat-label>
                    <input matInput type="number" formControlName="heartRateBpm" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Fréquence respiratoire (/min)</mat-label>
                    <input matInput type="number" formControlName="respiratoryRateBpm" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Saturation O₂ (%)</mat-label>
                    <input matInput type="number" formControlName="oxygenSaturationPercent" min="0" max="100" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Tension systolique (mmHg)</mat-label>
                    <input matInput type="number" formControlName="bloodPressureSystolic" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Tension diastolique (mmHg)</mat-label>
                    <input matInput type="number" formControlName="bloodPressureDiastolic" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Glycémie (mg/dL)</mat-label>
                    <input matInput type="number" formControlName="bloodGlucoseMgDl" min="0" />
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="span-2">
                    <mat-label>Notes signes vitaux</mat-label>
                    <input matInput formControlName="notes" />
                  </mat-form-field>
                </div>
              </mat-expansion-panel>
            </mat-accordion>

            <!-- ─── Examen clinique ─── -->
            <fieldset class="form-section">
              <legend>
                <span class="material-icons-round">stethoscope</span>
                Examen clinique
              </legend>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Notes d'examen</mat-label>
                <textarea
                  matInput
                  formControlName="examenCliniqueNotes"
                  rows="4"
                  placeholder="Description de l'examen physique, observations cliniques…"
                ></textarea>
              </mat-form-field>
            </fieldset>

            <!-- ─── Diagnostic & suite ─── -->
            <fieldset class="form-section">
              <legend>
                <span class="material-icons-round">medical_information</span>
                Diagnostic et suivi
              </legend>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Diagnostic</mat-label>
                <textarea
                  matInput
                  formControlName="diagnostic"
                  rows="3"
                  placeholder="Diagnostic clinique, hypothèses…"
                ></textarea>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Observations</mat-label>
                <textarea
                  matInput
                  formControlName="observations"
                  rows="3"
                  placeholder="Observations complémentaires…"
                ></textarea>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Recommandations au patient</mat-label>
                <textarea
                  matInput
                  formControlName="recommandations"
                  rows="3"
                  placeholder="Conseils, prescriptions hygiéno-diététiques…"
                ></textarea>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Prochain rendez-vous suggéré</mat-label>
                <input
                  matInput
                  [matDatepicker]="nextPicker"
                  formControlName="nextAppointmentSuggested"
                />
                <mat-datepicker-toggle matIconSuffix [for]="nextPicker" />
                <mat-datepicker #nextPicker />
              </mat-form-field>
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
                  <span class="material-icons-round">save</span>
                }
                Enregistrer le brouillon
              </button>
            </div>
            <p class="text-muted form-info">
              <span class="material-icons-round">info</span>
              La consultation reste modifiable tant qu'elle n'est pas signée.
              La signature se fait depuis la page détail.
            </p>
          </form>
        }
      }
    </div>
  `,
  styles: [
    `
      .consultation-form-page {
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
      }
      .spin {
        animation: tm-form-spin 0.9s linear infinite;
      }
      @keyframes tm-form-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .consultation-form {
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
      .vitals-grid {
        grid-template-columns: 1fr 1fr 1fr;
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
      .full-width {
        width: 100%;
      }

      mat-accordion {
        display: block;
      }
      mat-expansion-panel {
        background: var(--color-surface);
        box-shadow: var(--shadow);
        border-radius: var(--radius);
      }
      mat-panel-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
      }
      mat-panel-title .material-icons-round {
        color: var(--color-primary, #2963b0);
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        padding: 16px 0 0;
      }
      .form-actions .btn {
        min-width: 180px;
      }
      .form-actions .spin {
        font-size: 18px;
      }
      .form-info {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        margin: 0;
        padding: 0 4px;
      }
      .form-info .material-icons-round {
        font-size: 16px;
      }
      .text-muted {
        color: var(--color-text-muted);
      }

      ::ng-deep .consultation-form .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class ConsultationFormPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly api = inject(MedicalRecordApi);
  private readonly patientApi = inject(PatientApi);
  private readonly currentDoctor = inject(CurrentDoctorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  protected readonly mode = signal<FormMode>('CREATE');
  protected readonly saving = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);

  /** Pour mode CREATE — résolus depuis ?patientId. Le médecin auteur est
   *  identifié côté backend depuis le JWT, plus besoin de le passer ici. */
  private medicalRecordId: number | undefined;
  private patientId: number | undefined;
  private appointmentId: number | undefined;

  /** Pour mode EDIT — l'id de la consultation. */
  private consultationId: number | undefined;

  protected readonly subtitle = computed(() =>
    this.mode() === 'CREATE'
      ? 'Saisie d\'une nouvelle consultation'
      : `Modification du brouillon #${this.consultationId ?? ''}`
  );

  protected readonly form: FormGroup = this.fb.group({
    consultationDate: this.fb.control<Date | null>(new Date(), Validators.required),
    motif: this.fb.control<string>('', [Validators.required, Validators.maxLength(500)]),
    vitalSigns: this.fb.group({
      weightKg: this.fb.control<number | null>(null),
      heightCm: this.fb.control<number | null>(null),
      temperatureCelsius: this.fb.control<number | null>(null),
      heartRateBpm: this.fb.control<number | null>(null),
      respiratoryRateBpm: this.fb.control<number | null>(null),
      bloodPressureSystolic: this.fb.control<number | null>(null),
      bloodPressureDiastolic: this.fb.control<number | null>(null),
      oxygenSaturationPercent: this.fb.control<number | null>(null),
      bloodGlucoseMgDl: this.fb.control<number | null>(null),
      notes: this.fb.control<string>('')
    }),
    examenCliniqueNotes: this.fb.control<string>(''),
    diagnostic: this.fb.control<string>(''),
    observations: this.fb.control<string>(''),
    recommandations: this.fb.control<string>(''),
    nextAppointmentSuggested: this.fb.control<Date | null>(null)
  });

  /**
   * Effect qui patche le form en mode EDIT depuis facade.consultationState().
   * Pas d'écriture de signal ici — uniquement form.patchValue.
   */
  private readonly _detailPatchEffect = effect(() => {
    const state = this.facade.consultationState();
    if (this.mode() === 'EDIT' && state.consultation) {
      const c = state.consultation;
      // Si déjà signée → redirection détail (édition impossible)
      if (c.signed) {
        void this.router.navigate(['/consultations', c.id]);
        return;
      }
      this.form.patchValue(
        {
          consultationDate: c.consultationDate ? new Date(c.consultationDate) : null,
          motif: c.motif,
          vitalSigns: {
            weightKg: c.vitalSigns?.weightKg ?? null,
            heightCm: c.vitalSigns?.heightCm ?? null,
            temperatureCelsius: c.vitalSigns?.temperatureCelsius ?? null,
            heartRateBpm: c.vitalSigns?.heartRateBpm ?? null,
            respiratoryRateBpm: c.vitalSigns?.respiratoryRateBpm ?? null,
            bloodPressureSystolic: c.vitalSigns?.bloodPressureSystolic ?? null,
            bloodPressureDiastolic: c.vitalSigns?.bloodPressureDiastolic ?? null,
            oxygenSaturationPercent: c.vitalSigns?.oxygenSaturationPercent ?? null,
            bloodGlucoseMgDl: c.vitalSigns?.bloodGlucoseMgDl ?? null,
            notes: c.vitalSigns?.notes ?? ''
          },
          examenCliniqueNotes: c.examenCliniqueNotes ?? '',
          diagnostic: c.diagnostic ?? '',
          observations: c.observations ?? '',
          recommandations: c.recommandations ?? '',
          nextAppointmentSuggested: c.nextAppointmentSuggested
            ? new Date(c.nextAppointmentSuggested)
            : null
        },
        { emitEvent: false }
      );
    }
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      if (idParam) {
        // Mode EDIT
        const id = Number(idParam);
        if (Number.isNaN(id) || id <= 0) {
          void this.router.navigate(['/']);
          return;
        }
        this.consultationId = id;
        this.mode.set('EDIT');
        this.facade.loadConsultationDetail(id);
      } else {
        // Mode CREATE — résoudre patient + doctor
        this.mode.set('CREATE');
        this.consultationId = undefined;
        this.resolveCreateContext();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.facade.clearConsultation();
  }

  /**
   * Résout le contexte de création : patient (via query param) → medicalRecord,
   * et doctor (via CurrentDoctorService).
   */
  private resolveCreateContext(): void {
    const qp = this.route.snapshot.queryParamMap;
    const patientIdRaw = qp.get('patientId');
    const appointmentIdRaw = qp.get('appointmentId');

    if (!patientIdRaw || !/^\d+$/.test(patientIdRaw)) {
      this.loadError.set(
        'Patient non spécifié. Pour créer une consultation, utilisez le bouton depuis le dossier patient.'
      );
      return;
    }
    this.patientId = Number(patientIdRaw);
    if (appointmentIdRaw && /^\d+$/.test(appointmentIdRaw)) {
      this.appointmentId = Number(appointmentIdRaw);
    }

    this.loading.set(true);
    this.loadError.set(null);

    // Charger le MedicalRecord du patient (404 si pas de dossier → erreur)
    this.api.findRecordByPatientId(this.patientId).subscribe({
      next: (record: MedicalRecordDto) => {
        this.medicalRecordId = record.id;
        // Pré-vérifier que le DOCTOR connecté est bien lié à un profil Doctor.
        // Échec ici → UX claire avant submit, plutôt qu'un 409/404 backend après
        // remplissage du form. Le doctorId lui-même n'est pas utilisé : le
        // backend le résout depuis le JWT à la création.
        this.currentDoctor.resolve().subscribe({
          next: (doctor) => {
            this.loading.set(false);
            if (!doctor) {
              this.loadError.set(
                "Profil médecin introuvable pour le compte connecté. Demandez à un administrateur de lier votre compte Keycloak à un médecin (PUT /api/doctors/{id} avec keycloakSubject)."
              );
            }
          },
          error: () => {
            this.loading.set(false);
            this.loadError.set('Impossible de résoudre le profil médecin connecté.');
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        const status = err && typeof err === 'object' && 'status' in err ? err.status : 0;
        if (status === 404) {
          this.loadError.set(
            "Ce patient n'a pas de dossier médical. Créez-en un d'abord depuis la fiche patient."
          );
        } else {
          this.loadError.set('Erreur lors du chargement du dossier patient.');
        }
      }
    });
  }

  // ─── Soumission ───

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.submitError.set(null);

    if (this.mode() === 'CREATE') {
      if (this.medicalRecordId === undefined) {
        this.saving.set(false);
        this.submitError.set('Contexte invalide — impossible de créer la consultation.');
        return;
      }
      const request = this.toCreateRequest();
      this.facade.createConsultation(request).subscribe({
        next: (created) => {
          this.saving.set(false);
          void this.router.navigate(['/consultations', created.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    } else {
      if (this.consultationId === undefined) return;
      const request = this.toUpdateRequest();
      this.facade.updateConsultation(this.consultationId, request).subscribe({
        next: (updated) => {
          this.saving.set(false);
          void this.router.navigate(['/consultations', updated.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    }
  }

  onCancel(): void {
    if (this.mode() === 'EDIT' && this.consultationId !== undefined) {
      void this.router.navigate(['/consultations', this.consultationId]);
    } else if (this.patientId !== undefined) {
      void this.router.navigate(['/patients', this.patientId, 'medical-record']);
    } else {
      void this.router.navigate(['/']);
    }
  }

  goBack(): void {
    if (this.patientId !== undefined) {
      void this.router.navigate(['/patients', this.patientId, 'medical-record']);
    } else {
      void this.router.navigate(['/patients']);
    }
  }

  // ─── Helpers ───

  showError(controlName: string): boolean {
    const c = this.form.get(controlName);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  errorOf(controlName: string): string {
    const c = this.form.get(controlName);
    if (!c?.errors) return '';
    if (c.errors['required']) return 'Champ obligatoire';
    if (c.errors['maxlength']) {
      return `Maximum ${c.errors['maxlength'].requiredLength} caractères`;
    }
    return 'Valeur invalide';
  }

  // ─── Construction des DTOs ───

  private toCreateRequest(): CreateConsultationRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    const out: CreateConsultationRequest = {
      medicalRecordId: this.medicalRecordId!,
      // consultationDate côté backend = LocalDateTime → format YYYY-MM-DDTHH:MM:SS
      consultationDate: this.formatDateTime(v['consultationDate'] as Date),
      motif: (v['motif'] as string).trim()
    };
    if (this.appointmentId !== undefined) out.appointmentId = this.appointmentId;
    const vitals = this.toVitalSigns(v['vitalSigns']);
    if (vitals) out.vitalSigns = vitals;
    const examen = ((v['examenCliniqueNotes'] as string) || '').trim();
    if (examen) out.examenCliniqueNotes = examen;
    const diag = ((v['diagnostic'] as string) || '').trim();
    if (diag) out.diagnostic = diag;
    const obs = ((v['observations'] as string) || '').trim();
    if (obs) out.observations = obs;
    const reco = ((v['recommandations'] as string) || '').trim();
    if (reco) out.recommandations = reco;
    if (v['nextAppointmentSuggested'] instanceof Date) {
      out.nextAppointmentSuggested = this.formatDate(v['nextAppointmentSuggested'] as Date);
    }
    return out;
  }

  private toUpdateRequest(): UpdateConsultationRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    return {
      // LocalDateTime côté backend
      consultationDate: this.formatDateTime(v['consultationDate'] as Date),
      motif: (v['motif'] as string).trim(),
      vitalSigns: this.toVitalSigns(v['vitalSigns']) ?? undefined,
      examenCliniqueNotes: ((v['examenCliniqueNotes'] as string) || '').trim() || undefined,
      diagnostic: ((v['diagnostic'] as string) || '').trim() || undefined,
      observations: ((v['observations'] as string) || '').trim() || undefined,
      recommandations: ((v['recommandations'] as string) || '').trim() || undefined,
      nextAppointmentSuggested:
        v['nextAppointmentSuggested'] instanceof Date
          ? this.formatDate(v['nextAppointmentSuggested'] as Date)
          : undefined
    };
  }

  /**
   * Convertit le sous-form vital signs en DTO. Retourne null si tous les
   * champs sont vides — pour ne pas envoyer un objet inutile au backend.
   */
  private toVitalSigns(raw: unknown): VitalSignsDto | null {
    if (!raw || typeof raw !== 'object') return null;
    const v = raw as Record<string, unknown>;
    const result: VitalSignsDto = {};
    let hasAny = false;
    const numFields: (keyof VitalSignsDto)[] = [
      'weightKg',
      'heightCm',
      'temperatureCelsius',
      'heartRateBpm',
      'respiratoryRateBpm',
      'bloodPressureSystolic',
      'bloodPressureDiastolic',
      'oxygenSaturationPercent',
      'bloodGlucoseMgDl'
    ];
    for (const key of numFields) {
      const val = v[key];
      if (typeof val === 'number' && !Number.isNaN(val)) {
        (result as Record<string, unknown>)[key] = val;
        hasAny = true;
      }
    }
    if (typeof v['notes'] === 'string' && v['notes'].trim()) {
      result.notes = v['notes'].trim();
      hasAny = true;
    }
    return hasAny ? result : null;
  }

  /** Date → ISO YYYY-MM-DD (LocalDate côté backend Java). */
  private formatDate(d: Date): string {
    if (!(d instanceof Date) || Number.isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /**
   * Date → ISO YYYY-MM-DDTHH:MM:SS (LocalDateTime côté backend Java).
   *
   * <p><b>Important</b> : pas de toISOString() qui ajoute le suffixe 'Z' (UTC) —
   * Spring Boot {@code LocalDateTime} ne supporte pas le marqueur de timezone
   * et planterait avec DateTimeParseException. On construit donc manuellement
   * en heure locale, sans timezone.
   *
   * <p>Si l'utilisateur a sélectionné juste une date dans le datepicker,
   * l'heure résultante sera 00:00:00 (minuit local). Si on veut "maintenant"
   * pour une consultation en cours, on combine date sélectionnée + heure du
   * moment au submit.
   */
  private formatDateTime(d: Date): string {
    if (!(d instanceof Date) || Number.isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    // Si la date sélectionnée est aujourd'hui ET que l'heure est minuit
    // (= juste un click date sans modification time), on enrichit avec l'heure
    // courante pour rendre la timestamp réaliste.
    let hh = String(d.getHours()).padStart(2, '0');
    let mm = String(d.getMinutes()).padStart(2, '0');
    let ss = String(d.getSeconds()).padStart(2, '0');
    const today = new Date();
    const isToday =
      d.getFullYear() === today.getFullYear() &&
      d.getMonth() === today.getMonth() &&
      d.getDate() === today.getDate();
    if (isToday && d.getHours() === 0 && d.getMinutes() === 0 && d.getSeconds() === 0) {
      hh = String(today.getHours()).padStart(2, '0');
      mm = String(today.getMinutes()).padStart(2, '0');
      ss = String(today.getSeconds()).padStart(2, '0');
    }
    return `${y}-${m}-${day}T${hh}:${mm}:${ss}`;
  }
}
