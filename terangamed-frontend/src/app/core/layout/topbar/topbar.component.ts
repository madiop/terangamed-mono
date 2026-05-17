// ─────────────────────────────────────────────────────────────────────────────
// DEPRECATED — Composant retiré du layout (refonte design Phase D).
//
// Le topbar global a été supprimé au profit du composant réutilisable
// {@code <tm-page-header>} (src/app/shared/ui/page-header). Chaque page
// définit elle-même son titre et ses actions.
//
// Le profil utilisateur (avatar + nom + rôle + logout) est désormais en
// footer de la sidebar (src/app/core/layout/sidebar).
//
// Ce fichier est conservé temporairement pour éviter les imports cassés.
// Sera supprimé en Phase 9.10.
// ─────────────────────────────────────────────────────────────────────────────
import { ChangeDetectionStrategy, Component } from '@angular/core';

/** @deprecated Voir tm-page-header dans @shared/ui/page-header */
@Component({
  selector: 'tm-topbar-deprecated',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: ''
})
export class TopbarComponent {}
