import { request, type BrowserContext, type Page } from '@playwright/test';
import { SEED_USERS, type SeedRole, type SeedUser } from '../fixtures/users';

/**
 * Configuration Keycloak pour les helpers d'auth.
 * Surcharge possible via variables d'environnement (cf. {@link readEnvConfig}).
 */
export interface KeycloakConfig {
  readonly baseUrl: string;
  readonly realm: string;
  readonly clientId: string;
}

/**
 * Token issu d'un Direct Access Grant Keycloak.
 * Champ {@code id_token} optionnel — Keycloak peut ne pas l'émettre selon le scope.
 */
export interface KeycloakTokenSet {
  readonly access_token: string;
  readonly refresh_token: string;
  readonly id_token?: string;
  readonly expires_in: number;
  readonly refresh_expires_in: number;
  readonly token_type: 'Bearer';
  readonly scope: string;
  readonly session_state: string;
}

/**
 * Lit la config Keycloak depuis l'environnement avec des défauts cohérents
 * avec le seed dev local.
 */
export function readEnvConfig(): KeycloakConfig {
  return {
    baseUrl: process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:8180',
    realm: process.env['E2E_KEYCLOAK_REALM'] ?? 'terangamed',
    clientId: process.env['E2E_KEYCLOAK_CLIENT'] ?? 'terangamed-frontend'
  };
}

/**
 * Effectue un Direct Access Grant sur Keycloak et retourne le tokenSet.
 *
 * <p><b>Pré-requis</b> : le client Keycloak doit avoir
 * {@code directAccessGrantsEnabled: true}. C'est déjà le cas pour
 * {@code terangamed-frontend} dans le seed dev (vérifié dans
 * {@code realm-export.json}).
 *
 * <p>Cette méthode ne passe <b>jamais</b> par le navigateur — elle parle
 * directement au {@code /token} endpoint. Cela évite la lenteur et la fragilité
 * du flow PKCE complet pour les tests qui n'ont pas besoin de tester le login.
 *
 * @throws Error si le statut HTTP n'est pas 200 (mauvais credentials, client
 *   sans DAG activé, etc.)
 */
export async function directAccessGrant(
  user: SeedUser,
  config: KeycloakConfig = readEnvConfig()
): Promise<KeycloakTokenSet> {
  const ctx = await request.newContext();
  try {
    const response = await ctx.post(
      `${config.baseUrl}/realms/${config.realm}/protocol/openid-connect/token`,
      {
        form: {
          grant_type: 'password',
          client_id: config.clientId,
          username: user.username,
          password: user.password,
          scope: 'openid profile email'
        }
      }
    );
    if (!response.ok()) {
      const body = await response.text();
      throw new Error(
        `Direct Access Grant failed for user "${user.username}" (status ${response.status()}): ${body}`
      );
    }
    return (await response.json()) as KeycloakTokenSet;
  } finally {
    await ctx.dispose();
  }
}

/**
 * Décode le payload (claims) d'un JWT — sans vérification de signature.
 * Utilisé uniquement pour extraire {@code exp} / {@code preferred_username}
 * dans les assertions de test.
 */
export function decodeJwtPayload<T = Record<string, unknown>>(token: string): T {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT — expected 3 parts');
  }
  const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(payload, 'base64').toString('utf-8')) as T;
}

/**
 * Injecte un tokenSet dans le {@code sessionStorage} du contexte navigateur, à
 * l'emplacement où {@code angular-oauth2-oidc} le persiste. Cela permet à
 * l'application Angular de démarrer en mode "déjà authentifié", sans passer
 * par l'UI Keycloak.
 *
 * <p><b>Convention de stockage</b> {@code angular-oauth2-oidc} (sessionStorage) :
 * <ul>
 *   <li>{@code access_token}</li>
 *   <li>{@code refresh_token}</li>
 *   <li>{@code id_token}</li>
 *   <li>{@code expires_at} — timestamp en millisecondes</li>
 *   <li>{@code granted_scopes}</li>
 *   <li>{@code session_state}</li>
 *   <li>{@code id_token_claims_obj} — JSON des claims</li>
 *   <li>{@code id_token_expires_at}</li>
 *   <li>{@code access_token_stored_at}</li>
 * </ul>
 *
 * @param context Contexte navigateur Playwright (page.context() ou browser.newContext())
 * @param baseUrl URL de l'origine du frontend (où le sessionStorage est attaché)
 * @param tokens TokenSet retourné par {@link directAccessGrant}
 */
