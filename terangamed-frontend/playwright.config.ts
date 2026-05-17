import { defineConfig, devices } from '@playwright/test';

/**
 * Configuration Playwright pour les tests E2E TerangaMed.
 *
 * <h2>Stratégie</h2>
 * <ul>
 *   <li><b>Login hybride</b> : un test smoke fait le login complet via l'UI Keycloak ;
 *       tous les autres tests réutilisent un {@code storageState} produit par
 *       {@code globalSetup} via Direct Access Grant (rapide et stable).</li>
 *   <li><b>Sériel</b> ({@code workers: 1}) — pas d'interférence entre tests, plus simple
 *       à debugger. Parallélisation prévue plus tard si nécessaire.</li>
 *   <li><b>Données</b> : seed dev backend (V900*) fournit les comptes utilisateurs
 *       et quelques médecins/patients de référence. Chaque test crée ses propres
 *       données métier via API et les nettoie en {@code afterEach}.</li>
 * </ul>
 *
 * <h2>Lancement</h2>
 * <ol>
 *   <li>{@code docker compose up -d} — démarre back + Keycloak + Kafka</li>
 *   <li>{@code npm start} — démarre le frontend Angular sur 4200 (autre terminal)</li>
 *   <li>{@code npm run test:e2e} — lance les tests Playwright</li>
 * </ol>
 *
 * <p>Variables d'environnement supportées :
 * <ul>
 *   <li>{@code E2E_BASE_URL} — URL du frontend (défaut http://localhost:4200)</li>
 *   <li>{@code E2E_KEYCLOAK_URL} — URL de Keycloak (défaut http://localhost:8180)</li>
 *   <li>{@code E2E_KEYCLOAK_REALM} — Realm (défaut terangamed)</li>
 *   <li>{@code E2E_KEYCLOAK_CLIENT} — Client ID (défaut terangamed-frontend)</li>
 *   <li>{@code CI} — si défini, active 1 retry et désactive l'UI mode</li>
 * </ul>
 */
export default defineConfig({
  testDir: './e2e/tests',

  // Timeout par test — 30s suffisent pour les flows métier ; à augmenter si le seed
  // backend prend plus de temps à démarrer.
  timeout: 30_000,

  // Timeout par expect() — 5s par défaut pour les assertions DOM avec retry.
  expect: { timeout: 5_000 },

  // Échec si on a un test marqué .only en CI — sécurité contre les commits accidentels.
  forbidOnly: !!process.env['CI'],

  // 1 retry en CI (réseau / startup variabilité), 0 en local pour ne pas masquer
  // les flakes au développeur.
  retries: process.env['CI'] ? 1 : 0,

  // Sériel — workers: 1 — décision validée en design 10A.
  workers: 1,

  // Reporter — HTML local + line concis sur stdout.
  reporter: process.env['CI']
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'on-failure' }]],

  // Setup global : login DAG → storageStates écrits dans e2e/.auth/
  globalSetup: require.resolve('./e2e/setup/global-setup'),

  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',

    // Capture artefacts uniquement sur échec — réduit le poids des runs verts.
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',

    // Locale FR — cohérent avec l'UI (date-fns/locale fr).
    locale: 'fr-FR',
    timezoneId: 'Africa/Dakar',

    // Action timeout — laisse le DOM Angular se stabiliser sur les pages avec
    // beaucoup de transitions Material.
    actionTimeout: 10_000
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
    // Smoke cross-browser optionnel — décommenter pour vérifier WebKit/Firefox :
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] }, grep: /@smoke/ },
    // { name: 'webkit',  use: { ...devices['Desktop Safari']   }, grep: /@smoke/ }
  ],

  // Optionnel : démarrer le frontend automatiquement (commenté car on assume
  // que l'utilisateur a déjà `npm start` en cours dans un autre terminal).
  // webServer: {
  //   command: 'npm start',
  //   url: 'http://localhost:4200',
  //   reuseExistingServer: !process.env['CI'],
  //   timeout: 120_000
  // }
});
