import { environment } from '@env/environment';

/**
 * Configuration runtime — chargée depuis `/assets/runtime-config.json` AVANT
 * le bootstrap Angular (cf. {@link bootstrap} dans `main.ts`).
 *
 * <p>Permet à <b>une seule image Docker</b> (build prod) de tourner contre
 * n'importe quel Keycloak / API gateway sans rebuild :
 * <ul>
 *   <li>en local-Docker : `KEYCLOAK_ISSUER=http://localhost:8180/realms/terangamed`</li>
 *   <li>en staging     : `KEYCLOAK_ISSUER=https://auth.staging.example/realms/terangamed`</li>
 *   <li>en prod        : `KEYCLOAK_ISSUER=https://auth.prod.example/realms/terangamed`</li>
 * </ul>
 *
 * <p>Le fichier JSON est généré au démarrage du container par
 * `nginx/docker-entrypoint.sh` via {@code envsubst} sur un template ; en
 * dev (`ng serve`), il est servi depuis `src/assets/runtime-config.json`.
 *
 * <p><b>Pourquoi un JSON et pas une variable globale inlinée dans index.html ?</b>
 * Le JSON est traité comme un asset statique → mis en cache par le navigateur
 * avec une heuristique courte (no-store côté loader), et surtout indépendant
 * du bundle JS (qui a un hash de fingerprint et un cache 1 an). Un changement
 * de config en prod ne nécessite donc pas de purger les caches CDN du bundle.
 */
export interface RuntimeConfig {
  /** Issuer OIDC complet (avec `/realms/<name>`). */
  keycloakIssuer?: string;
  /** Base URL des appels API (vide si même origine que le SPA). */
  apiBaseUrl?: string;
}

declare global {
  interface Window {
    __terangaRuntimeConfig?: RuntimeConfig;
  }
}

/**
 * Récupère le runtime-config.json. En cas d'erreur (404, JSON invalide,
 * réseau...) on retourne `{}` — les helpers `getKeycloakIssuer()` /
 * `getApiBaseUrl()` retomberont sur les valeurs hardcodées dans `environment.ts`.
 *
 * <p>Filtre les valeurs sentinelles non substituées par {@code envsubst}
 * (variable manquante → la string reste sous forme `${VAR}` ou vide).
 */
export async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  try {
    const resp = await fetch('/assets/runtime-config.json', { cache: 'no-store' });
    if (!resp.ok) {
      return {};
    }
    const raw = (await resp.json()) as Record<string, unknown>;
    const cleaned: RuntimeConfig = {};
    for (const [k, v] of Object.entries(raw)) {
      if (typeof v !== 'string') continue;
      // envsubst laisse `${FOO}` tel quel si FOO n'est pas dans l'environnement
      if (v.startsWith('${') || v === '') continue;
      (cleaned as Record<string, string>)[k] = v;
    }
    return cleaned;
  } catch {
    return {};
  }
}

/** Lit la config runtime déjà chargée (ou `{}` si bootstrap pas encore passé). */
export function getRuntimeConfig(): RuntimeConfig {
  return window.__terangaRuntimeConfig ?? {};
}

/** Issuer Keycloak effectif — runtime override > environment fallback. */
export function getKeycloakIssuer(): string {
  return getRuntimeConfig().keycloakIssuer ?? environment.keycloak.issuer;
}

/** Base URL API effective — runtime override > environment fallback. */
export function getApiBaseUrl(): string {
  return getRuntimeConfig().apiBaseUrl ?? environment.apiBaseUrl;
}
