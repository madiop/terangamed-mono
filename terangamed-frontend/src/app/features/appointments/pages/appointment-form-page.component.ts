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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { fr } from 'date-fns/locale';
import { format } from 'date-fns';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import {
  CreateAppointmentRequest,
  UpdateAppointmentRequest
} from '@api/models/appointment.model';
import { AppointmentFacade } from '../appointment.facade';
import { PatientPickerComponent } from '../components/patient-picker.component';
import { DoctorPickerComponent } from '../components/doctor-picker.component';

type FormMode = 'CREATE' | 'EDIT';

const DURATION_OPTIONS = [15, 30, 45, 60, 90];

/**
 * Page formulaire rendez-vous — gère création ET édition.
 *
 * <h3>Détection du mode</h3>
 * <ul>
 *   <li>{@code /appointments/new} → CREATE (form vide)</li>
 *   <li>{@code /appointments/:id/edit} → EDIT (pré-rempli via facade.loadDetail)</li>
 * </ul>
 *
 * <h3>Pré-remplissage via query params</h3>
 * En CREATE, on supporte {@code ?patientId=42} et {@code ?doctorId=5} pour
 * pré-sélectionner les pickers. Pratique depuis le dashboard ou la fiche
 * patient.
 *
 * <h3>Datetime</h3>
 * Date et heure sont 2 champs séparés (mat-datepicker + input type="time").
 * On combine en ISO string au moment du submit pour le DTO backend qui attend
 * un {@code OffsetDateTime}.
 *
 * <h3>Conflit de créneau</h3>
 * Le backend détecte les chevauchements (même médecin, fenêtre temporelle qui
 * recouvre) et renvoie 409. La facade traduit en message clair "Conflit —
 * créneau déjà occupé".
 */
