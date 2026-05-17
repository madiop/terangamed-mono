import { ChangeDetectionStrategy, Component, Inject, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { DoctorDto } from '@api/models/doctor.model';
import { DoctorFacade } from '../doctor.facade';

export interface DoctorRetireDialogData {
  readonly doctor: DoctorDto;
}

export type DoctorRetireDialogResult = boolean | undefined;

/**
 * Dialog de confirmation pour la mise en retraite d'un médecin
 * (ACTIVE / ON_LEAVE → RETIRED).
 *
 * <p>Action <b>terminale par défaut</b> mais théoriquement réversible via
 * {@code reactivate} (cas du retour de retraite — autorisé en UI cf. décision
 * 9.7). Avertissement explicite à l'utilisateur pour éviter les confusions
 * avec la mise en congé.
 *
 * <p>Le dossier du médecin et son historique restent <b>intégralement
 * préservés</b> — c'est la raison pour laquelle on ne propose pas de
 * suppression physique.
 */
@Component({
  selector: 'tm-doctor-retire-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round warn-icon">elderly</span>
      Mettre à la retraite ?
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>
        Vous êtes sur le point de mettre à la retraite
        <strong>{{ data.doctor.lastName | uppercase }} {{ data.doctor.firstName }}</strong>
        ({{ data.doctor.licenseNumber }}).
      </p>

      <div class="warning-block">
        <span class="material-icons-round">warning</span>
        <div>
          <strong>Différence avec « Mettre en congé » :</strong>
          la mise en retraite est une décision durable (fin de carrière, départ
          de l'établissement). Préférez « Mettre en congé » pour une absence
          temporaire (vacances, congé maladie, formation).
        </div>
      </div>

      <p class="text-muted">Conséquences :</p>
      <ul class="text-muted consequences">
        <li>Le médecin n'apparaîtra plus dans aucun sélecteur</li>
        <li>L'historique (consultations, prescriptions, RDV passés) est <strong>intégralement préservé</strong></li>
        <li>La <strong>réactivation reste possible</strong> en cas de retour</li>
      </ul>

      @if (facade.mutation().error; as err) {
        <div class="error-banner" role="alert">
          <span class="material-icons-round">error_outline</span>
          <span>{{ err }}</span>
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end" class="dialog-actions">
      <button mat-button [disabled]="saving()" (click)="onCancel()">
        Annuler
      </button>
      <button mat-flat-button color="warn" [disabled]="saving()" (click)="onConfirm()">
        @if (saving()) {
          <span class="material-icons-round spin">progress_activity</span>
          Mise en retraite…
        } @else {
          <span class="material-icons-round">elderly</span>
          Confirmer la retraite
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
      .warn-icon {
        color: #6b7280;
        font-size: 24px;
      }
      .dialog-content {
        font-size: 14px;
        line-height: 1.6;
        max-width: 520px;
      }
      .dialog-content p {
        margin: 8px 0;
      }
      .text-muted {
        color: var(--color-text-muted);
      }
      .consequences {
        margin: 8px 0;
        padding-left: 24px;
      }
      .consequences li {
        margin: 4px 0;
      }
      .warning-block {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 10px 12px;
        background: #fef3c7;
        border-left: 4px solid #f59e0b;
        border-radius: 6px;
        color: #78350f;
        margin: 12px 0;
      }
      .warning-block .material-icons-round {
        color: #d97706;
        font-size: 20px;
        flex-shrink: 0;
      }
      .warning-block strong {
        display: block;
        margin-bottom: 4px;
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
        animation: tm-retire-spin 0.9s linear infinite;
      }
      @keyframes tm-retire-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class DoctorRetireDialogComponent {
  protected readonly facade = inject(DoctorFacade);
  private readonly dialogRef = inject<
    MatDialogRef<DoctorRetireDialogComponent, DoctorRetireDialogResult>
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: DoctorRetireDialogData) {}

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    this.facade.retire(this.data.doctor.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        /* erreur déjà persistée dans facade.mutation().error */
      }
    });
  }
}
