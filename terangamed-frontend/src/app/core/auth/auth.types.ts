/**
 * Profil utilisateur extrait du JWT Keycloak.
 *
 * <p>Mappe les claims essentiels du token. Conservé immuable côté front,
 * la source de vérité reste le serveur Keycloak.
 */
export interface AuthUser {
  /** Stable JWT subject — utilisé pour matcher Patient.keycloakSubject côté backend. */
  readonly sub: string;
  readonly username: string;
  readonly email: string | null;
  readonly firstName: string | null;
  readonly lastName: string | null;
  readonly roles: readonly TerangaMedRole[];
}

/**
 * Rôles métier — déclarés dans Keycloak realm 'terangamed' et mappés depuis
 * realm_access.roles du JWT. Tout autre rôle est filtré.
 */
export const TERANGAMED_ROLES = ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT'] as const;
export type TerangaMedRole = (typeof TERANGAMED_ROLES)[number];

export function isTerangaMedRole(value: string): value is TerangaMedRole {
  return (TERANGAMED_ROLES as readonly string[]).includes(value);
}

/**
 * Construit le nom d'affichage à partir du profil JWT.
 * Préfère "Prénom Nom" si disponibles, sinon username, sinon "Utilisateur".
 */
export function displayNameOf(user: AuthUser | null): string {
  if (!user) {
    return 'Utilisateur';
  }
  const full = [user.firstName, user.lastName].filter((s): s is string => !!s).join(' ').trim();
  return full || user.username || 'Utilisateur';
}

/**
 * Renvoie les initiales (1 ou 2 caractères) pour l'avatar — basées sur le
 * displayName.
 */
export function initialsOf(user: AuthUser | null): string {
  if (!user) {
    return '?';
  }
  const name = displayNameOf(user);
  const parts = name.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}
