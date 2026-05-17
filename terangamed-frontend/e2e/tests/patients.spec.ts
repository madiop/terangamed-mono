import { test, expect, type Page } from '@playwright/test';
import { loginAs } from '../auth/auth.helpers';

/**
 * Tests E2E du module Patients — couverture des parcours métier critiques.
 *
 * <h3>Périmètre</h3>
 * <ol>
 *   <li><b>Liste + recherche</b> (lecture seule, exploite le seed V900)</li>
 *   <li><b>URL stateful</b> : filtres + pagination dans queryParams</li>
 *   <li><b>Création</b> : form CREATE → POST → redirection détail</li>
 *   <li><b>Édition</b> : form EDIT pré-rempli → PUT → détail mis à jour</li>
 *   <li><b>Détail</b> : affichage des infos + actions selon rôle</li>
 *   <li><b>Permissions</b> : ADMIN peut archiver, DOCTOR/RECEPTIONIST non</li>
 * </ol>
 *
 * <h3>Stratégie données</h3>
 * Les tests création/édition créent un patient avec un suffixe timestamp
 * unique (ex: "Test-E2E-1715168400000"). <b>Pas de cleanup automatique</b>
 * pour cette V1 — les patients créés restent en base (acceptable en dev).
 * À implémenter en 10A.5 si la pollution devient un problème.
 */

/** Génère un suffixe unique pour identifier les patients créés par les tests. */
const uniqueSuffix = () => `${Date.now()}-${Math.floor(Math.random() * 1000)}`;

/** Helper : remplit un champ `<mat-select>` Material en cliquant + sélectionnant. */
async function selectMatOption(page: Page, formControlName: string, optionLabel: string | RegExp) {
  await page.locator(`mat-select[formControlName="${formControlName}"]`).click();
  // Le panel mat-select s'ouvre dans un overlay — on cherche dans le document
  const optionLocator =
    typeof optionLabel === 'string'
      ? page.getByRole('option', { name: optionLabel, exact: false })
      : page.getByRole('option', { name: optionLabel });
  await optionLocator.first().click();
}

/**
 * Helper : remplit un champ datepicker Material avec une date au format dd/MM/yyyy.
 * Le datepicker accepte la frappe directe — on évite donc le clic sur le calendrier
 * qui est moins stable.
 */
async function fillDatepicker(page: Page, formControlName: string, ddMMyyyy: string) {
  await page.locator(`input[formControlName="${formControlName}"]`).fill(ddMMyyyy);
}

// ════════════════════════════════════════════════════════════════════════════
// 1. Liste + recherche
// ════════════════════════════════════════════════════════════════════════════

