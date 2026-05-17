import { ChangeDetectionStrategy, Component, Inject, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { ConsultationDto } from '@api/models/medical-record.model';
import { MedicalRecordFacade } from '../medical-record.facade';

export interface ConsultationSignDialogData {
  readonly consultation: ConsultationDto;
}

export type ConsultationSignDialogResult = boolean | undefined;

/**
 * Dialog de confirmation pour la signature d'une consultation.
 *
 * <p>La signature est <b>irréversible</b> : une fois signée, la consultation
 * passe en {@code signed=true} et devient immutable. L'utilisateur doit en
 * être conscient — message clair affiché.
 *
 * <p>Le dialog gère lui-même l'appel facade pour permettre l'affichage
 * d'erreurs backend (ex: 409 si déjà signée par quelqu'un d'autre) et le
 * retry sans fermer.
 */
@Component({
  selector: 'tm-consultation-sign-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <span class="material-icons-round info-icon">verified</span>
      Signer cette consultation ?
    </h2>

    <mat-dialog-content class="dialog-content">
      <p>
        Vous êtes sur le point de signer la consultation du
        <strong>{{ formatDate(data.consultation.consultationDate) }}</strong>.
      </p>
      <p class="warning-block">
        <span class="material-icons-round">warning</span>
        <span>
          <strong>Action irréversible.</strong> Une fois signée, la consultation
          ne pourra plus être modifiée — vous ne pourrez plus changer le motif,
          le diagnostic, les observations ni les recommandations.
        </span>
      </p>
      <p class="text-muted">
        Vérifiez que le contenu est complet et correct avant de signer.
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
        Vérifier d'abord
      </button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="onConfirm()">
        @if (saving()) {
          <span class="material-icons-round spin">progress_activity</span>
          Signature…
        } @else {
          <span class="material-icons-round">verified</span>
          Signer définitivement
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
        color: #2963b0;
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
        animation: tm-sign-spin 0.9s linear infinite;
      }
      @keyframes tm-sign-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
    `
  ]
})
export class ConsultationSignDialogComponent {
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly dialogRef = inject<
    MatDialogRef<ConsultationSignDialogComponent, ConsultationSignDialogResult>
  >(MatDialogRef);

  protected readonly saving = computed(() => this.facade.mutation().saving);

  constructor(@Inject(MAT_DIALOG_DATA) public readonly data: ConsultationSignDialogData) {}

  protected onCancel(): void {
    if (this.saving()) return;
    this.dialogRef.close(false);
  }

  protected onConfirm(): void {
    if (this.saving()) return;
    this.facade.signConsultation(this.data.consultation.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        // Erreur déjà persistée dans facade.mutation().error
      }
    });
  }

  protected formatDate(iso: string): string {
    try {
      const d = new Date(iso);
      return d.toLocaleDateString('fr-FR', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      });
    } catch {
      return iso;
    }
  }
}
