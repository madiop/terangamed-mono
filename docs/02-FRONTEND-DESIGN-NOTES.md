# TerangaMed — Notes de design frontend (référence Étape 6)

> Source : `design 21 avr. 2026, 01_06_27.png`
> À utiliser lors de l'implémentation Angular Material 17.

## Layout général

- **Sidebar fixe à gauche** (~240px, fond navy `#1B2A4E` / `#1F2C4F`)
  - Logo en haut : « CLINIQUE MÉDICAL »
  - Liens de navigation avec icône + label (état actif sur fond légèrement plus clair)
  - Bouton « Déconnexion » en bas
- **Zone principale** sur fond gris très clair (`#F5F7FA`)
  - Header : « Bonjour {prénom utilisateur} », sous-titre contextuel, barre recherche, cloche notifications, avatar
  - Contenu en grille (cards arrondies, ombre légère, fond blanc)

## Navigation sidebar

```
Tableau de bord    (active)
Patients
Rendez-vous
Consultations
Facturation
Documents
─────────────────
Déconnexion
```

→ Mappe naturellement aux modules Angular : `dashboard`, `patients`, `appointments`, `consultations`, `billing`, `documents`.

## Composants identifiés sur le dashboard

1. **KPI cards** — 4 cartes alignées : libellé, valeur numérique grande, lien « Voir »
   - Rendez-vous du jour
   - Patients en attente
   - Consultations à finaliser
   - Factures en attente
2. **Planning des Rendez-vous** — calendrier hebdomadaire, axe horaire vertical (9:00–15:00), événements colorés par statut/médecin
3. **Tâches à faire** — checklist actionnable
4. **Dossier Patient** (panneau à droite) — photo, nom, âge, n° dossier, infos santé (allergies, traitement), onglets `Informations | Historique | Prescriptions | Documents`, boutons « Voir détails » et « Nouvelle Consultation »
5. **Messages** — liste avec avatar + extrait + horodatage

## Palette et tokens (proposition)

| Token            | Valeur     | Usage                         |
|------------------|-----------|-------------------------------|
| `--tm-primary`   | `#1F2C4F` | Sidebar, boutons primaires    |
| `--tm-accent`    | `#3B82F6` | Liens, focus, charts          |
| `--tm-success`   | `#22C55E` | Statut OK, RDV confirmé       |
| `--tm-warning`   | `#F59E0B` | RDV en attente                |
| `--tm-danger`    | `#EF4444` | Factures en retard, erreurs   |
| `--tm-bg`        | `#F5F7FA` | Fond principal                |
| `--tm-card`      | `#FFFFFF` | Cards                         |
| `--tm-text`      | `#0F172A` | Texte principal               |
| `--tm-text-soft` | `#64748B` | Texte secondaire              |
| `--tm-border`    | `#E2E8F0` | Bordures, séparateurs         |

## Stack technique frontend (rappel)

- Angular 17 (standalone components)
- Angular Material 17 (theme custom basé sur la palette ci-dessus)
- `keycloak-angular` pour l'authentification
- `@angular/cdk/layout` pour la responsivité
- Routing avec lazy loading par feature
