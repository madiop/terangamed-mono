import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * En-tête de page réutilisable — pattern du design existant.
 *
 * <p>Chaque page utilise ce composant en tête de son template :
 * <pre>
 * &lt;tm-page-header
 *   title="Tableau de bord"
 *   [subtitle]="'Bienvenue, ' + userName"&gt;
 *   &lt;a class="btn btn-primary"&gt;Nouveau RDV&lt;/a&gt;
 * &lt;/tm-page-header&gt;
 * </pre>
 *
 * <p>Le slot {@code <ng-content>} accueille les actions à droite (boutons, etc.).
 */
@Component({
  selector: 'tm-page-header',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ title }}</h1>
        @if (subtitle) {
          <p class="page-subtitle">{{ subtitle }}</p>
        }
      </div>
      <div class="header-right">
        <ng-content />
      </div>
    </header>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 24px;
        gap: 16px;
        flex-wrap: wrap;
      }
      .page-title {
        font-size: 22px;
        font-weight: 700;
        color: var(--color-text);
        margin: 0;
      }
      .page-subtitle {
        font-size: 13px;
        color: var(--color-text-muted);
        margin: 4px 0 0;
      }
      .header-right {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    `
  ]
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
}
