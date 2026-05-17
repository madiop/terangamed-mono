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

export interface DoctorReactivateDialogData {
  readonly doctor: DoctorDto;
}

export type DoctorReactivateDialogResult = boolean | undefined;

/**
 * Dialog de confirmation pour la réactivation d'un médecin
 * (ON_LEAVE / RETIRED → ACTIVE).
 *
 * <p>Le message s'adapte au statut courant :
 * <ul>
 *   <li>{@code ON_LEAVE} → "Reprise d'activité" (retour de congé)</li>
 *   <li>{@code RETIRED} → "Sortie de retraite" (avec rappel que c'est un cas
 *       exceptionnel — autorisé en UI cf. décision 9.7)</li>
 * </ul>
 */
@Component({
  selector: 'tm-doctor-reactivate-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round info-icon">play_circle</span>
      {{ isFromRetirement() ? 'Sortir de la retraite ?' : 'Reprendre l\\'activité ?' }}
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>
        Vous êtes sur le point de réactiver
        <strong>{{ data.doctor.lastName | uppercase }} {{ data.doctor.firstName }}</strong>
        ({{ data.doctor.licenseNumber }}).
      </p>

      @if (isFromRetirement()) {
        <div class="info-block">
          <span class="material-icons-round">info</span>
          <div>
            Le médecin était en <strong>retraite</strong>. Cette action sort le
            praticien de retraite et le repasse en activité — cas exceptionnel
            (retour de retraite, reprise d'activité). Vérifiez que cette
            situation correspond bien.
          </div>
        </div>
      }

      <p class="text-muted">Conséquences :</p>
      <ul class="text-muted consequences">
        <li>Le médecin réapparaîtra dans les sélecteurs pour les nouveaux RDV</li>
        <li>Statut passera à <strong>{{ "En activité" }}</strong></li>
        <li>Aucune donnée n'est créée ou modifiée — c'est une simple transition d'état</li>
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
          Réactivation…
        } @else {
          <span class="material-icons-round">play_circle</span>
          {{ isFromRetirement() ? 'Confirmer la sortie de retraite' : "Confirmer la reprise" }}
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
        color: #059669;
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
      .info-block {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 10px 12px;
        background: #dbeafe;
        border-left: 4px solid #3b82f6;
        border-radius: 6px;
        color: #1e3a8a;
        margin: 12px 0;
      }
      .info-block .material-icons-round {
        color: #2563eb;
        font-size: 20px;
        flex-shrink: 0;
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
        animation: tm-react-spin 0.9s linear infinite;
      }
      @keyframes tm-react-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class DoctorReactivateDialogComponent {
  protected readonly facade = inject(DoctorFacade);
  private readonly dialogRef = inject<
    MatDialogRef<DoctorReactivateDialogComponent, DoctorReactivateDialogResult>
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: DoctorReactivateDialogData) {}

  /** True si on réactive depuis RETIRED (cas exceptionnel — message dédié). */
  protected isFromRetirement(): boolean {
    return this.data.doctor.status === 'RETIRED';
  }

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    this.facade.reactivate(this.data.doctor.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        /* erreur déjà persistée dans facade.mutation().error */
      }
    });
  }
}
