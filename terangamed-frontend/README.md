# TerangaMed — Frontend

Application Angular 17 (standalone components + signals) pour la gestion de cabinet médical.
Frontend du projet TerangaMed.

## Stack

- **Angular 17** (standalone components, signals, control flow blocks)
- **Angular Material 17** (theming custom — palette TerangaMed)
- **TailwindCSS 3** (utilities pour layouts/spacing)
- **angular-oauth2-oidc** (Authorization Code + PKCE Keycloak)
- **angular-calendar** (calendrier hebdomadaire RDV)
- **Jest** (tests unitaires)
- **TypeScript strict**

## Prérequis

- **Node.js** 20+ LTS (`node -v` doit afficher v20.x ou plus)
- **npm** 10+ (livré avec Node 20)

## Installation

```bash
cd terangamed-frontend
npm install
```

## Démarrage en dev

Le backend doit tourner en local (Gateway sur :8080, Keycloak sur :8180) :

```bash
# 1. Démarrer le backend
cd ../terangamed-backend/docker && docker compose up -d

# 2. Démarrer le frontend (proxy /api → :8080 via proxy.conf.json)
cd ../../terangamed-frontend && npm start
```

→ Application disponible sur **http://localhost:4200**

## Scripts npm

| Commande | Description |
|---|---|
| `npm start` | Dev server (port 4200, proxy /api activé) |
| `npm run build:prod` | Build optimisé production → `dist/` |
| `npm test` | Tests Jest unitaires |
| `npm run test:watch` | Tests en mode watch |
| `npm run test:coverage` | Tests + rapport de couverture |
| `npm run lint` | ESLint (TypeScript + templates HTML) |
| `npm run lint:fix` | ESLint avec corrections automatiques |
| `npm run format` | Prettier sur tous les sources |

## Structure du projet

```
src/
├── main.ts                    # Bootstrap Angular standalone
├── styles.scss                # Tailwind + Material theme + design tokens
├── environments/              # Config dev / prod
└── app/
    ├── app.component.ts       # Shell <router-outlet />
    ├── app.config.ts          # Providers globaux (router, http, animations, locale)
    ├── app.routes.ts          # Routes top-level
    │
    ├── core/                  # Transversal (auth, layout, http, services)
    │   └── layout/
    │       ├── main-layout.component.ts
    │       ├── sidebar/sidebar.component.ts
    │       └── topbar/topbar.component.ts
    │
    ├── shared/                # Composants/pipes/directives partagés
    ├── api/                   # Clients HTTP typés vers les microservices
    └── features/              # Modules métier (lazy-loaded)
        └── dashboard/
            └── dashboard-page.component.ts
```

## Design system

Palette de couleurs alignée sur le design "Clinique Médical".

- Sidebar : `#1B3358` (bleu marine)
- Primary : `#2563EB` (boutons, KPI numbers, liens)
- Background : `#F5F7FA`
- Surface (cards) : `#FFFFFF`
- Typographie : **Inter** (Google Fonts)
- Coins arrondis : 12px (cards), 8px (boutons)

Tokens CSS dans `src/styles.scss` (`--ts-*`) — accessibles via Tailwind ET CSS pur.

## Roadmap (frontend)

| Étape | Contenu | Statut |
|---|---|---|
| 9.0 | Conception (architecture + design system) | ✅ |
| 9.1 | Foundation + layout sidebar/topbar | ✅ |
| 9.2 | Auth Keycloak (PKCE) + AuthGuard + JwtInterceptor | ⏳ |
| 9.3 | Dashboard complet (KPI + planning + dossier patient) | ⏳ |
| 9.4 | Module Patients | ⏳ |
| 9.5 | Module Rendez-vous (calendrier) | ⏳ |
| 9.6 | Module Consultations + Dossier médical | ⏳ |
| 9.7 | Module Admin / Personnel | ⏳ |
| 9.10 | Build production + Dockerfile nginx | ⏳ |

## Code style

- **TypeScript strict** activé
- **Préfixe sélecteur** : `tm-` (TerangaMed) pour composants et `tm` pour directives
- **Standalone components** uniquement (pas de NgModule sauf nécessité absolue)
- **Change detection** : `OnPush` partout
- **Imports absolus** : `@core/*`, `@shared/*`, `@features/*`, `@api/*`, `@env/*`

## Internationalisation

V1 : français uniquement. Les libellés sont passés via `i18n="@@key"` pour
préparer la V2 avec un export JSON et un build par locale.

## Tests

- Coverage minimale : 60 % lines / functions
- Pattern : un fichier `*.spec.ts` à côté de chaque `.ts` testable
- `jest --watch` pendant le développement