test.describe('Patients — Liste + recherche', () => {
  test('liste rendue avec au moins le seed dev', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    // Attend la table Material
    await expect(page.locator('table')).toBeVisible({ timeout: 10_000 });
    // Au moins une ligne (seed dev V900 contient des patients)
    const rows = page.locator('table tbody tr.mat-mdc-row');
    await expect(rows.first()).toBeVisible();
  });

  test('recherche par nom filtre la liste — empty state si aucun match', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    // Champ recherche dans la barre tm-patient-search-bar (formControlName="lastName")
    await page.locator('input[formControlName="lastName"]').fill('ZZZZNoMatch12345');
    // Debounce 300 ms côté composant + appel API
    await page.waitForTimeout(800);
    // Empty state — texte "Aucun patient" attendu (cf. patient-table empty-cell)
    await expect(page.locator('text=/Aucun patient/i').first()).toBeVisible({ timeout: 5_000 });
  });

  test('URL stateful : filtre lastName persisté en queryParam', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    await page.locator('input[formControlName="lastName"]').fill('Diop');
    await page.waitForTimeout(800);
    expect(page.url()).toContain('lastName=Diop');
  });

  test('F5 sur une URL filtrée préserve le filtre', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients?lastName=Diop');
    // Le composant search-bar doit pré-remplir l'input depuis l'initialCriteria
    await expect(page.locator('input[formControlName="lastName"]')).toHaveValue('Diop');
  });

  test('clic sur une ligne navigue vers /patients/:id', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 10_000 });
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    await expect(page).toHaveURL(/\/patients\/\d+$/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 2. Création
// ════════════════════════════════════════════════════════════════════════════

test.describe('Patients — Création', () => {
  test('bouton Nouveau patient navigue vers /patients/new', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    await page.getByRole('button', { name: /nouveau patient/i }).click();
    await expect(page).toHaveURL(/\/patients\/new/);
  });

  test('soumission avec champs vides → bouton submit désactivé', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients/new');
    // Le bouton submit principal du formulaire patient
    const submitBtn = page.getByRole('button', { name: /créer le patient/i });
    await expect(submitBtn).toBeDisabled();
  });

  test('création patient minimum → succès → redirige vers détail', async ({ page }) => {
    const suffix = uniqueSuffix();
    const lastName = `E2ETest${suffix}`;

    await loginAs(page, 'DOCTOR');
    await page.goto('/patients/new');

    // Champs requis : civility, lastName, firstName, birthDate, gender
    await selectMatOption(page, 'civility', 'Madame');
    await selectMatOption(page, 'gender', 'Femme');
    await page.locator('input[formControlName="lastName"]').fill(lastName);
    await page.locator('input[formControlName="firstName"]').fill('Aïssatou');
    await fillDatepicker(page, 'birthDate', '01/01/1990');

    // Submit
    await page.getByRole('button', { name: /créer le patient/i }).click();

    // Redirection vers la page détail du patient créé
    await expect(page).toHaveURL(/\/patients\/\d+$/, { timeout: 15_000 });
    // Le nom doit apparaître dans le header — le template applique `| uppercase`
    // sur lastName, donc on utilise un regex case-insensitive.
    await expect(page.locator('.patient-name')).toContainText(new RegExp(lastName, 'i'), {
      timeout: 5_000
    });
  });

  test('création + email + téléphone optionnels → succès', async ({ page }) => {
    const suffix = uniqueSuffix();
    const lastName = `E2EFull${suffix}`;
    const email = `e2e.test.${suffix}@terangamed.test`;

    await loginAs(page, 'ADMIN');
    await page.goto('/patients/new');

    await selectMatOption(page, 'civility', 'Monsieur');
    await selectMatOption(page, 'gender', 'Homme');
    await page.locator('input[formControlName="lastName"]').fill(lastName);
    await page.locator('input[formControlName="firstName"]').fill('Mamadou');
    await fillDatepicker(page, 'birthDate', '15/06/1985');
    await page.locator('input[formControlName="email"]').fill(email);
    await page.locator('input[formControlName="phone"]').fill('+221 77 123 45 67');

    await page.getByRole('button', { name: /créer le patient/i }).click();
    await expect(page).toHaveURL(/\/patients\/\d+$/, { timeout: 15_000 });
    // `| uppercase` sur lastName → regex case-insensitive
    await expect(page.locator('.patient-name')).toContainText(new RegExp(lastName, 'i'));
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 3. Édition
// ════════════════════════════════════════════════════════════════════════════

test.describe('Patients — Édition', () => {
  test('form EDIT pré-rempli + modification du téléphone → succès', async ({ page }) => {
    // 1. Crée un patient à éditer (test indépendant)
    const suffix = uniqueSuffix();
    const lastName = `E2EEdit${suffix}`;

    await loginAs(page, 'ADMIN');
    await page.goto('/patients/new');
    await selectMatOption(page, 'civility', 'Madame');
    await selectMatOption(page, 'gender', 'Femme');
    await page.locator('input[formControlName="lastName"]').fill(lastName);
    await page.locator('input[formControlName="firstName"]').fill('À-Modifier');
    await fillDatepicker(page, 'birthDate', '01/01/1995');
    await page.locator('input[formControlName="phone"]').fill('+221 77 000 00 00');
    // Email valide à la création — précaution conservée même après le fix
    // backend de la dette #57 (V2__partial_unique_email.sql + normalizeBlankFields).
    // Cela garantit que le test reste valide si une régression réintroduit
    // une contrainte unique stricte sur email.
    await page.locator('input[formControlName="email"]').fill(`edit.${suffix}@terangamed.test`);
    await page.getByRole('button', { name: /créer le patient/i }).click();
    await expect(page).toHaveURL(/\/patients\/\d+$/, { timeout: 15_000 });

    // 2. Va sur l'édition via le bouton Modifier
    await page.getByRole('button', { name: /modifier/i }).first().click();
    await expect(page).toHaveURL(/\/patients\/\d+\/edit$/);

    // 3. Vérifie que le form est pré-rempli
    await expect(page.locator('input[formControlName="lastName"]')).toHaveValue(lastName);
    await expect(page.locator('input[formControlName="phone"]')).toHaveValue('+221 77 000 00 00');

    // 4. Modifie le téléphone et soumet via le bouton submit du form
    // (sélecteur stable, indépendant du label "Enregistrer").
    await page.locator('input[formControlName="phone"]').fill('+221 77 999 99 99');
    const submitBtn = page.locator('form button[type="submit"]');
    await expect(submitBtn).toBeEnabled({ timeout: 5_000 });
    await submitBtn.click();

    // 5. Retour vers le détail avec valeur mise à jour
    await expect(page).toHaveURL(/\/patients\/\d+$/, { timeout: 10_000 });
    await expect(page.locator('body')).toContainText('+221 77 999 99 99');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 4. Détail
// ════════════════════════════════════════════════════════════════════════════

test.describe('Patients — Détail', () => {
  test('affiche nom, MRN et avatar', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    await expect(page).toHaveURL(/\/patients\/\d+$/);

    // Sélecteurs basés sur les classes du composant patient-detail-page
    await expect(page.locator('.patient-name')).toBeVisible();
    await expect(page.locator('.patient-mrn')).toContainText(/N° dossier/i);
    await expect(page.locator('.patient-avatar')).toBeVisible();
  });

  test('bouton Retour à la liste navigue vers /patients', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    await expect(page).toHaveURL(/\/patients\/\d+$/);

    await page.getByRole('button', { name: /retour à la liste/i }).click();
    await expect(page).toHaveURL(/\/patients(\?|$)/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 5. Permissions — Archive (ADMIN uniquement)
// ════════════════════════════════════════════════════════════════════════════

test.describe('Patients — Permissions archive', () => {
  test('ADMIN voit le bouton Archiver via menu Plus d\'actions', async ({ page }) => {
    await loginAs(page, 'ADMIN');
    await page.goto('/patients');
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    await expect(page).toHaveURL(/\/patients\/\d+$/);

    // Ouvre le menu "Plus d'actions"
    await page.getByRole('button', { name: /plus d'actions/i }).click();
    await expect(page.getByRole('menuitem', { name: /archiver/i })).toBeVisible();
  });

  test('DOCTOR ne voit pas le menu Plus d\'actions (ou ne voit pas Archiver)', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/patients');
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    await expect(page).toHaveURL(/\/patients\/\d+$/);

    // Soit le menu n'existe pas, soit il existe mais sans l'item Archiver.
    // On accepte les deux variantes selon l'implémentation finale.
    const moreBtn = page.getByRole('button', { name: /plus d'actions/i });
    const moreCount = await moreBtn.count();
    if (moreCount > 0) {
      await moreBtn.click();
      await expect(page.getByRole('menuitem', { name: /archiver/i })).toHaveCount(0);
    }
  });
});
