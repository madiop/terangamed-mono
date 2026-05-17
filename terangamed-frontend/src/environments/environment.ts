/**
 * Environnement DEV — utilisé par `npm start` (ng serve sur :4200, proxy vers :8080).
 *
 * <p>Pour les builds Docker (local-stack, staging, prod), on n'utilise PAS ce
 * fichier directement : Angular substitue `environment.prod.ts` au build, et
 * les URLs externes sont surchargées à l'exécution via
 * `/assets/runtime-config.json` (cf. `core/config/runtime-config.ts`).
 *
 * <p>Les valeurs ci-dessous servent donc UNIQUEMENT :
 * <ul>
 *   <li>en `ng serve` (le runtime-config.json dev est dans src/assets/)</li>
 *   <li>comme fallback si le runtime-config.json est absent ou cassé</li>
 * </ul>
 */
export const environment = {
  production: false,
  apiBaseUrl: '',
  keycloak: {
    issuer: 'http://localhost:8180/realms/terangamed',
    clientId: 'terangamed-frontend',
    redirectUri: window.location.origin,
    responseType: 'code',
    scope: 'openid profile email'
  }
};
