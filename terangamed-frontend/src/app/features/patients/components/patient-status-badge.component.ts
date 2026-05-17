import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PatientStatus } from '@api/models/patient.model';

const STATUS_LABEL: Record<PatientStatus, string> = {
  ACTIVE: 'Actif',
  INACTIVE: 'Inactif',
  ARCHIVED: 'Archivé'
};

/**
 * Badge coloré indiquant le statut du patient.
 *
 * <p>Code couleur :
 * <ul>
 *   <li>{@code ACTIVE} — vert (suivi en cours)</li>
 *   <li>{@code INACTIVE} — gris (dossier inactif mais conservé)</li>
 *   <li>{@code ARCHIVED} — rouge (soft-deleted)</li>
 * </ul>
 */
@Component({
  selector: 'tm-patient-status-badge',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="status-badge" [class]="'status-badge--' + status.toLowerCase()">
      {{ label }}
    </span>
  `,
  styles: [
    `
      .status-badge {
        display: inline-flex;
        align-items: center;
        padding: 2px 10px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 600;
        line-height: 1.5;
        white-space: nowrap;
      }
      .status-badge--active {
        background: #d1fae5;
        color: #065f46;
      }
      .status-badge--inactive {
        background: #e5e7eb;
        color: #374151;
      }
      .status-badge--archived {
        background: #fee2e2;
        color: #991b1b;
      }
    `
  ]
})
export class PatientStatusBadgeComponent {
  @Input({ required: true }) status!: PatientStatus;

  get label(): string {
    return STATUS_LABEL[this.status];
  }
}
