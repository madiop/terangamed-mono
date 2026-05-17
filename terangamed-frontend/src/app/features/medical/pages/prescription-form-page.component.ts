import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AbstractControl,
  FormArray,
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
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { fr } from 'date-fns/locale';
import { Subject, of, forkJoin, Observable, concat, defer } from 'rxjs';
import { catchError, takeUntil, toArray } from 'rxjs/operators';
import { format, parseISO } from 'date-fns';
import {
  ConsultationDto,
  CreatePrescriptionLineRequest,
  CreatePrescriptionRequest,
  MedicationRoute,
  PrescriptionDto,
  PrescriptionLineDto,
  UpdatePrescriptionLineRequest,
  UpdatePrescriptionRequest
} from '@api/models/medical-record.model';
import { MedicalRecordApi } from '@api/medical-record.api';
import { MedicalRecordFacade } from '../medical-record.facade';

const ROUTE_LABEL: Record<MedicationRoute, string> = {
  ORAL: 'Orale',
  INJECTION: 'Injection',
  TOPICAL: 'Topique',
  INHALATION: 'Inhalation',
  OPHTHALMIC: 'Ophtalmique',
  NASAL: 'Nasale',
  RECTAL: 'Rectale',
  OTHER: 'Autre'
};

interface LineFormShape {
  id: FormControl<number | null>;
  medicationName: FormControl<string>;
  dosage: FormControl<string>;
  frequency: FormControl<string>;
  duration: FormControl<string>;
  route: FormControl<MedicationRoute | null>;
  quantity: FormControl<number | null>;
  instructions: FormControl<string>;
}

interface PrescriptionFormShape {
  validUntil: FormControl<Date | null>;
  generalInstructions: FormControl<string>;
  lines: FormArray<FormGroup<LineFormShape>>;
}

/**
 * Page édition prescription — `/consultations/:id/prescription`.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>CREATE</b> : pas d'ordonnance liée à la consultation → POST atomique
 *       avec toutes les lignes inline ({@link MedicalRecordFacade#createPrescription}).</li>
 *   <li><b>EDIT</b> : ordonnance existante → delta detection (PATCH méta +
 *       add/update/delete lignes granulaires en série).</li>
 * </ul>
 *
 * <h3>Read-only si consultation signée</h3>
 * Si {@code consultation.signed === true}, le form est désactivé et un banner
 * informe l'utilisateur que la consultation (donc son ordonnance) est verrouillée.
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>Au moins une ligne médicament (FormArray.length ≥ 1)</li>
 *   <li>Chaque ligne doit avoir un {@code medicationName} non vide</li>
 *   <li>Quantité ≥ 1 si renseignée</li>
 * </ul>
 *
 * <h3>Permissions</h3>
 * Accessible uniquement aux ADMIN/DOCTOR (cf. roleGuard sur la route).
 */
