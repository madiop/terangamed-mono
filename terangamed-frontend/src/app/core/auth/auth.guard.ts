import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { TerangaMedRole } from './auth.types';

/**
 * Guard universel — exige une session authentifiée, sinon redirige vers /login.
 *
 * <p>Usage :
 * <pre>
 * { path: 'dashboard', canActivate: [authGuard], ... }
 * </pre>
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

/**
 * Crée un guard contraint à des rôles. Si l'utilisateur n'a aucun des rôles
 * attendus, redirige vers {@code /unauthorized}.
 *
 * <p>Usage :
 * <pre>
 * { path: 'admin', canActivate: [authGuard, roleGuard(['ADMIN'])], ... }
 * </pre>
 */
export const roleGuard = (allowedRoles: readonly TerangaMedRole[]): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    if (auth.hasAnyRole(allowedRoles)) {
      return true;
    }
    return router.createUrlTree(['/unauthorized']);
  };
};