export async function injectTokenInSessionStorage(
  context: BrowserContext,
  baseUrl: string,
  tokens: KeycloakTokenSet
): Promise<void> {
  const now = Date.now();
  const expiresAt = now + tokens.expires_in * 1000;
  const idTokenExpiresAt = now + tokens.refresh_expires_in * 1000;
  const idTokenClaims = tokens.id_token
    ? decodeJwtPayload(tokens.id_token)
    : decodeJwtPayload(tokens.access_token);

  // Payload typé explicitement — sinon TS strict refuse les binding elements
  // sans annotation dans le callback (qui s'exécute en contexte navigateur).
  interface InitPayload {
    readonly access: string;
    readonly refresh: string;
    readonly id: string | null;
    readonly scope: string;
    readonly session: string;
    readonly expAt: number;
    readonly idExpAt: number;
    readonly claims: Record<string, unknown>;
    readonly storedAt: number;
  }

  const payload: InitPayload = {
    access: tokens.access_token,
    refresh: tokens.refresh_token,
    id: tokens.id_token ?? null,
    scope: tokens.scope,
    session: tokens.session_state,
    expAt: expiresAt,
    idExpAt: idTokenExpiresAt,
    claims: idTokenClaims as Record<string, unknown>,
    storedAt: now
  };

  // Injection au prochain chargement de page : addInitScript s'exécute avant
  // tout script de l'app Angular, garantissant que sessionStorage est peuplé
  // avant que OAuthService ne lise son état initial.
  await context.addInitScript((p: InitPayload) => {
    try {
      sessionStorage.setItem('access_token', p.access);
      sessionStorage.setItem('refresh_token', p.refresh);
      if (p.id) sessionStorage.setItem('id_token', p.id);
      sessionStorage.setItem('granted_scopes', JSON.stringify(p.scope.split(' ')));
      sessionStorage.setItem('session_state', p.session);
      sessionStorage.setItem('expires_at', String(p.expAt));
      sessionStorage.setItem('id_token_expires_at', String(p.idExpAt));
      sessionStorage.setItem('id_token_claims_obj', JSON.stringify(p.claims));
      sessionStorage.setItem('access_token_stored_at', String(p.storedAt));
    } catch {
      // Silencieux — sessionStorage peut être indisponible dans certains contextes
      // (mode privé, frame cross-origin). Le test fera échec naturellement.
    }
  }, payload);

  // L'origine doit être déclarée pour que sessionStorage soit attaché à la
  // bonne origine — Playwright n'établit pas l'origine tant qu'on n'a pas
  // navigué. On force une navigation vide vers la baseURL avant les tests.
  // C'est le rôle du globalSetup ou des helpers loginAs() ci-dessous.
}

/**
 * Authentifie une page avec un rôle donné, sans passer par l'UI.
 *
 * <p><b>Mode rapide (DAG injection)</b> — tokenSet récupéré via Direct Access
 * Grant, puis injecté dans le sessionStorage via {@code addInitScript}.
 * ~200 ms par appel. Pratique pour la majorité des tests métier qui ont
 * besoin d'un user authentifié sans tester le flow auth lui-même.
 *
 * <p><b>Limitation</b> : {@code addInitScript} est <b>persistent par contexte</b> —
 * il réinjecte le token à <b>chaque navigation</b>. Conséquence : un test qui
 * vérifie le logout doit utiliser {@link loginAsViaUi} à la place, sinon le
 * token se réinjecte juste après le {@code logout()}.
 *
 * <p><b>Effet de bord OAuthService</b> : selon la version d'angular-oauth2-oidc
 * et le timing du {@code loadDiscoveryDocument()}, l'état interne de
 * {@code OAuthService} peut ne pas être pleinement synchronisé avec le
 * sessionStorage que nous injectons. Pour les tests qui dépendent de
 * {@code currentUser.roles} (ex: assertions sur le rôle affiché dans la
 * sidebar), préférer {@link loginAsViaUi}.
 */
export async function loginAs(
  page: Page,
  role: SeedRole,
  config: KeycloakConfig = readEnvConfig()
): Promise<void> {
  const user = SEED_USERS[role];
  const tokens = await directAccessGrant(user, config);
  const baseUrl = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';
  await injectTokenInSessionStorage(page.context(), baseUrl, tokens);
}

/**
 * Authentifie une page en passant par <b>le formulaire de login intégré</b>
 * de l'app (Resource Owner Password via {@code OAuthService.fetchTokenUsingPasswordFlow}).
 *
 * <p>Plus lent (~2-3 s par appel) mais reproduit fidèlement le flow utilisateur :
 * <ul>
 *   <li>Le tokenSet est obtenu par {@code OAuthService} lui-même → état interne
 *       synchronisé</li>
 *   <li>{@code currentUser.roles} est correctement peuplé après navigation</li>
 *   <li>Aucun {@code addInitScript} persistent → le logout fonctionne normalement</li>
 * </ul>
 *
 * <p>À utiliser pour les tests qui :
 * <ul>
 *   <li>Vérifient le logout puis re-tente une nav (sinon DAG réinjecte)</li>
 *   <li>Asserte sur {@code currentUser.roles} ou le label rôle dans l'UI</li>
 *   <li>Reproduisent un parcours user complet bout-en-bout</li>
 * </ul>
 */
export async function loginAsViaUi(page: Page, role: SeedRole): Promise<void> {
  const user = SEED_USERS[role];
  await page.goto('/login');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/dashboard/, { timeout: 15_000 });

  // Reload pour forcer la re-initialisation complète de l'AuthService via
  // APP_INITIALIZER (qui appelle {@code auth.initialize()} → lit sessionStorage
  // → peuple {@code currentUser.roles}).
  //
  // <p><b>Pourquoi ?</b> Le flow direct {@code loginWithCredentials} →
  // {@code refreshUserFromToken} peuple bien {@code currentUser} en mémoire,
  // mais la propagation du signal jusqu'aux composants lazy-loaded
  // ({@code MainLayoutComponent}, {@code SidebarComponent}) souffre d'une
  // race condition observée empiriquement : la sidebar peut être rendue
  // avant que le signal {@code currentUser} ne contienne les rôles.
  // Le reload garantit que l'app boot avec un sessionStorage déjà peuplé,
  // ce qui rend la propagation déterministe.
  await page.reload();
  await page.waitForURL(/\/dashboard/, { timeout: 15_000 });
}

// Note : pas de helper {@code saveStorageStateForRole} ici. Le mécanisme
// {@code storageState} natif de Playwright ne capture que {@code localStorage}
// + cookies, alors que {@code angular-oauth2-oidc} stocke les tokens dans
// {@code sessionStorage}. La stratégie retenue est donc d'appeler
// {@link loginAs} dans un {@code beforeEach} — ~200 ms par login, mais
// robuste et alignée avec le storage réel de l'app.
//
// Si à l'avenir l'app bascule vers {@code localStorage}, on pourra ajouter
// un helper qui pré-construit un storageState dans {@code globalSetup}.
