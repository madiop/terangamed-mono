import { ChangeDetectionStrategy, Component, Inject, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { fr } from 'date-fns/locale';
import {
  AntecedentDto,
  AntecedentType,
  CreateAntecedentRequest,
  UpdateAntecedentRequest
} from '@api/models/medical-record.model';
import { MedicalRecordFacade } from '../medical-record.facade';

/**
 * Données passées au dialog par le composant appelant.
 *
 * <p>Mode CREATE → {@code antecedent} undefined, on fournit {@code medicalRecordId}.
 * Mode EDIT → on fournit l'antécédent existant.
 */
export interface AntecedentFormDialogData {
  readonly medicalRecordId: number;
  readonly antecedent?: AntecedentDto;
}

export type AntecedentFormDialogResult = AntecedentDto | undefined;

const ANTECEDENT_TYPE_LABEL: Record<AntecedentType, string> = {
  ALLERGY: 'Allergie',
  MEDICAL_CONDITION: 'Antécédent médical',
  SURGERY: 'Chirurgie',
  MEDICATION: 'Traitement long cours',
  FAMILY: 'Antécédent familial'
};

/**
 * Dialog formulaire antécédent — gère création et édition.
 *
 * <p>Le dialog gère lui-même l'appel facade (create ou update) et ne se ferme
 * qu'après succès HTTP. Renvoie le DTO créé/mis à jour au caller pour qu'il
 * puisse réagir (refresh, scroll, etc.).
 */
@Component({
  selector: 'tm-antecedent-form-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatCheckboxModule,
    MatButtonModule
  ],
  providers: [
    provideDateFnsAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: fr }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round">{{ isEdit ? 'edit_note' : 'add_circle' }}</span>
      {{ isEdit ? "Modifier l'antécédent" : 'Ajouter un antécédent' }}
    </h2>

    <mat-dialog-content class="dialog-content">
      <form [formGroup]="form" class="dialog-form">
        <div class="form-grid">
          <mat-form-field appearance="outline">
            <mat-label>Type *</mat-label>
            <mat-select formControlName="type">
              @for (key of typeKeys; track key) {
                <mat-option [value]="key">{{ typeLabel(key) }}</mat-option>
              }
            </mat-select>
            @if (showError('type')) {
              <mat-error>Type obligatoire</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Titre *</mat-label>
            <input matInput formControlName="title" maxlength="200"
                   placeholder="Ex: Pénicilline, Asthme, Appendicectomie…" />
            @if (showError('title')) {
              <mat-error>{{ errorOf('title') }}</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Description (optionnel)</mat-label>
            <textarea matInput formControlName="description" rows="3" maxlength="5000"
                      placeholder="Détails complémentaires, contexte, gravité…"></textarea>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Date d'apparition (optionnel)</mat-label>
            <input matInput [matDatepicker]="onsetPicker" formControlName="onsetDate" [max]="today" />
            <mat-datepicker-toggle matIconSuffix [for]="onsetPicker" />
            <mat-datepicker #onsetPicker />
          </mat-form-field>

          <div class="active-toggle">
            <mat-checkbox formControlName="active">
              Antécédent actif
            </mat-checkbox>
            <span class="text-muted active-hint">
              Décocher si l'antécédent est résolu / inactif.
            </span>
          </div>
        </div>

        @if (facade.mutation().error; as err) {
          <div class="error-banner" role="alert">
            <span class="material-icons-round">error_outline</span>
            <span>{{ err }}</span>
          </div>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end" class="dialog-actions">
      <button mat-button [disabled]="saving()" (click)="onCancel()">
        Annuler
      </button>
      <button
        mat-flat-button
        color="primary"
        [disabled]="form.invalid || saving()"
        (click)="onConfirm()"
      >
        @if (saving()) {
          <span class="material-icons-round spin">progress_activity</span>
          Enregistrement…
        } @else {
          <span class="material-icons-round">{{ isEdit ? 'save' : 'add' }}</span>
          {{ isEdit ? 'Enregistrer' : 'Ajouter' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-title {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 18px;
        margin: 0;
      }
      .dialog-title .material-icons-round {
        color: var(--color-primary, #2963b0);
        font-size: 24px;
      }
      .dialog-content {
        min-width: 480px;
        max-width: 600px;
      }
      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px 16px;
        margin-top: 12px;
      }
      .span-2 {
        grid-column: span 2;
      }
      .active-toggle {
        grid-column: span 2;
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .active-hint {
        font-size: 12px;
      }
      .text-muted {
        color: var(--color-text-muted);
      }
      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 12px;
        padding: 10px 12px;
        background: #fef2f2;
        border-left: 4px solid #ef4444;
        border-radius: 6px;
        color: #991b1b;
        font-size: 13px;
      }
      .error-banner .material-icons-round {
        font-size: 18px;
      }
      .dialog-actions {
        gap: 8px;
        padding: 8px 16px 16px;
      }
      .dialog-actions button {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .dialog-actions .material-icons-round {
        font-size: 18px;
      }
      .spin {
        animation: tm-ant-spin 0.9s linear infinite;
      }
      @keyframes tm-ant-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
      ::ng-deep .dialog-form .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class AntecedentFormDialogComponent {
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject<
    MatDialogRef<AntecedentFormDialogComponent, AntecedentFormDialogResult>
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  protected readonly today = new Date();

  protected readonly typeKeys: AntecedentType[] = [
    'ALLERGY',
    'MEDICAL_CONDITION',
    'SURGERY',
    'MEDICATION',
    'FAMILY'
  ];

  protected readonly form: FormGroup;
  protected readonly isEdit: boolean;

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: AntecedentFormDialogData) {
    this.isEdit = !!data.antecedent;
    const a = data.antecedent;
    this.form = this.fb.group({
      type: this.fb.control<AntecedentType | null>(a?.type ?? null, Validators.required),
      title: this.fb.control<string>(a?.title ?? '', [
        Validators.required,
        Validators.maxLength(200)
      ]),
      description: this.fb.control<string>(a?.description ?? '', [Validators.maxLength(5000)]),
      onsetDate: this.fb.control<Date | null>(a?.onsetDate ? new Date(a.onsetDate) : null),
      active: this.fb.control<boolean>(a?.active ?? true)
    });
  }

  protected typeLabel(t: AntecedentType): string {
    return ANTECEDENT_TYPE_LABEL[t];
  }

  protected showError(controlName: string): boolean {
    const c = this.form.get(controlName);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected errorOf(controlName: string): string {
    const c = this.form.get(controlName);
    if (!c?.errors) return '';
    if (c.errors['required']) return 'Champ obligatoire';
    if (c.errors['maxlength']) {
      return `Maximum ${c.errors['maxlength'].requiredLength} caractères`;
    }
    return 'Valeur invalide';
  }

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(undefined);
  }

  protected onConfirm(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue() as Record<string, unknown>;

    if (this.isEdit && this.data.antecedent) {
      const request: UpdateAntecedentRequest = {
        type: v['type'] as AntecedentType,
        title: ((v['title'] as string) || '').trim(),
        description: ((v['description'] as string) || '').trim() || undefined,
        onsetDate: v['onsetDate'] instanceof Date ? this.formatDate(v['onsetDate']) : undefined,
        active: v['active'] as boolean
      };
      this.facade.updateAntecedent(this.data.antecedent.id, request).subscribe({
        next: (updated) => this.dialogRef.close(updated),
        error: () => {
          /* erreur déjà persistée dans facade.mutation().error */
        }
      });
    } else {
      const request: CreateAntecedentRequest = {
        medicalRecordId: this.data.medicalRecordId,
        type: v['type'] as AntecedentType,
        title: ((v['title'] as string) || '').trim(),
        description: ((v['description'] as string) || '').trim() || undefined,
        onsetDate: v['onsetDate'] instanceof Date ? this.formatDate(v['onsetDate']) : undefined,
        active: v['active'] as boolean
      };
      this.facade.createAntecedent(request).subscribe({
        next: (created) => this.dialogRef.close(created),
        error: () => {
          /* idem */
        }
      });
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
}
