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

export interface DoctorPutOnLeaveDialogData {
  readonly doctor: DoctorDto;
}

export type DoctorPutOnLeaveDialogResult = boolean | undefined;

/**
 * Dialog de confirmation pour la mise en congé d'un médecin (ACTIVE → ON_LEAVE).
 *
 * <p>État réversible : la transition inverse {@code reactivate} permet de
 * remettre le médecin en activité. Aucune perte de données.
 *
 * <p>Le dialog gère lui-même l'appel facade pour afficher les erreurs
 * éventuelles (403, 409 si déjà ON_LEAVE) et permettre le retry.
 */
@Component({
  selector: 'tm-doctor-put-on-leave-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round info-icon">beach_access</span>
      Mettre en congé ?
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>
        Vous êtes sur le point de mettre en congé
        <strong>{{ data.doctor.lastName | uppercase }} {{ data.doctor.firstName }}</strong>
        ({{ data.doctor.licenseNumber }}).
      </p>
      <p class="text-muted">Conséquences :</p>
      <ul class="text-muted consequences">
        <li>Le médecin n'apparaîtra plus dans les sélecteurs pour de nouveaux rendez-vous</li>
        <li>Les rendez-vous déjà planifiés ne sont <strong>pas annulés automatiquement</strong> — à gérer manuellement</li>
        <li>Action <strong>réversible</strong> via la réactivation</li>
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
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="onConfirm()">
        @if (saving()) {
          <span class="material-icons-round spin">progress_activity</span>
          Mise en congé…
        } @else {
          <span class="material-icons-round">beach_access</span>
          Confirmer la mise en congé
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
      .info-icon {
        color: #d97706;
        font-size: 24px;
      }
      .dialog-content {
        font-size: 14px;
        line-height: 1.6;
        max-width: 480px;
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
        animation: tm-leave-spin 0.9s linear infinite;
      }
      @keyframes tm-leave-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class DoctorPutOnLeaveDialogComponent {
  protected readonly facade = inject(DoctorFacade);
  private readonly dialogRef = inject<
    MatDialogRef<DoctorPutOnLeaveDialogComponent, DoctorPutOnLeaveDialogResult>
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: DoctorPutOnLeaveDialogData) {}

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    this.facade.putOnLeave(this.data.doctor.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        /* erreur déjà persistée dans facade.mutation().error */
      }
    });
  }
}
