# Tests E2E — TerangaMed

Tests end-to-end basés sur **Playwright** pour valider les parcours métier critiques de bout en bout (frontend Angular + backend Spring Boot + Keycloak).

## Architecture

```
e2e/
├── auth/
│   └── auth.helpers.ts      # Direct Access Grant + injection sessionStorage
├── fixtures/
│   └── users.ts             # Catalogue des comptes Keycloak seed dev
├── setup/
│   └── global-setup.ts      # Pre-flight checks (frontend, Keycloak, DAG)
├── tests/
│   └── *.spec.ts            # Suites de tests E2E
├── tsconfig.json            # TS config dédiée (séparée du build Angular)
└── README.md
```

## Stratégie d'authentification

**Login hybride** :
- Un test `@smoke login_via_keycloak_ui_works` couvre le flux complet via UI Keycloak
- Tous les autres tests utilisent `loginAs(page, role)` qui fait un **Direct Access Grant** (POST `/token`) puis injecte le tokenSet dans `sessionStorage` via `addInitScript`. ~200 ms par login.

**Pré-requis** : le client Keycloak `terangamed-frontend` doit avoir `directAccessGrantsEnabled: true` (déjà le cas dans `realm-export.json`).

## Lancer les tests

### Pré-requis (un seul setup)

```bash
# Une fois — installation des navigateurs Playwright
cd terangamed-frontend
npm install
npx playwright install chromium
```

### À chaque session

Trois processus en cours dans des terminaux différents :

```bash
# Terminal 1 — backend + Keycloak + Kafka
cd terangamed-backend
docker compose up -d

# Terminal 2 — frontend Angular
cd terangamed-frontend
npm start

# Terminal 3 — tests E2E
cd terangamed-frontend
npm run test:e2e
```

### Scripts npm disponibles

| Script | Usage |
|--------|-------|
| `npm run test:e2e` | Lance toute la suite (Chromium, série) |
| `npm run test:e2e:smoke` | Uniquement les tests `@smoke` (rapide, valide le setup) |
| `npm run test:e2e:ui` | Mode UI interactif Playwright (excellent pour debug) |
| `npm run test:e2e:debug` | Lance avec inspector Playwright (`PWDEBUG=1`) |
| `npm run test:e2e:report` | Affiche le rapport HTML du dernier run |

## Variables d'environnement

| Variable | Défaut | Description |
|----------|--------|-------------|
| `E2E_BASE_URL` | `http://localhost:4200` | URL du frontend |
| `E2E_KEYCLOAK_URL` | `http://localhost:8180` | URL de Keycloak |
| `E2E_KEYCLOAK_REALM` | `terangamed` | Realm |
| `E2E_KEYCLOAK_CLIENT` | `terangamed-frontend` | Client ID |
| `CI` | (non défini) | Si défini : 1 retry + reporter `github` |

Exemple : tests contre un environnement de staging avec Keycloak distant :

```bash
E2E_BASE_URL=https://staging.terangamed.local \
E2E_KEYCLOAK_URL=https://kc.staging.terangamed.local \
npm run test:e2e
```

## Comptes utilisés

Les tests utilisent les **3 comptes seed dev** déjà définis dans `realm-export.json` :

| Username | Password | Rôle |
|----------|----------|------|
| `admin` | `admin` | ADMIN |
| `dr.martin` | `doctor123` | DOCTOR |
| `reception` | `reception` | RECEPTIONIST |

Ces credentials sont en clair dans `e2e/fixtures/users.ts` — c'est volontaire car ils n'existent que dans l'environnement de dev local. **Jamais** ils ne doivent être utilisés en staging ou prod.

## Conventions d'écriture des tests

### Pattern type d'un test métier

```ts
import { test, expect } from '@playwright/test';
import { loginAs } from '../auth/auth.helpers';

test.describe('Module Patients', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'DOCTOR');
  });

  test('création patient → redirection vers détail', async ({ page }) => {
    await page.goto('/patients/new');
    // … remplir le form, submit, vérifier la nav
  });
});
```

### Données de test

Chaque test crée ses propres données via API REST et les nettoie en `afterEach`. **Pas d'interaction entre tests** — chaque test doit être indépendant et idempotent.

### Tags

- `@smoke` — tests d'infrastructure (login, routing, pre-flight)
- (autres tags à venir : `@critical`, `@slow`)

## Dépannage

| Symptôme | Cause probable |
|----------|----------------|
| `Frontend non joignable` | `npm start` pas démarré, ou port 4200 occupé |
| `Keycloak non joignable` | `docker compose up -d keycloak` à faire |
| `Login DAG échoué pour "admin"` | Mot de passe seed changé ; vérifier `realm-export.json` |
| Tests qui passent en local mais pas en CI | Souvent un timing — augmenter `actionTimeout` dans `playwright.config.ts` ou utiliser `await expect(...).toBeVisible()` plutôt que `await page.click(...)` |
| `module not found '@playwright/test'` | `npm install` ou `npm i -D @playwright/test` |

## TODO (prochaines étapes)

- [x] 10A.3 — Setup config + helpers (cette livraison)
- [ ] 10A.4a — Parcours auth complet (login UI Keycloak + logout)
- [ ] 10A.4b — Parcours Patients (CRUD + recherche)
- [ ] 10A.4c — Parcours RDV (CRUD + workflow statut)
- [ ] 10A.4d — Parcours Consultation + Prescription
- [ ] 10A.4e — Parcours Admin Staff (CRUD + transitions)
- [ ] 10A.5 — Coverage report consolidé (back + front + E2E)
