import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppointmentStatus } from '@api/models/appointment.model';

const STATUS_LABEL: Record<AppointmentStatus, string> = {
  PLANNED: 'Planifié',
  CONFIRMED: 'Confirmé',
  COMPLETED: 'Terminé',
  CANCELLED: 'Annulé',
  NO_SHOW: 'Absent'
};

/**
 * Badge coloré indiquant le statut d'un rendez-vous.
 *
 * <p>Code couleur cohérent avec {@code appointmentToCalendarEvent} (planning) :
 * <ul>
 *   <li>{@code PLANNED} — bleu (à confirmer)</li>
 *   <li>{@code CONFIRMED} — vert (prêt à recevoir)</li>
 *   <li>{@code COMPLETED} — gris (consultation terminée)</li>
 *   <li>{@code CANCELLED} — rouge (annulé)</li>
 *   <li>{@code NO_SHOW} — orange (patient absent)</li>
 * </ul>
 */
@Component({
  selector: 'tm-appointment-status-badge',
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
      .status-badge--planned {
        background: #dbeafe;
        color: #1e40af;
      }
      .status-badge--confirmed {
        background: #d1fae5;
        color: #065f46;
      }
      .status-badge--completed {
        background: #e5e7eb;
        color: #374151;
      }
      .status-badge--cancelled {
        background: #fee2e2;
        color: #991b1b;
      }
      .status-badge--no_show {
        background: #fed7aa;
        color: #9a3412;
      }
    `
  ]
})
export class AppointmentStatusBadgeComponent {
  @Input({ required: true }) status!: AppointmentStatus;

  get label(): string {
    return STATUS_LABEL[this.status];
  }
}
