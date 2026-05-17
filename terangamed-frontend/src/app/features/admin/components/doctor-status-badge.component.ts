import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DoctorStatus } from '@api/models/doctor.model';

const STATUS_LABEL: Record<DoctorStatus, string> = {
  ACTIVE: 'En activité',
  ON_LEAVE: 'En congé',
  RETIRED: 'Retraité'
};

const STATUS_ICON: Record<DoctorStatus, string> = {
  ACTIVE: 'check_circle',
  ON_LEAVE: 'beach_access',
  RETIRED: 'elderly'
};

/**
 * Badge coloré indiquant le statut métier d'un médecin.
 *
 * <p>Code couleur :
 * <ul>
 *   <li>{@code ACTIVE} — vert (médecin disponible pour les consultations)</li>
 *   <li>{@code ON_LEAVE} — ambre (en congé temporaire, peut être réactivé)</li>
 *   <li>{@code RETIRED} — gris (sorti de l'activité, retour possible via réactivation)</li>
 * </ul>
 */
@Component({
  selector: 'tm-doctor-status-badge',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="status-badge" [class]="'status-badge--' + status.toLowerCase()">
      @if (showIcon) {
        <span class="material-icons-round">{{ icon }}</span>
      }
      {{ label }}
    </span>
  `,
  styles: [
    `
      .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 10px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 600;
        line-height: 1.5;
        white-space: nowrap;
      }
      .status-badge .material-icons-round {
        font-size: 14px;
      }
      .status-badge--active {
        background: #d1fae5;
        color: #065f46;
      }
      .status-badge--on_leave {
        background: #fef3c7;
        color: #78350f;
      }
      .status-badge--retired {
        background: #e5e7eb;
        color: #374151;
      }
    `
  ]
})
export class DoctorStatusBadgeComponent {
  @Input({ required: true }) status!: DoctorStatus;
  @Input() showIcon = false;

  get label(): string {
    return STATUS_LABEL[this.status];
  }

  get icon(): string {
    return STATUS_ICON[this.status];
  }
}