@Component({
  selector: 'tm-appointment-form-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    PatientPickerComponent,
    DoctorPickerComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    provideDateFnsAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: fr }
  ],
  template: `
    <div class="appointment-form-page">
      <tm-page-header
        [title]="mode() === 'CREATE' ? 'Nouveau rendez-vous' : 'Modifier le rendez-vous'"
        [subtitle]="subtitle()"
      />

      @if (loadingDetail()) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement…</p>
        </div>
      } @else {
        @if (loadDetailError(); as err) {
          <div class="error-banner" role="alert">
            <span class="material-icons-round">error_outline</span>
            <p>{{ err }}</p>
            <button type="button" class="btn btn-link" (click)="goBack()">
              Retour à la liste
            </button>
          </div>
        } @else {
          @if (submitError(); as err) {
            <div class="error-banner" role="alert">
              <span class="material-icons-round">error_outline</span>
              <p>{{ err }}</p>
            </div>
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="appointment-form">
            <!-- ─── Participants ─── -->
            <fieldset class="form-section">
              <legend>
                <span class="material-icons-round">groups</span>
                Participants
              </legend>
              <div class="form-grid">
                <tm-patient-picker
                  formControlName="patientId"
                  [required]="true"
                  [showError]="showError('patientId')"
                />
                <tm-doctor-picker
                  formControlName="doctorId"
                  [required]="true"
                  [showError]="showError('doctorId')"
                />
              </div>
            </fieldset>

            <!-- ─── Date et heure ─── -->
            <fieldset class="form-section">
              <legend>
                <span class="material-icons-round">event</span>
                Date et heure
              </legend>
              <div class="form-grid datetime-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Date *</mat-label>
                  <input
                    matInput
                    [matDatepicker]="datePicker"
                    formControlName="date"
                    [min]="today"
                  />
                  <mat-datepicker-toggle matIconSuffix [for]="datePicker" />
                  <mat-datepicker #datePicker />
                  @if (showError('date')) {
                    <mat-error>{{ errorOf('date') }}</mat-error>
                  }
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Heure *</mat-label>
                  <input
                    matInput
                    type="time"
                    formControlName="time"
                    step="900"
                  />
                  @if (showError('time')) {
                    <mat-error>Heure obligatoire</mat-error>
                  }
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Durée *</mat-label>
                  <mat-select formControlName="durationMinutes">
                    @for (d of durationOptions; track d) {
                      <mat-option [value]="d">{{ d }} min</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </div>

              @if (form.get('date')?.value && form.get('time')?.value) {
                <p class="end-time-info">
                  <span class="material-icons-round">schedule</span>
                  Fin prévue : <strong>{{ computedEndTime() }}</strong>
                </p>
              }
            </fieldset>

            <!-- ─── Détails ─── -->
            <fieldset class="form-section">
              <legend>
                <span class="material-icons-round">description</span>
                Détails
              </legend>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="span-2">
                  <mat-label>Motif</mat-label>
                  <input matInput formControlName="reason" maxlength="200"
                         placeholder="Ex: Bilan annuel, contrôle tension…" />
                </mat-form-field>

                <mat-form-field appearance="outline" class="span-2">
                  <mat-label>Notes (visibles par le médecin)</mat-label>
                  <textarea matInput formControlName="notes" rows="3"
                            placeholder="Informations complémentaires…"></textarea>
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
                {{ mode() === 'CREATE' ? 'Créer le rendez-vous' : 'Enregistrer' }}
              </button>
            </div>
          </form>
        }
      }
    </div>
  `,
  styles: [
    `
      .appointment-form-page {
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
        animation: tm-form-spin 0.9s linear infinite;
      }
      @keyframes tm-form-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .appointment-form {
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
      .datetime-grid {
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

      .end-time-info {
        margin: 8px 0 0;
        font-size: 13px;
        color: var(--color-text-muted);
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .end-time-info .material-icons-round {
        font-size: 16px;
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        padding: 16px 0;
      }
      .form-actions .btn {
        min-width: 160px;
      }
      .form-actions .spin {
        font-size: 18px;
      }

      ::ng-deep .appointment-form .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class AppointmentFormPageComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly facade = inject(AppointmentFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  protected readonly mode = signal<FormMode>('CREATE');
  protected readonly saving = signal(false);
  protected readonly submitError = signal<string | null>(null);

  /** Loading & error dérivés de la facade pour le mode EDIT. */
  protected readonly loadingDetail = computed(
    () => this.mode() === 'EDIT' && this.facade.detail().loading
  );
  protected readonly loadDetailError = computed(() =>
    this.mode() === 'EDIT' ? this.facade.detail().error : null
  );

  protected readonly today = new Date();
  protected readonly durationOptions = DURATION_OPTIONS;

  private appointmentId: number | undefined;

  protected readonly subtitle = computed(() =>
    this.mode() === 'CREATE'
      ? 'Renseignez les informations du nouveau rendez-vous'
      : `Mise à jour du rendez-vous #${this.appointmentId ?? ''}`
  );

  protected readonly form: FormGroup = this.fb.group({
    patientId: this.fb.control<number | null>(null, Validators.required),
    doctorId: this.fb.control<number | null>(null, Validators.required),
    date: this.fb.control<Date | null>(null, Validators.required),
    time: this.fb.control<string>('', Validators.required),
    durationMinutes: this.fb.control<number>(30, Validators.required),
    reason: this.fb.control<string>('', [Validators.maxLength(200)]),
    notes: this.fb.control<string>('')
  });

  /**
   * Effect qui patche le form au mode EDIT quand le détail arrive.
   * Pas de signal write ici — uniquement form.patchValue (méthode standard).
   */
  private readonly _detailPatchEffect = effect(() => {
    const detail = this.facade.detail();
    if (this.mode() === 'EDIT' && detail.appointment) {
      const a = detail.appointment;
      const start = new Date(a.startTime);
      this.form.patchValue(
        {
          patientId: a.patientId,
          doctorId: a.doctorId,
          date: start,
          time: format(start, 'HH:mm'),
          durationMinutes: a.durationMinutes,
          reason: a.reason ?? '',
          notes: a.notes ?? ''
        },
        { emitEvent: false }
      );
    }
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      if (idParam) {
        const id = Number(idParam);
        if (Number.isNaN(id) || id <= 0) {
          void this.router.navigate(['/appointments']);
          return;
        }
        this.appointmentId = id;
        this.mode.set('EDIT');
        this.facade.loadDetail(id);
      } else {
        // CREATE — pré-sélection éventuelle via query params
        this.mode.set('CREATE');
        this.appointmentId = undefined;
        const qp = this.route.snapshot.queryParamMap;
        const patientId = qp.get('patientId');
        const doctorId = qp.get('doctorId');
        if (patientId && /^\d+$/.test(patientId)) {
          this.form.get('patientId')?.setValue(Number(patientId));
        }
        if (doctorId && /^\d+$/.test(doctorId)) {
          this.form.get('doctorId')?.setValue(Number(doctorId));
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.facade.clearDetail();
  }

  /** Calcule l'heure de fin pour affichage dans le form. */
  protected computedEndTime(): string {
    const date = this.form.get('date')?.value as Date | null;
    const time = this.form.get('time')?.value as string;
    const duration = this.form.get('durationMinutes')?.value as number;
    if (!date || !time || !duration) return '';
    const start = this.combineDateAndTime(date, time);
    if (!start) return '';
    const end = new Date(start.getTime() + duration * 60_000);
    return format(end, "HH'h'mm", { locale: fr });
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
      if (!request) {
        this.saving.set(false);
        this.submitError.set('Date/heure invalides');
        return;
      }
      this.facade.create(request).subscribe({
        next: (created) => {
          this.saving.set(false);
          void this.router.navigate(['/appointments', created.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    } else {
      if (this.appointmentId === undefined) return;
      const request = this.toUpdateRequest();
      this.facade.update(this.appointmentId, request).subscribe({
        next: (updated) => {
          this.saving.set(false);
          void this.router.navigate(['/appointments', updated.id]);
        },
        error: () => {
          this.saving.set(false);
          this.submitError.set(this.facade.mutation().error ?? 'Erreur inconnue');
        }
      });
    }
  }

  onCancel(): void {
    if (this.mode() === 'EDIT' && this.appointmentId !== undefined) {
      void this.router.navigate(['/appointments', this.appointmentId]);
    } else {
      this.goBack();
    }
  }

  goBack(): void {
    void this.router.navigate(['/appointments']);
  }

  // ─── Validation helpers ───

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

  private toCreateRequest(): CreateAppointmentRequest | null {
    const v = this.form.getRawValue() as Record<string, unknown>;
    const start = this.combineDateAndTime(v['date'] as Date, v['time'] as string);
    if (!start) return null;
    const out: CreateAppointmentRequest = {
      patientId: v['patientId'] as number,
      doctorId: v['doctorId'] as number,
      startTime: start.toISOString(),
      durationMinutes: v['durationMinutes'] as number
    };
    const reason = (v['reason'] as string).trim();
    if (reason) out.reason = reason;
    const notes = (v['notes'] as string).trim();
    if (notes) out.notes = notes;
    return out;
  }

  private toUpdateRequest(): UpdateAppointmentRequest {
    const v = this.form.getRawValue() as Record<string, unknown>;
    const start = this.combineDateAndTime(v['date'] as Date, v['time'] as string);
    return {
      ...(start ? { startTime: start.toISOString() } : {}),
      durationMinutes: v['durationMinutes'] as number,
      reason: ((v['reason'] as string) || '').trim() || undefined,
      notes: ((v['notes'] as string) || '').trim() || undefined
    };
  }

  /**
   * Combine une Date (jour) avec une string time "HH:mm" en Date complète.
   * Retourne null si l'input est invalide.
   */
  private combineDateAndTime(date: Date, time: string): Date | null {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) return null;
    if (!time || !/^\d{2}:\d{2}$/.test(time)) return null;
    const [hh, mm] = time.split(':').map(Number);
    const result = new Date(date);
    result.setHours(hh, mm, 0, 0);
    return result;
  }
}
