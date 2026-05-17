import { ChangeDetectionStrategy, Component, Inject, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { PatientDto } from '@api/models/patient.model';
import { PatientFacade } from '../patient.facade';

/**
 * Données passées au dialog par le composant appelant via {@code MatDialog.open()}.
 */
export interface PatientArchiveDialogData {
  readonly patient: PatientDto;
}

/**
 * Résultat retourné par le dialog au {@code afterClosed()} :
 * <ul>
 *   <li>{@code true} → archivage confirmé ET réussi</li>
 *   <li>{@code false} ou {@code undefined} → annulé ou échec</li>
 * </ul>
 */
export type PatientArchiveDialogResult = boolean | undefined;

/**
 * Dialog de confirmation pour l'archivage d'un patient.
 *
 * <p>Le dialog gère lui-même l'appel à {@link PatientFacade#archive} pour :
 * <ul>
 *   <li>Afficher le spinner pendant la requête</li>
 *   <li>Afficher l'erreur backend (403, 409, 500…) directement dans le dialog</li>
 *   <li>Ne fermer en succès ({@code true}) qu'après confirmation HTTP 200</li>
 * </ul>
 *
 * <p><b>Usage</b> :
 * <pre>
 * this.dialog.open(PatientArchiveDialogComponent, {
 *   data: { patient },
 *   width: '420px'
 * }).afterClosed().subscribe(confirmed =&gt; {
 *   if (confirmed) {
 *     // patient archivé, refresh la vue
 *   }
 * });
 * </pre>
 */
@Component({
  selector: 'tm-patient-archive-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round warning-icon">warning</span>
      Archiver ce patient ?
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>
        Vous êtes sur le point d'archiver le dossier de
        <strong>{{ data.patient.lastName | uppercase }} {{ data.patient.firstName }}</strong>
        (N° {{ data.patient.medicalRecordNumber }}).
      </p>
      <p class="text-muted">
        Le patient passe en statut <strong>ARCHIVÉ</strong> :
      </p>
      <ul class="text-muted consequences">
        <li>Il ne pourra plus être sélectionné pour un nouveau rendez-vous</li>
        <li>Il restera consultable en lecture seule dans la liste avec le filtre "Archivés"</li>
        <li>L'historique des consultations et factures est <strong>préservé</strong></li>
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
          Archivage…
        } @else {
          <span class="material-icons-round">archive</span>
          Archiver
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
        animation: tm-archive-spin 0.9s linear infinite;
      }
      @keyframes tm-archive-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class PatientArchiveDialogComponent {
  protected readonly facade = inject(PatientFacade);
  private readonly dialogRef = inject<
    MatDialogRef<PatientArchiveDialogComponent, PatientArchiveDialogResult>
  >(MatDialogRef);

  /** Spinner pendant l'appel HTTP. Dérivé de l'état mutation de la facade. */
  protected readonly saving = computed(() => this.facade.mutation().saving);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: PatientArchiveDialogData) {}

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    this.facade.archive(this.data.patient.id).subscribe({
      next: () => {
        this.dialogRef.close(true);
      },
      error: () => {
        // L'erreur est déjà persistée dans facade.mutation().error et
        // affichée dans le template — on ne ferme pas, l'utilisateur
        // peut relire le message et réessayer.
      }
    });
  }
}
