/**
 * Environnement PRODUCTION — substitué automatiquement par Angular CLI au build
 * `--configuration production` (cf. `fileReplacements` dans angular.json).
 *
 * <p><b>Ces valeurs sont des FALLBACKS</b> en cas d'absence de
 * `/assets/runtime-config.json` (qui est généré au démarrage du container Docker
 * via envsubst). En pratique, dès qu'on tourne via Docker, le runtime config
 * écrase ces valeurs — la même image Docker est donc déployable en local,
 * staging, et prod sans rebuild (cf. `core/config/runtime-config.ts`).
 *
 * <p>Pourquoi garder un fallback "localhost" plutôt qu'une URL prod hardcodée ?
 * <ul>
 *   <li>Si l'entrypoint Docker échoue (template manquant, variable mal posée),
 *       le SPA pointe vers localhost — détection immédiate en local.</li>
 *   <li>Une URL prod hardcodée ferait fuiter un hostname interne dans le bundle
 *       JS public.</li>
 * </ul>
 *
 * <p><b>Important</b> : `production: true` n'implique PAS `requireHttps: true` ici.
 * On utilise `requireHttps: 'remoteOnly'` dans authConfig — les hosts loopback
 * (localhost, 127.0.0.1) peuvent rester en HTTP, indispensable pour le mode
 * local-Docker. La prod réelle utilise HTTPS naturellement (issuer https://...).
 */
export const environment = {
  production: true,
  apiBaseUrl: '',
  keycloak: {
    issuer: 'http://localhost:8180/realms/terangamed',
    clientId: 'terangamed-frontend',
    redirectUri: window.location.origin,
    responseType: 'code',
    scope: 'openid profile email'
  }
};