@Component({
  selector: 'tm-prescription-form-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatTooltipModule
  ],
  providers: [
    provideDateFnsAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: fr }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="prescription-form-page">
      @if (loading()) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement de la consultation…</p>
        </div>
      } @else if (loadError()) {
        <div class="error-state">
          <span class="material-icons-round">error_outline</span>
          <h2>{{ loadError() }}</h2>
          <button type="button" class="btn btn-outline" (click)="goBack()">
            Retour
          </button>
        </div>
      } @else {
        @if (consultation(); as c) {
          <!-- Header -->
          <header class="page-header">
            <button
              type="button"
              class="back-button"
              (click)="goBack()"
              aria-label="Retour à la consultation"
            >
              <span class="material-icons-round">arrow_back</span>
            </button>
            <div class="header-content">
              <h1 class="page-title">
                <span class="material-icons-round">receipt_long</span>
                @if (isEdit()) {
                  Modifier l'ordonnance
                  @if (prescription(); as p) {
                    <span class="prescription-num">— N° {{ p.prescriptionNumber }}</span>
                  }
                } @else {
                  Nouvelle ordonnance
                }
              </h1>
              <p class="page-subtitle text-muted">
                Consultation du {{ formatDateTime(c.consultationDate) }} — {{ c.motif }}
              </p>
            </div>
          </header>

          <!-- Banner consultation signée (read-only) -->
          @if (c.signed) {
            <div class="locked-banner" role="alert">
              <span class="material-icons-round">lock</span>
              <div>
                <strong>Consultation signée.</strong>
                L'ordonnance est verrouillée et ne peut plus être modifiée. Pour
                effectuer un changement, créez une nouvelle consultation.
              </div>
            </div>
          }

          <!-- Form -->
          <form [formGroup]="form" class="prescription-form">
            <!-- Section Métadonnées -->
            <section class="card form-section">
              <h2 class="section-title">
                <span class="material-icons-round">tune</span>
                Métadonnées de l'ordonnance
              </h2>

              <div class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Date de validité</mat-label>
                  <input
                    matInput
                    [matDatepicker]="validUntilPicker"
                    formControlName="validUntil"
                    [min]="today"
                    placeholder="Défaut : J+3 mois"
                  />
                  <mat-datepicker-toggle matIconSuffix [for]="validUntilPicker" />
                  <mat-datepicker #validUntilPicker />
                  <mat-hint>
                    Si vide, le serveur fixera la validité à 3 mois après création.
                  </mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="span-2">
                  <mat-label>Instructions générales (facultatif)</mat-label>
                  <textarea
                    matInput
                    formControlName="generalInstructions"
                    rows="3"
                    maxlength="2000"
                    placeholder="Recommandations générales destinées au patient…"
                  ></textarea>
                </mat-form-field>
              </div>
            </section>

            <!-- Section Lignes -->
            <section class="card form-section">
              <h2 class="section-title">
                <span class="material-icons-round">medication</span>
                Médicaments prescrits
                <span class="lines-count">{{ linesArray.length }}</span>
              </h2>

              @if (linesArray.length === 0) {
                <p class="text-muted no-content">
                  Aucun médicament ajouté. Cliquez sur « Ajouter un médicament »
                  pour commencer l'ordonnance.
                </p>
              } @else {
                <div class="lines-list" formArrayName="lines">
                  @for (line of linesArray.controls; track line; let i = $index) {
                    <div class="line-card" [formGroupName]="i">
                      <div class="line-header">
                        <span class="line-index">#{{ i + 1 }}</span>
                        @if (!c.signed) {
                          <button
                            type="button"
                            mat-icon-button
                            class="line-delete"
                            matTooltip="Supprimer cette ligne"
                            aria-label="Supprimer la ligne"
                            (click)="removeLine(i)"
                          >
                            <span class="material-icons-round">delete_outline</span>
                          </button>
                        }
                      </div>

                      <div class="form-grid">
                        <mat-form-field appearance="outline" class="span-2">
                          <mat-label>Médicament *</mat-label>
                          <input
                            matInput
                            formControlName="medicationName"
                            maxlength="300"
                            placeholder="Ex: Amoxicilline 500 mg"
                          />
                          @if (showLineError(i, 'medicationName')) {
                            <mat-error>{{ lineErrorOf(i, 'medicationName') }}</mat-error>
                          }
                        </mat-form-field>

                        <mat-form-field appearance="outline">
                          <mat-label>Posologie</mat-label>
                          <input
                            matInput
                            formControlName="dosage"
                            maxlength="200"
                            placeholder="Ex: 1 comprimé"
                          />
                        </mat-form-field>

                        <mat-form-field appearance="outline">
                          <mat-label>Fréquence</mat-label>
                          <input
                            matInput
                            formControlName="frequency"
                            maxlength="200"
                            placeholder="Ex: 3 fois par jour"
                          />
                        </mat-form-field>

                        <mat-form-field appearance="outline">
                          <mat-label>Durée</mat-label>
                          <input
                            matInput
                            formControlName="duration"
                            maxlength="200"
                            placeholder="Ex: 7 jours"
                          />
                        </mat-form-field>

                        <mat-form-field appearance="outline">
                          <mat-label>Voie d'administration</mat-label>
                          <mat-select formControlName="route">
                            <mat-option [value]="null">— Non spécifiée —</mat-option>
                            @for (key of routeKeys; track key) {
                              <mat-option [value]="key">{{ routeLabel(key) }}</mat-option>
                            }
                          </mat-select>
                        </mat-form-field>

                        <mat-form-field appearance="outline">
                          <mat-label>Quantité (boîtes)</mat-label>
                          <input
                            matInput
                            type="number"
                            min="1"
                            max="999"
                            formControlName="quantity"
                            placeholder="Ex: 1"
                          />
                          @if (showLineError(i, 'quantity')) {
                            <mat-error>{{ lineErrorOf(i, 'quantity') }}</mat-error>
                          }
                        </mat-form-field>

                        <mat-form-field appearance="outline" class="span-2">
                          <mat-label>Instructions spécifiques</mat-label>
                          <textarea
                            matInput
                            formControlName="instructions"
                            rows="2"
                            maxlength="1000"
                            placeholder="Ex: À prendre pendant les repas"
                          ></textarea>
                        </mat-form-field>
                      </div>
                    </div>
                  }
                </div>
              }

              @if (!c.signed) {
                <button
                  type="button"
                  class="btn btn-outline btn-add-line"
                  (click)="addLine()"
                >
                  <span class="material-icons-round">add</span>
                  Ajouter un médicament
                </button>
              }

              @if (showFormError('linesEmpty')) {
                <p class="form-level-error" role="alert">
                  Au moins une ligne médicament est requise pour créer une ordonnance.
                </p>
              }
            </section>

            <!-- Erreur facade -->
            @if (mutationError(); as err) {
              <div class="error-banner" role="alert">
                <span class="material-icons-round">error_outline</span>
                <p>{{ err }}</p>
              </div>
            }

            <!-- Footer actions -->
            @if (!c.signed) {
              <footer class="form-actions">
                <button
                  type="button"
                  class="btn btn-outline"
                  [disabled]="saving()"
                  (click)="goBack()"
                >
                  Annuler
                </button>
                <button
                  type="button"
                  class="btn btn-primary"
                  [disabled]="form.invalid || saving() || (isEdit() && !form.dirty && !linesDirty())"
                  (click)="onSubmit()"
                >
                  @if (saving()) {
                    <span class="material-icons-round spin">progress_activity</span>
                    Enregistrement…
                  } @else {
                    <span class="material-icons-round">save</span>
                    {{ isEdit() ? 'Enregistrer les modifications' : "Créer l'ordonnance" }}
                  }
                </button>
              </footer>
            }
          </form>
        }
      }
    </div>
  `,
  styles: [
    `
      .prescription-form-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .loading-state,
      .error-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        padding: 48px 24px;
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .loading-state .material-icons-round,
      .error-state .material-icons-round {
        font-size: 40px;
        color: var(--color-text-muted);
      }
      .error-state .material-icons-round {
        color: #ef4444;
      }
      .spin {
        animation: tm-px-spin 0.9s linear infinite;
      }
      @keyframes tm-px-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .page-header {
        display: flex;
        align-items: center;
        gap: 16px;
        background: var(--color-surface);
        padding: 20px 24px;
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .back-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: transparent;
        border: 1px solid var(--color-border, #e5e7eb);
        cursor: pointer;
        color: var(--color-text);
        flex-shrink: 0;
      }
      .back-button:hover {
        background: rgba(0, 0, 0, 0.04);
      }
      .header-content {
        flex: 1;
        min-width: 0;
      }
      .page-title {
        display: flex;
        align-items: center;
        gap: 10px;
        font-size: 20px;
        font-weight: 700;
        margin: 0;
      }
      .page-title .material-icons-round {
        color: var(--color-primary, #2963b0);
        font-size: 26px;
      }
      .prescription-num {
        font-weight: 500;
        color: var(--color-text-muted);
      }
      .page-subtitle {
        margin: 4px 0 0;
        font-size: 13px;
      }
      .text-muted {
        color: var(--color-text-muted);
      }

      .locked-banner {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        padding: 14px 16px;
        background: #fef3c7;
        border-left: 4px solid #f59e0b;
        border-radius: var(--radius);
        color: #78350f;
      }
      .locked-banner .material-icons-round {
        color: #d97706;
        font-size: 22px;
        flex-shrink: 0;
      }
      .locked-banner div {
        flex: 1;
        line-height: 1.5;
      }

      .prescription-form {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .card {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .form-section {
        padding: 20px 24px;
      }
      .section-title {
        display: flex;
        align-items: center;
        gap: 10px;
        margin: 0 0 16px;
        font-size: 16px;
        font-weight: 600;
      }
      .section-title .material-icons-round {
        color: var(--color-primary, #2963b0);
        font-size: 22px;
      }
      .lines-count {
        margin-left: 8px;
        padding: 1px 10px;
        height: 22px;
        border-radius: 11px;
        background: rgba(41, 99, 176, 0.1);
        color: var(--color-primary, #2963b0);
        font-size: 12px;
        font-weight: 600;
        display: inline-flex;
        align-items: center;
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
      .no-content {
        font-style: italic;
        margin: 0 0 16px;
      }

      /* Lignes */
      .lines-list {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .line-card {
        background: rgba(0, 0, 0, 0.02);
        border: 1px solid var(--color-border, #e5e7eb);
        border-radius: var(--radius);
        padding: 14px 16px;
      }
      .line-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
      }
      .line-index {
        font-weight: 700;
        color: var(--color-text-muted);
        font-size: 14px;
      }
      .line-delete {
        color: var(--color-text-muted);
      }
      .line-delete:hover {
        color: #b91c1c;
      }
      .line-delete .material-icons-round {
        font-size: 20px;
      }

      .btn-add-line {
        margin-top: 12px;
        align-self: flex-start;
      }

      .form-level-error {
        margin: 12px 0 0;
        padding: 10px 12px;
        background: #fef2f2;
        border-left: 3px solid #ef4444;
        border-radius: 6px;
        color: #991b1b;
        font-size: 13px;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 16px;
        background: #fee2e2;
        border-left: 4px solid #ef4444;
        border-radius: var(--radius);
        color: #991b1b;
      }
      .error-banner p {
        flex: 1;
        margin: 0;
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        padding: 16px 0 0;
      }
      .form-actions .btn {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .form-actions .material-icons-round {
        font-size: 18px;
      }
    `
  ]
})
export class PrescriptionFormPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly api = inject(MedicalRecordApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroy$ = new Subject<void>();

  /** ID consultation extrait de la route. */
  private consultationId: number | undefined;

  protected readonly today = new Date();
  protected readonly routeKeys: MedicationRoute[] = [
    'ORAL', 'INJECTION', 'TOPICAL', 'INHALATION', 'OPHTHALMIC', 'NASAL', 'RECTAL', 'OTHER'
  ];

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly consultation = signal<ConsultationDto | null>(null);
  protected readonly prescription = signal<PrescriptionDto | null>(null);

  protected readonly saving = computed(() => this.facade.mutation().saving);
  protected readonly mutationError = computed(() => this.facade.mutation().error);

  protected readonly isEdit = computed(() => this.prescription() !== null);

  /**
   * Snapshot des lignes initiales en mode EDIT — utilisé pour le delta detection.
   * Map id → DTO original.
   */
  private initialLines = new Map<number, PrescriptionLineDto>();
  /** Snapshot métadonnées initiales — pour détecter si PATCH metadata est nécessaire. */
  private initialMeta: { validUntil: string | null; generalInstructions: string | null } = {
    validUntil: null,
    generalInstructions: null
  };

  protected readonly form: FormGroup<PrescriptionFormShape> = this.fb.group<PrescriptionFormShape>({
    validUntil: this.fb.control<Date | null>(null),
    generalInstructions: this.fb.control<string>('', {
      nonNullable: true,
      validators: [Validators.maxLength(2000)]
    }),
    lines: this.fb.array<FormGroup<LineFormShape>>([], [this.minOneLineValidator])
  });

  protected get linesArray(): FormArray<FormGroup<LineFormShape>> {
    return this.form.controls.lines;
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      const id = idParam ? Number(idParam) : NaN;
      if (Number.isNaN(id) || id <= 0) {
        void this.router.navigate(['/dashboard']);
        return;
      }
      this.consultationId = id;
      this.loadData(id);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadData(consultationId: number): void {
    this.loading.set(true);
    this.loadError.set(null);

    // Chargement parallèle : consultation + prescription (404 absorbé)
    forkJoin({
      consultation: this.api.findConsultation(consultationId),
      prescription: this.api
        .findPrescriptionByConsultation(consultationId)
        .pipe(catchError(() => of<PrescriptionDto | null>(null)))
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ consultation, prescription }) => {
          this.consultation.set(consultation);
          this.prescription.set(prescription);
          this.populateForm(prescription);
          if (consultation.signed) {
            this.form.disable({ emitEvent: false });
          }
          this.loading.set(false);
        },
        error: (err) => {
          const status = err && typeof err === 'object' && 'status' in err ? err.status : 0;
          this.loadError.set(
            status === 404
              ? 'Consultation introuvable'
              : 'Erreur lors du chargement de la consultation'
          );
          this.loading.set(false);
        }
      });
  }

  /**
   * Pré-remplit le form en mode EDIT, ou démarre avec une ligne vide en CREATE.
   */
  private populateForm(p: PrescriptionDto | null): void {
    this.linesArray.clear();
    this.initialLines.clear();

    if (p) {
      // Mode EDIT
      this.initialMeta = {
        validUntil: p.validUntil ?? null,
        generalInstructions: p.generalInstructions ?? null
      };
      this.form.patchValue(
        {
          validUntil: p.validUntil ? parseISO(p.validUntil) : null,
          generalInstructions: p.generalInstructions ?? ''
        },
        { emitEvent: false }
      );
      for (const line of p.lines) {
        this.linesArray.push(this.buildLineGroup(line));
        this.initialLines.set(line.id, line);
      }
    } else {
      // Mode CREATE — une ligne vierge par défaut pour guider la saisie
      this.linesArray.push(this.buildLineGroup());
    }

    this.form.markAsPristine();
  }

  /** Construit un FormGroup ligne (vierge ou pré-rempli depuis un DTO). */
  private buildLineGroup(line?: PrescriptionLineDto): FormGroup<LineFormShape> {
    return this.fb.group<LineFormShape>({
      id: this.fb.control<number | null>(line?.id ?? null),
      medicationName: this.fb.control<string>(line?.medicationName ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(300)]
      }),
      dosage: this.fb.control<string>(line?.dosage ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(200)]
      }),
      frequency: this.fb.control<string>(line?.frequency ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(200)]
      }),
      duration: this.fb.control<string>(line?.duration ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(200)]
      }),
      route: this.fb.control<MedicationRoute | null>(line?.route ?? null),
      quantity: this.fb.control<number | null>(line?.quantity ?? null, [
        Validators.min(1),
        Validators.max(999)
      ]),
      instructions: this.fb.control<string>(line?.instructions ?? '', {
        nonNullable: true,
        validators: [Validators.maxLength(1000)]
      })
    });
  }

  /** Validateur form-level : au moins une ligne dans le FormArray. */
  private minOneLineValidator(ctrl: AbstractControl): { [k: string]: boolean } | null {
    const arr = ctrl as FormArray;
    return arr.length === 0 ? { linesEmpty: true } : null;
  }

  /**
   * Détection des modifications dans les lignes — compte les ajouts/suppressions/modifs.
   * Utilisé pour activer le bouton Enregistrer en EDIT (form.dirty couvre les méta,
   * mais pas suffisamment les FormArray dont on ne peut pas se fier au dirty agrégé).
   */
  protected linesDirty(): boolean {
    if (!this.isEdit()) return true;
    if (this.linesArray.length !== this.initialLines.size) return true;
    for (const line of this.linesArray.controls) {
      const id = line.controls.id.value;
      if (id === null) return true; // nouvelle ligne
      if (line.dirty) return true;
    }
    return false;
  }

  // ─── Actions FormArray ───

  protected addLine(): void {
    if (this.consultation()?.signed) return;
    this.linesArray.push(this.buildLineGroup());
    this.form.markAsDirty();
  }

  protected removeLine(index: number): void {
    if (this.consultation()?.signed) return;
    this.linesArray.removeAt(index);
    this.form.markAsDirty();
  }

  // ─── Validation visuelle ───

  protected showFormError(errorKey: string): boolean {
    return this.form.touched && !!this.form.errors?.[errorKey];
  }

  protected showLineError(index: number, controlName: keyof LineFormShape): boolean {
    const c = this.linesArray.at(index)?.get(controlName);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected lineErrorOf(index: number, controlName: keyof LineFormShape): string {
    const c = this.linesArray.at(index)?.get(controlName);
    if (!c?.errors) return '';
    if (c.errors['required']) return 'Champ obligatoire';
    if (c.errors['maxlength']) {
      return `Maximum ${c.errors['maxlength'].requiredLength} caractères`;
    }
    if (c.errors['min']) return `Valeur minimale : ${c.errors['min'].min}`;
    if (c.errors['max']) return `Valeur maximale : ${c.errors['max'].max}`;
    return 'Valeur invalide';
  }

  // ─── Submit ───

  protected onSubmit(): void {
    if (this.consultation()?.signed) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.consultationId === undefined) return;

    if (this.isEdit()) {
      this.submitEdit();
    } else {
      this.submitCreate();
    }
  }

  /** CREATE : POST atomique avec toutes les lignes inline. */
  private submitCreate(): void {
    const consultationId = this.consultationId!;
    const v = this.form.getRawValue();
    const request: CreatePrescriptionRequest = {
      validUntil: v.validUntil ? this.formatDate(v.validUntil) : undefined,
      generalInstructions: v.generalInstructions?.trim() || undefined,
      lines: this.linesArray.controls.map((line) => this.toCreateLine(line))
    };

    this.facade.createPrescription(consultationId, request).subscribe({
      next: () => this.goBack(),
      error: () => {
        /* erreur dans facade.mutation().error */
      }
    });
  }

  /**
   * EDIT : delta detection.
   *
   * <ol>
   *   <li>PATCH métadonnées si validUntil ou generalInstructions ont changé</li>
   *   <li>DELETE des lignes retirées du form (présentes dans initialLines mais
   *       plus dans le FormArray)</li>
   *   <li>POST des lignes ajoutées (id=null dans le FormArray)</li>
   *   <li>PATCH des lignes existantes modifiées</li>
   * </ol>
   *
   * <p>Les opérations sont enchaînées avec {@code concat} pour garantir l'ordre
   * et permettre l'arrêt sur première erreur (rollback impossible — l'utilisateur
   * verra l'erreur et pourra retry ; le serveur garde l'état partiel atteint).
   */
  private submitEdit(): void {
    const p = this.prescription();
    if (!p) return;
    const prescriptionId = p.id;

    const ops: Observable<unknown>[] = [];

    // 1. Métadonnées
    const v = this.form.getRawValue();
    const newValidUntil = v.validUntil ? this.formatDate(v.validUntil) : null;
    const newGeneralInstructions = v.generalInstructions?.trim() || null;
    if (
      newValidUntil !== this.initialMeta.validUntil ||
      newGeneralInstructions !== this.initialMeta.generalInstructions
    ) {
      const metaReq: UpdatePrescriptionRequest = {
        validUntil: newValidUntil ?? undefined,
        generalInstructions: newGeneralInstructions ?? undefined
      };
      ops.push(this.facade.updatePrescription(prescriptionId, metaReq));
    }

    // 2. Lignes supprimées (id présent dans initialLines mais absent du FormArray)
    const currentIds = new Set<number>();
    for (const ctrl of this.linesArray.controls) {
      const id = ctrl.controls.id.value;
      if (id !== null) currentIds.add(id);
    }
    for (const id of this.initialLines.keys()) {
      if (!currentIds.has(id)) {
        ops.push(this.facade.deletePrescriptionLine(prescriptionId, id));
      }
    }

    // 3. Lignes ajoutées (id null) et 4. lignes modifiées (id présent + dirty)
    for (const ctrl of this.linesArray.controls) {
      const id = ctrl.controls.id.value;
      if (id === null) {
        // Nouvelle ligne
        ops.push(
          this.facade.addPrescriptionLine(prescriptionId, this.toCreateLine(ctrl))
        );
      } else if (ctrl.dirty) {
        // Ligne modifiée
        const initial = this.initialLines.get(id);
        if (initial && this.lineDiffers(ctrl, initial)) {
          ops.push(
            this.facade.updatePrescriptionLine(
              prescriptionId,
              id,
              this.toUpdateLine(ctrl)
            )
          );
        }
      }
    }

    if (ops.length === 0) {
      // Rien à enregistrer (sécurité, ne devrait pas arriver vu le bouton)
      this.goBack();
      return;
    }

    // Exécution séquentielle pour garantir l'ordre et capter la 1re erreur
    concat(...ops.map((op) => defer(() => op)))
      .pipe(toArray())
      .subscribe({
        next: () => this.goBack(),
        error: () => {
          /* erreur dans facade.mutation().error — l'utilisateur peut retry */
        }
      });
  }

  // ─── Mappers form → DTO ───

  private toCreateLine(ctrl: FormGroup<LineFormShape>): CreatePrescriptionLineRequest {
    const v = ctrl.getRawValue();
    return {
      medicationName: v.medicationName.trim(),
      dosage: v.dosage?.trim() || undefined,
      frequency: v.frequency?.trim() || undefined,
      duration: v.duration?.trim() || undefined,
      route: v.route ?? undefined,
      quantity: v.quantity ?? undefined,
      instructions: v.instructions?.trim() || undefined
    };
  }

  private toUpdateLine(ctrl: FormGroup<LineFormShape>): UpdatePrescriptionLineRequest {
    // Update partial — on envoie tous les champs courants (le serveur gère le merge).
    const v = ctrl.getRawValue();
    return {
      medicationName: v.medicationName.trim(),
      dosage: v.dosage?.trim() || undefined,
      frequency: v.frequency?.trim() || undefined,
      duration: v.duration?.trim() || undefined,
      route: v.route ?? undefined,
      quantity: v.quantity ?? undefined,
      instructions: v.instructions?.trim() || undefined
    };
  }

  /** Compare une ligne form à son DTO initial — true si différence détectée. */
  private lineDiffers(ctrl: FormGroup<LineFormShape>, initial: PrescriptionLineDto): boolean {
    const v = ctrl.getRawValue();
    const norm = (s: string | null | undefined) => (s ?? '').trim();
    return (
      norm(v.medicationName) !== norm(initial.medicationName) ||
      norm(v.dosage) !== norm(initial.dosage) ||
      norm(v.frequency) !== norm(initial.frequency) ||
      norm(v.duration) !== norm(initial.duration) ||
      (v.route ?? null) !== (initial.route ?? null) ||
      (v.quantity ?? null) !== (initial.quantity ?? null) ||
      norm(v.instructions) !== norm(initial.instructions)
    );
  }

  // ─── Helpers d'affichage ───

  protected routeLabel(r: MedicationRoute): string {
    return ROUTE_LABEL[r] ?? r;
  }

  protected formatDateTime(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), "dd MMM yyyy 'à' HH:mm", { locale: fr });
    } catch {
      return iso;
    }
  }

  /** Date → ISO YYYY-MM-DD (LocalDate côté backend). */
  private formatDate(d: Date): string {
    if (!(d instanceof Date) || Number.isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  // ─── Navigation ───

  protected goBack(): void {
    if (this.consultationId !== undefined) {
      void this.router.navigate(['/consultations', this.consultationId]);
    } else {
      void this.router.navigate(['/dashboard']);
    }
  }
}
