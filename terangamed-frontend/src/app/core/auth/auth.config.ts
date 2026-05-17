import { AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '@env/environment';
import { getKeycloakIssuer } from '@core/config/runtime-config';

/**
 * Construit la {@link AuthConfig} angular-oauth2-oidc.
 *
 * <p><b>Pourquoi un factory et pas une constante ?</b> L'issuer Keycloak vient
 * de la config runtime (chargée depuis `/assets/runtime-config.json` avant le
 * bootstrap Angular). Un `const` serait gelé au chargement du module, AVANT
 * que le runtime config ne soit disponible. La fonction est appelée par
 * `AuthService.initialize()` au moment de la configuration de OAuthService.
 *
 * <p><b>Flow</b> : Authorization Code + PKCE pour le redirect classique, ET
 * Password Flow pour le form de login intégré (cf. `loginWithCredentials`).
 *
 * <p><b>requireHttps: 'remoteOnly'</b> — relâche la contrainte HTTPS pour les
 * hosts loopback (localhost, 127.0.0.1). Indispensable pour le mode local-Docker
 * où Keycloak tourne en HTTP sur :8180. Pour les déploiements distants
 * (staging, prod), HTTPS reste obligatoire — `angular-oauth2-oidc` rejettera
 * une URL externe `http://`.
 */
export function getAuthConfig(): AuthConfig {
  return {
    issuer: getKeycloakIssuer(),
    redirectUri: environment.keycloak.redirectUri,
    clientId: environment.keycloak.clientId,
    responseType: environment.keycloak.responseType,
    scope: environment.keycloak.scope,
    showDebugInformation: !environment.production,
    requireHttps: 'remoteOnly',
    clockSkewInSec: 60,
    postLogoutRedirectUri: environment.keycloak.redirectUri
  };
}
