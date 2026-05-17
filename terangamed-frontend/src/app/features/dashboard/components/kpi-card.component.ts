import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { KpiState } from '../dashboard.facade';

export type KpiColor = 'blue' | 'orange' | 'purple' | 'green';

/**
 * Card KPI réutilisable — affiche un nombre avec un libellé et une icône colorée.
 *
 * <p>Trois états :
 * <ul>
 *   <li><b>loading</b> → skeleton animé</li>
 *   <li><b>error</b> → "—" + tooltip avec le message</li>
 *   <li><b>value</b> → grand chiffre</li>
 * </ul>
 */
@Component({
  selector: 'tm-kpi-card',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="kpi-card" [class]="'kpi-' + color">
      <div class="kpi-icon">
        <span class="material-icons-round">{{ icon }}</span>
      </div>
      <div class="kpi-content">
        @if (state?.loading) {
          <span class="kpi-skeleton" aria-hidden="true"></span>
          <span class="sr-only">Chargement…</span>
        } @else if (state?.error) {
          <span class="kpi-value kpi-value--error" [title]="state?.error ?? ''">—</span>
        } @else {
          <span class="kpi-value">{{ state?.value ?? 0 }}</span>
        }
        <span class="kpi-label">{{ label }}</span>
      </div>
    </article>
  `,
  styles: [
    `
      .kpi-card {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 20px;
        display: flex;
        align-items: center;
        gap: 16px;
        transition: box-shadow 0.2s, transform 0.2s;
      }
      .kpi-card:hover {
        box-shadow: var(--shadow-md);
        transform: translateY(-1px);
      }

      .kpi-icon {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      .kpi-icon .material-icons-round { font-size: 24px; }

      .kpi-blue   .kpi-icon { background: #dbeafe; color: #2563eb; }
      .kpi-orange .kpi-icon { background: #fed7aa; color: #ea580c; }
      .kpi-purple .kpi-icon { background: #e9d5ff; color: #7c3aed; }
      .kpi-green  .kpi-icon { background: #d1fae5; color: #059669; }

      .kpi-content {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;
      }

      .kpi-value {
        font-size: 28px;
        font-weight: 700;
        color: var(--color-text);
        line-height: 1.1;
      }
      .kpi-value--error { color: var(--color-text-muted); }

      .kpi-label {
        font-size: 13px;
        color: var(--color-text-muted);
        margin-top: 2px;
      }

      // Skeleton de loading
      .kpi-skeleton {
        display: block;
        height: 28px;
        width: 60px;
        border-radius: 6px;
        background: linear-gradient(
          90deg,
          rgba(0, 0, 0, 0.06) 25%,
          rgba(0, 0, 0, 0.10) 37%,
          rgba(0, 0, 0, 0.06) 63%
        );
        background-size: 400% 100%;
        animation: tm-skeleton 1.4s ease infinite;
      }
      @keyframes tm-skeleton {
        0%   { background-position: 100% 50%; }
        100% { background-position: 0 50%; }
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
export class KpiCardComponent {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) icon!: string;
  @Input({ required: true }) color!: KpiColor;
  @Input() state: KpiState | null = null;
}
