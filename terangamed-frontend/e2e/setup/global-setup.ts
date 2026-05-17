import { request } from '@playwright/test';
import { readEnvConfig, directAccessGrant } from '../auth/auth.helpers';
import { SEED_USERS, allUsers } from '../fixtures/users';

/**
 * Global setup Playwright — exécuté une seule fois avant tous les tests.
 *
 * <h3>Vérifications</h3>
 * <ol>
 *   <li><b>Frontend joignable</b> : GET sur baseURL → 200</li>
 *   <li><b>Keycloak joignable</b> : OIDC discovery → 200</li>
 *   <li><b>Direct Access Grant fonctionnel</b> : login DAG sur le user ADMIN → tokenSet</li>
 *   <li><b>Comptes seed valides</b> : login DAG sur tous les autres users seed</li>
 * </ol>
 *
 * <p>Si une vérification échoue, le run échoue avec un message actionnable
 * (ex: "Démarrer Keycloak avec `docker compose up -d keycloak`"). On préfère
 * un échec rapide en setup à des erreurs cryptiques pendant les tests.
 *
 * <p><b>Pourquoi pas de pré-construction de storageState ici ?</b> {@code angular-oauth2-oidc}
 * stocke les tokens dans {@code sessionStorage} qui n'est pas supporté nativement
 * par {@code storageState} Playwright (qui ne capture que {@code localStorage}
 * + cookies). Les tests utilisent donc {@link loginAs} dans un beforeEach,
 * qui fait un DAG (~200ms) et injecte via {@code addInitScript}. C'est
 * légèrement plus lent que storageState mais plus robuste.
 */
async function globalSetup(): Promise<void> {
  const config = readEnvConfig();
  const baseUrl = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';

  // eslint-disable-next-line no-console
  console.log('\n[playwright] Pre-flight checks…');
  // eslint-disable-next-line no-console
  console.log(`  frontend  : ${baseUrl}`);
  // eslint-disable-next-line no-console
  console.log(`  keycloak  : ${config.baseUrl}/realms/${config.realm}`);

  const ctx = await request.newContext();
  try {
    // 1. Frontend joignable — Angular dev server répond toujours en 200 sur '/'.
    try {
      const r = await ctx.get(baseUrl, { timeout: 5_000 });
      if (!r.ok()) {
        throw new Error(`Frontend HTTP ${r.status()}`);
      }
    } catch (err) {
      throw new Error(
        `[playwright] Frontend non joignable sur ${baseUrl} — démarre-le avec ` +
          `'npm start' dans un autre terminal puis relance les tests.\n  cause: ${(err as Error).message}`
      );
    }

    // 2. Keycloak OIDC discovery
    try {
      const r = await ctx.get(
        `${config.baseUrl}/realms/${config.realm}/.well-known/openid-configuration`,
        { timeout: 5_000 }
      );
      if (!r.ok()) {
        throw new Error(`Keycloak HTTP ${r.status()}`);
      }
    } catch (err) {
      throw new Error(
        `[playwright] Keycloak non joignable sur ${config.baseUrl} — démarre-le avec ` +
          `'docker compose up -d keycloak' puis relance les tests.\n  cause: ${(err as Error).message}`
      );
    }

    // 3 & 4. DAG sur tous les users seed — détecte un seed corrompu / mots de passe changés
    for (const user of allUsers()) {
      try {
        await directAccessGrant(user, config);
      } catch (err) {
        throw new Error(
          `[playwright] Login DAG échoué pour le user seed "${user.username}". ` +
            `Vérifie que le realm Keycloak est bien importé et que le client ` +
            `"${config.clientId}" a 'directAccessGrantsEnabled: true'.\n  cause: ${(err as Error).message}`
        );
      }
    }

    // eslint-disable-next-line no-console
    console.log(
      `  users seed: ${allUsers().length} users authentifiés (${Object.keys(SEED_USERS).join(', ')})`
    );
    // eslint-disable-next-line no-console
    console.log('[playwright] Pre-flight OK — démarrage des tests.\n');
  } finally {
    await ctx.dispose();
  }
}

export default globalSetup;
