# Scripts utilitaires — TerangaMed

## `coverage-all.sh` — Couverture consolidée

Lance les tests **backend** (Maven + Jacoco) et **frontend** (Jest avec coverage), agrège les résultats, et imprime un récapitulatif des chemins vers les rapports HTML détaillés.

### Pré-requis

- JDK 21 + Maven Wrapper (`./mvnw`)
- Node 18+ + npm

### Usage

```bash
./scripts/coverage-all.sh                # Run complet (back + front)
./scripts/coverage-all.sh --fast         # Skip Jacoco check (seuils 80%) — itérations rapides
./scripts/coverage-all.sh --no-front     # Backend uniquement
./scripts/coverage-all.sh --no-back      # Frontend uniquement
./scripts/coverage-all.sh -h             # Aide
```

### Sortie

1. **Logs colorés** par module (succès/échec)
2. **Récap des % frontend** depuis `coverage/coverage-summary.json` (Jest reporter `json-summary`)
3. **Liste des chemins** vers les rapports HTML à ouvrir manuellement

Exemple de sortie :

```
════════════════════════════════════════════════════════════
  TerangaMed — Coverage consolidé
════════════════════════════════════════════════════════════
ℹ  Mode: back=true, front=true, fast=false
…
✓  Backend tests verts
ℹ  Rapports HTML Jacoco générés :
    file:///.../patient-service/target/site/jacoco/index.html
    file:///.../doctor-service/target/site/jacoco/index.html
    …
✓  Frontend tests verts
ℹ  Couverture globale (depuis coverage/coverage-summary.json) :
    Lines:      72.4%   (1183/1632)
    Statements: 71.9%
    Functions:  68.2%
    Branches:   58.7%
✓  Tous les modules : OK
```

### Ouvrir tous les rapports HTML (macOS)

```bash
open terangamed-backend/services/*/target/site/jacoco/index.html
open terangamed-frontend/coverage/lcov-report/index.html
```

### Seuils de couverture

| Module | Seuil | Outil |
|--------|-------|-------|
| Backend (chaque service) | 80% (lignes) | Jacoco — `jacoco.coverage.minimum=0.80` dans parent POM |
| Frontend | 60% lignes/statements/fonctions, 50% branches | Jest — `coverageThreshold` dans `jest.config.js` |

Mode `--fast` désactive le check Jacoco pour les itérations rapides (les tests tournent quand même, juste pas le `verify` qui fail si seuil non atteint).

### Pourquoi pas un rapport HTML unifié ?

Pour V1 on s'appuie sur les rapports natifs de Jacoco et Jest qui sont déjà très lisibles (drill-down par classe/composant, mise en surbrillance des lignes non couvertes). Un rapport unifié nécessiterait un agrégateur tiers (sonarqube, codecov) qu'on ajoutera en 10B (CI/CD) si besoin.

### E2E coverage ?

Les tests Playwright E2E **ne sont pas inclus** dans le coverage. Raisons :

1. Les E2E testent l'**intégration** (browser ↔ API ↔ DB), pas la couverture de code source.
2. Instrumenter le frontend Angular pour collecter du coverage E2E nécessite un setup Istanbul + reload de l'app — fragile et lent.
3. La pyramide de tests TerangaMed place :
   - **Tests unit** (Jest 80%+ et Spring 80%+) → couverture des fonctions et branches
   - **Tests E2E** (Playwright) → couverture des **parcours métier** (au lieu de % de lignes)

Si on veut un % global E2E, ajouter `@playwright/test --coverage` est possible mais reporté à une éventuelle évolution post-10A.
