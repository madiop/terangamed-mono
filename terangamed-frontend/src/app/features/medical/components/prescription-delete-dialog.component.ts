import { ChangeDetectionStrategy, Component, Inject, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { PrescriptionDto } from '@api/models/medical-record.model';
import { MedicalRecordFacade } from '../medical-record.facade';

export interface PrescriptionDeleteDialogData {
  readonly prescription: PrescriptionDto;
}

export type PrescriptionDeleteDialogResult = boolean | undefined;

/**
 * Dialog de confirmation de suppression d'une ordonnance.
 *
 * <p>La suppression est <b>définitive</b> : l'ordonnance entière (et toutes
 * ses lignes médicament) est retirée. La consultation rattachée n'est pas
 * affectée.
 *
 * <p>Le dialog gère lui-même l'appel facade afin d'afficher les erreurs
 * éventuelles (404 si déjà supprimée, 409 si conflit de version, 423 si
 * la consultation a été signée entre-temps) et permettre un retry sans
 * re-fermer.
 *
 * <p>Renvoie {@code true} après suppression réussie, {@code false} si annulé.
 */
@Component({
  selector: 'tm-prescription-delete-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round warn-icon">delete_forever</span>
      Supprimer cette ordonnance ?
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>Vous êtes sur le point de supprimer définitivement&nbsp;:</p>
      <div class="prescription-summary">
        <div class="prescription-num">
          Ordonnance N° <strong>{{ data.prescription.prescriptionNumber }}</strong>
        </div>
        <div class="prescription-meta">
          {{ data.prescription.lines.length }} ligne(s) médicament
          @if (data.prescription.validUntil) {
            · Valide jusqu'au {{ formatDate(data.prescription.validUntil) }}
          }
        </div>
      </div>

      <p class="warning-block">
        <span class="material-icons-round">warning</span>
        <span>
          <strong>Action irréversible.</strong> L'ordonnance entière
          (avec toutes ses lignes médicament) sera retirée du dossier
          de la consultation. Pour seulement ajuster une ligne, modifiez
          plutôt l'ordonnance au lieu de la supprimer.
        </span>
      </p>

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
          Suppression…
        } @else {
          <span class="material-icons-round">delete</span>
          Supprimer l'ordonnance
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
        color: #b91c1c;
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
      .prescription-summary {
        padding: 10px 12px;
        background: rgba(0, 0, 0, 0.04);
        border-left: 3px solid #2963b0;
        border-radius: 6px;
        margin: 8px 0 12px;
      }
      .prescription-num {
        font-size: 14px;
      }
      .prescription-meta {
        font-size: 12px;
        color: var(--color-text-muted);
        margin-top: 4px;
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
        margin: 8px 0;
      }
      .warning-block .material-icons-round {
        color: #d97706;
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
        animation: tm-px-del-spin 0.9s linear infinite;
      }
      @keyframes tm-px-del-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class PrescriptionDeleteDialogComponent {
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly dialogRef = inject<
    MatDialogRef<PrescriptionDeleteDialogComponent, PrescriptionDeleteDialogResult>
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: PrescriptionDeleteDialogData) {}

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    this.facade.deletePrescription(this.data.prescription.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        /* erreur déjà persistée dans facade.mutation().error */
      }
    });
  }

  protected formatDate(iso: string): string {
    try {
      const d = new Date(iso);
      return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
    } catch {
      return iso;
    }
  }
}
