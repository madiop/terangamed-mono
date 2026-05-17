import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './sidebar/sidebar.component';

/**
 * Layout principal — sidebar à gauche + main scrollable à droite.
 *
 * <p>Pas de topbar global : chaque page utilise {@code <tm-page-header>}
 * pour son propre titre et ses actions.
 */
@Component({
  selector: 'tm-main-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-layout">
      <tm-sidebar />
      <main class="app-main">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [
    `
      .app-layout {
        display: flex;
        min-height: 100vh;
        background: var(--color-bg);
      }
      .app-main {
        flex: 1;
        overflow-y: auto;
        min-width: 0;
        padding: 24px 32px;
      }
    `
  ]
})
export class MainLayoutComponent {}
