import { ChangeDetectionStrategy, Component, Inject, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Observable } from 'rxjs';
import { AppointmentDto } from '@api/models/appointment.model';
import { AppointmentFacade } from '../appointment.facade';

/**
 * Type d'action destructive proposée par le dialog.
 */
export type AppointmentActionType = 'cancel' | 'noShow';

/**
 * Données passées au dialog.
 */
export interface AppointmentConfirmActionDialogData {
  readonly appointment: AppointmentDto;
  readonly action: AppointmentActionType;
}

export type AppointmentConfirmActionDialogResult = boolean | undefined;

/**
 * Configuration UX par type d'action — texte + icône + couleur de l'action.
 */
const ACTION_CONFIG: Record<
  AppointmentActionType,
  {
    title: string;
    icon: string;
    confirmLabel: string;
    confirmIcon: string;
    description: (a: AppointmentDto) => string;
    consequences: string[];
  }
> = {
  cancel: {
    title: 'Annuler ce rendez-vous ?',
    icon: 'cancel',
    confirmLabel: 'Annuler le rendez-vous',
    confirmIcon: 'cancel',
    description: (a) =>
      `Le rendez-vous de ${a.patientNameSnapshot} avec ${a.doctorNameSnapshot} sera annulé.`,
    consequences: [
      'Le créneau sera libéré et pourra être réattribué',
      "L'historique du rendez-vous est préservé",
      'Le patient sera notifié (selon configuration)'
    ]
  },
  noShow: {
    title: 'Marquer le patient comme absent ?',
    icon: 'person_off',
    confirmLabel: 'Confirmer absence',
    confirmIcon: 'person_off',
    description: (a) =>
      `Marquer ${a.patientNameSnapshot} comme absent au rendez-vous avec ${a.doctorNameSnapshot}.`,
    consequences: [
      "L'absence est enregistrée dans l'historique du patient",
      'Le créneau ne sera pas réutilisable rétroactivement',
      "Cette action n'est pas réversible — un nouveau rendez-vous sera nécessaire"
    ]
  }
};

/**
 * Dialog générique de confirmation pour les transitions destructives d'un
 * rendez-vous (Annuler, Marquer absent).
 *
 * <p>Le dialog gère lui-même l'appel facade pour afficher l'erreur backend
 * directement et permettre un retry sans fermer.
 */
@Component({
  selector: 'tm-appointment-confirm-action-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round warning-icon">{{ config.icon }}</span>
      {{ config.title }}
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>{{ config.description(data.appointment) }}</p>
      <p class="text-muted">Conséquences :</p>
      <ul class="text-muted consequences">
        @for (c of config.consequences; track c) {
          <li>{{ c }}</li>
        }
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
        Retour
      </button>
      <button mat-flat-button color="warn" [disabled]="saving()" (click)="onConfirm()">
        @if (saving()) {
          <span class="material-icons-round spin">progress_activity</span>
          Traitement…
        } @else {
          <span class="material-icons-round">{{ config.confirmIcon }}</span>
          {{ config.confirmLabel }}
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
      .warning-icon {
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
        animation: tm-confirm-spin 0.9s linear infinite;
      }
      @keyframes tm-confirm-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class AppointmentConfirmActionDialogComponent {
  protected readonly facade = inject(AppointmentFacade);
  private readonly dialogRef = inject<
    MatDialogRef<
      AppointmentConfirmActionDialogComponent,
      AppointmentConfirmActionDialogResult
    >
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  protected readonly config: (typeof ACTION_CONFIG)[AppointmentActionType];

  constructor(
    @Inject(MAT_DIALOG_DATA) public readonly data: AppointmentConfirmActionDialogData
  ) {
    this.config = ACTION_CONFIG[data.action];
  }

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    const apiCall: Observable<void> =
      this.data.action === 'cancel'
        ? this.facade.cancel(this.data.appointment.id)
        : this.facade.markNoShow(this.data.appointment.id);

    apiCall.subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        // Erreur déjà persistée dans facade.mutation().error
      }
    });
  }
}
