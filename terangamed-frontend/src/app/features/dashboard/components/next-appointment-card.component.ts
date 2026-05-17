import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { NextAppointmentState } from '../dashboard.facade';

/**
 * Card spécifique au mode DOCTOR — affiche le prochain RDV à venir.
 *
 * <p>Diffère de {@link KpiCardComponent} qui n'expose qu'un nombre :
 * ici on affiche une date/heure formatée + le nom du patient. Présentation
 * volontairement alignée visuellement (mêmes paddings, radius, ombre) pour
 * tenir dans la grille KPI sans rupture.
 */
@Component({
  selector: 'tm-next-appointment-card',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card next-card">
      <div class="next-icon">
        <span class="material-icons-round">event_available</span>
      </div>
      <div class="next-content">
        @if (state?.loading) {
          <span class="next-skeleton" aria-hidden="true"></span>
          <span class="sr-only">Chargement…</span>
        } @else if (state?.error) {
          <span class="next-value next-value--error" [title]="state?.error ?? ''">—</span>
        } @else {
          <!-- Nouvelle chaîne primary @if → 'as a' autorisé -->
          @if (state?.appointment; as a) {
            <span class="next-datetime">{{ formatDateTime(a.startTime) }}</span>
            <span class="next-patient">{{ a.patientNameSnapshot }}</span>
          } @else {
            <span class="next-value next-value--empty">Aucun RDV à venir</span>
          }
        }
        <span class="next-label">Mon prochain RDV</span>
      </div>
    </article>
  `,
  styles: [
    `
      .next-card {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 20px;
        display: flex;
        align-items: center;
        gap: 16px;
        transition: box-shadow 0.2s, transform 0.2s;
      }
      .next-card:hover {
        box-shadow: var(--shadow-md);
        transform: translateY(-1px);
      }

      .next-icon {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: #d1fae5;
        color: #059669;
      }
      .next-icon .material-icons-round {
        font-size: 24px;
      }

      .next-content {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;
      }

      .next-datetime {
        font-size: 18px;
        font-weight: 700;
        color: var(--color-text);
        line-height: 1.2;
      }
      .next-patient {
        font-size: 13px;
        color: var(--color-text);
        margin-top: 2px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .next-value {
        font-size: 16px;
        font-weight: 600;
        color: var(--color-text-muted);
      }
      .next-value--error,
      .next-value--empty {
        color: var(--color-text-muted);
      }
      .next-label {
        font-size: 13px;
        color: var(--color-text-muted);
        margin-top: 4px;
      }

      .next-skeleton {
        display: block;
        height: 22px;
        width: 120px;
        border-radius: 6px;
        background: linear-gradient(
          90deg,
          rgba(0, 0, 0, 0.06) 25%,
          rgba(0, 0, 0, 0.1) 37%,
          rgba(0, 0, 0, 0.06) 63%
        );
        background-size: 400% 100%;
        animation: tm-next-skeleton 1.4s ease infinite;
      }
      @keyframes tm-next-skeleton {
        0% {
          background-position: 100% 50%;
        }
        100% {
          background-position: 0 50%;
        }
      }

      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
      }
    `
  ]
})
export class NextAppointmentCardComponent {
  @Input() state: NextAppointmentState | null = null;

  /** Format français court : "lun. 4 mai · 10h30". */
  formatDateTime(iso: string): string {
    try {
      const d = parseISO(iso);
      return format(d, "EEE d MMM · HH'h'mm", { locale: fr });
    } catch {
      return iso;
    }
  }
}
