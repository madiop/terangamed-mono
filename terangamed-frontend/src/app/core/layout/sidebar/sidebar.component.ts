import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { TerangaMedRole, displayNameOf, initialsOf } from '@core/auth/auth.types';

interface NavItem {
  readonly icon: string;
  readonly label: string;
  readonly route: string;
  /** Roles autorisés à voir l'item — undefined = tout user authentifié. */
  readonly roles?: readonly TerangaMedRole[];
}

const ROLE_LABELS: Record<TerangaMedRole, string> = {
  ADMIN: 'Administrateur',
  DOCTOR: 'Médecin',
  RECEPTIONIST: 'Réceptionniste',
  PATIENT: 'Patient'
};

/**
 * Sidebar de navigation — matche le design de référence.
 *
 * <p>Structure :
 * <ul>
 *   <li>Logo (icône bleue + texte "TerangaMed" + tagline)</li>
 *   <li>Navigation filtrée par rôle (computed)</li>
 *   <li>Footer : avatar + nom + rôle + bouton logout</li>
 * </ul>
 */
@Component({
  selector: 'tm-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  private readonly auth = inject(AuthService);

  readonly currentUser = this.auth.currentUser;
  readonly displayName = computed(() => displayNameOf(this.auth.currentUser()));
  readonly initials = computed(() => initialsOf(this.auth.currentUser()));
  readonly roleLabel = computed(() => {
    const roles = this.auth.roles();
    return roles[0] ? ROLE_LABELS[roles[0]] : '';
  });

  /**
   * Items globaux — l'attribut {@code roles} filtre dynamiquement.
   *
   * <p>Note ordre : "Personnel" est placé juste sous "Rendez-vous" pour
   * regrouper les modules de gestion (cf. décision 9.7 design). L'icône
   * {@code badge} (badge professionnel) a été retenue car {@code medical_services}
   * est déjà utilisée pour Consultations — éviter le doublon visuel.
   */
  readonly navItems: readonly NavItem[] = [
    { icon: 'dashboard', label: 'Tableau de bord', route: '/dashboard' },
    {
      icon: 'people',
      label: 'Patients',
      route: '/patients',
      roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST']
    },
    {
      icon: 'calendar_today',
      label: 'Rendez-vous',
      route: '/appointments',
      roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT']
    },
    {
      icon: 'badge',
      label: 'Personnel',
      route: '/admin/staff',
      roles: ['ADMIN']
    },
    {
      icon: 'medical_services',
      label: 'Consultations',
      route: '/consultations',
      roles: ['ADMIN', 'DOCTOR', 'PATIENT']
    },
    {
      icon: 'receipt_long',
      label: 'Facturation',
      route: '/billing',
      roles: ['ADMIN', 'RECEPTIONIST', 'PATIENT']
    },
    {
      icon: 'folder_open',
      label: 'Documents',
      route: '/documents',
      roles: ['ADMIN', 'DOCTOR']
    }
  ];

  /** Items visibles pour l'utilisateur courant. */
  readonly visibleItems = computed(() =>
    this.navItems.filter((item) => !item.roles || this.auth.hasAnyRole(item.roles))
  );

  logout(): void {
    this.auth.logout();
  }
}
