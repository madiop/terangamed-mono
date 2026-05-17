import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

import { getAuthConfig } from './auth.config';
import { AuthUser, TerangaMedRole, isTerangaMedRole } from './auth.types';

/**
 * Service d'authentification — façade au-dessus de {@link OAuthService}.
 *
 * <h3>Cycle de vie</h3>
 * 1. {@link initialize} est appelé au bootstrap (APP_INITIALIZER) :
 *    - charge le discovery document Keycloak
 *    - tente un login silencieux via le code dans l'URL si présent
 *    - met à jour les signals (currentUser, roles, isAuthenticated)
 * 2. Le SPA peut alors démarrer le routing — les guards lisent les signals.
 *
 * <h3>Signals exposés</h3>
 * <ul>
 *   <li>{@link currentUser} : profil utilisateur ou {@code null}</li>
 *   <li>{@link roles} : tableau des rôles TerangaMed extraits du JWT</li>
 *   <li>{@link isAuthenticated} : computed signal — true si user présent</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  /** Profil utilisateur courant — null si non connecté. */
  readonly currentUser = signal<AuthUser | null>(null);

  /** Rôles TerangaMed extraits du JWT. */
  readonly roles = computed<readonly TerangaMedRole[]>(() => this.currentUser()?.roles ?? []);

  /** Authentifié si on a un user ET un access token valide. */
  readonly isAuthenticated = computed<boolean>(
    () => this.currentUser() !== null && this.oauth.hasValidAccessToken()
  );

  /**
   * Initialisation de l'auth — à appeler avant le démarrage du routing.
   * Retourne une promise pour APP_INITIALIZER.
   *
   * <p>On utilise UNIQUEMENT {@code loadDiscoveryDocument()} (pas
   * {@code loadDiscoveryDocumentAndTryLogin}) car nous sommes en Password Flow,
   * pas en Authorization Code Flow. {@code TryLogin} intercepterait un
   * éventuel {@code ?code=...} dans l'URL et tenterait un flow incompatible.
   *
   * <p>Si un access token est déjà présent en session storage (rechargement
   * de page après login), on reconstruit simplement {@link AuthUser} à partir
   * de lui — pas de redirection.
   */
  async initialize(): Promise<void> {
    this.oauth.configure(getAuthConfig());
    try {
      await this.oauth.loadDiscoveryDocument();
      // Restaure l'utilisateur si un access token valide est encore en session storage
      this.refreshUserFromToken();
    } catch (err) {
      console.error('Auth — échec du discovery : ', err);
      this.currentUser.set(null);
    }
  }

  /**
   * Démarre le flow Authorization Code + PKCE (redirection Keycloak).
   * <p>Conservé comme alternative — non utilisé en V1 puisque la page de login
   * est embarquée dans l'app via {@link loginWithCredentials}.
   */
  login(): void {
    this.oauth.initCodeFlow();
  }

  /**
   * Connexion via Resource Owner Password Credentials (Password Flow Keycloak).
   * <p>L'utilisateur reste sur l'app : on POST username/password directement
   * au token endpoint Keycloak. Pour fonctionner, le client Keycloak doit avoir
   * {@code directAccessGrantsEnabled: true} (déjà configuré dans realm-export).
   *
   * <p><b>Sécurité</b> : ce flow est acceptable pour une SPA first-party (même
   * org que l'IdP) — le frontend manipule les credentials uniquement le temps
   * de l'appel réseau, sans les stocker. Pour des intégrations tierces ou
   * fédération externe, préférer Authorization Code + PKCE.
   *
   * @throws Error en cas d'échec d'authentification (Keycloak retourne 401)
   */
  async loginWithCredentials(username: string, password: string): Promise<void> {
    // fetchTokenUsingPasswordFlow utilise scope=openid + setup discovery préalable
    await this.oauth.fetchTokenUsingPasswordFlow(username, password);
    this.refreshUserFromToken();
    if (!this.isAuthenticated()) {
      throw new Error('Authentification échouée — token invalide après login');
    }
  }

  /**
   * Déconnexion locale — clear tokens + currentUser + navigation vers /login.
   *
   * <p>Le {@code true} passé à {@code oauth.logOut()} évite la redirection
   * vers le {@code end_session_endpoint} Keycloak (sinon l'utilisateur sortirait
   * de l'app). On navigue explicitement vers {@code /login} car le router
   * Angular ne ré-évalue les guards que lors d'une navigation — sans navigate(),
   * l'utilisateur resterait sur la page courante avec un état "déconnecté".
   */
  logout(): void {
    this.oauth.logOut(true);
    this.currentUser.set(null);
    void this.router.navigate(['/login']);
  }

  /** Vérifie qu'au moins un des rôles attendus est présent. */
  hasAnyRole(required: readonly TerangaMedRole[]): boolean {
    if (required.length === 0) {
      return true;
    }
    const userRoles = this.roles();
    return required.some((r) => userRoles.includes(r));
  }

  /** Renvoie l'access token courant ou null s'il n'y en a pas. */
  accessToken(): string | null {
    const token = this.oauth.getAccessToken();
    return token && this.oauth.hasValidAccessToken() ? token : null;
  }

  // ─────────────────────── Helpers privés ───────────────────────

  /**
   * Reconstruit {@link AuthUser} depuis l'access token (où Keycloak place
   * {@code realm_access.roles}) + l'ID token (claims user-friendly).
   *
   * <p><b>Pourquoi décoder l'access token côté front ?</b> Les rôles ne sont pas
   * inclus dans l'ID token Keycloak par défaut — uniquement dans l'access token.
   * On le décode (sans vérification de signature, qui est le rôle du backend)
   * pour exposer les rôles aux guards/UI. La sécurité repose sur Keycloak +
   * resource server backend, jamais sur ce parsing frontend.
   */
  private refreshUserFromToken(): void {
    if (!this.oauth.hasValidAccessToken()) {
      this.currentUser.set(null);
      return;
    }

    const idClaims = (this.oauth.getIdentityClaims() ?? {}) as Record<string, unknown>;
    const accessClaims = decodeJwtPayload(this.oauth.getAccessToken()) ?? {};

    // Rôles : realm_access.roles (Keycloak realm-level) — décodés depuis l'access token
    const realmAccess = accessClaims['realm_access'] as { roles?: string[] } | undefined;
    const rolesFromToken = (realmAccess?.roles ?? []).filter(isTerangaMedRole);

    // Identité : préférer les claims ID token (officiels OIDC), fallback sur access token
    const claims = { ...accessClaims, ...idClaims };

    const user: AuthUser = {
      sub: String(claims['sub'] ?? ''),
      username: String(claims['preferred_username'] ?? ''),
      email: typeof claims['email'] === 'string' ? (claims['email'] as string) : null,
      firstName: typeof claims['given_name'] === 'string' ? (claims['given_name'] as string) : null,
      lastName: typeof claims['family_name'] === 'string' ? (claims['family_name'] as string) : null,
      roles: rolesFromToken
    };
    this.currentUser.set(user);
  }
}

/**
 * Décode le payload d'un JWT (base64url → JSON). Aucune vérification de
 * signature — c'est le rôle du backend (resource server). Ici on extrait
 * juste les claims pour l'UI.
 */
function decodeJwtPayload(token: string | null): Record<string, unknown> | null {
  if (!token) {
    return null;
  }
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    // base64url → base64 standard
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    // Padding manquant si longueur % 4 != 0
    const padding = '='.repeat((4 - (padded.length % 4)) % 4);
    const json = atob(padded + padding);
    return JSON.parse(json) as Record<string, unknown>;
  } catch (err) {
    console.warn('AuthService — décodage du JWT impossible', err);
    return null;
  }
}
