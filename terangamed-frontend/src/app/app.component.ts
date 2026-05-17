import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Shell racine. Le layout (sidebar/topbar) est porté par MainLayoutComponent
 * dans les routes — ainsi les pages publiques (login) peuvent avoir leur
 * propre layout sans la sidebar.
 */
@Component({
  selector: 'tm-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {}
