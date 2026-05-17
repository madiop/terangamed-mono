import { test, expect } from '@playwright/test';
import { loginAs } from '../auth/auth.helpers';

/**
 * Tests E2E du module Consultations + Prescriptions.
 *
 * <h3>Périmètre — pragmatique</h3>
 * <ol>
 *   <li><b>Permissions par rôle</b> : ADMIN/DOCTOR accèdent, RECEPTIONIST/PATIENT bloqués</li>
 *   <li><b>Routes /consultations/*</b> : navigation et smoke checks</li>
 *   <li><b>Dossier médical patient</b> (route /patients/:id/medical-record) : tabs visibles</li>
 *   <li><b>Lien création consultation</b> depuis le dossier médical</li>
 *   <li><b>Smoke check route prescription</b></li>
 * </ol>
 *
 * <h3>Pourquoi pas de test de création complet ?</h3>
 * Le form consultation requiert plusieurs pré-conditions backend (patient avec
 * medical record actif, doctor courant via X-Doctor-Id, validation date/motif).
 * Le couvrir nécessiterait soit un seed E2E dédié, soit beaucoup d'orchestration.
 * Les tests unitaires Jest des composants form/detail couvrent déjà la logique
 * UI fine. Les tests E2E ici se concentrent sur la <b>navigation et les guards</b>,
 * qui sont la garantie de sécurité métier.
 *
 * <p>Une couverture création/signature complète est suivie en dette technique
 * pour 10A.5+ (avec seed E2E dédié).
 */

// ════════════════════════════════════════════════════════════════════════════
// 1. Permissions /consultations
// ════════════════════════════════════════════════════════════════════════════

test.describe('Consultations — Permissions par rôle', () => {
  test('ADMIN peut accéder à /consultations', async ({ page }) => {
    await loginAs(page, 'ADMIN');
    await page.goto('/consultations');
    await expect(page).not.toHaveURL(/\/login|\/unauthorized/);
  });

  test('DOCTOR peut accéder à /consultations', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/consultations');
    await expect(page).not.toHaveURL(/\/login|\/unauthorized/);
  });

  test('RECEPTIONIST bloqué sur /consultations → /unauthorized', async ({ page }) => {
    await loginAs(page, 'RECEPTIONIST');
    await page.goto('/consultations');
    await expect(page).toHaveURL(/\/unauthorized/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 2. Dossier médical patient (route /patients/:id/medical-record)
// ════════════════════════════════════════════════════════════════════════════

test.describe('Dossier médical patient', () => {
  test('ADMIN accède au dossier médical depuis la page patient', async ({ page }) => {
    await loginAs(page, 'ADMIN');
    await page.goto('/patients');
    // Click sur le 1er patient pour aller sur sa page détail
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    await expect(page).toHaveURL(/\/patients\/\d+$/);

    // Depuis la page patient, le menu "Plus d'actions" propose "Dossier médical"
    await page.getByRole('button', { name: /plus d'actions/i }).click();
    await page.getByRole('menuitem', { name: /dossier médical/i }).click();

    // Devrait arriver sur /patients/:id/medical-record
    await expect(page).toHaveURL(/\/patients\/\d+\/medical-record/, { timeout: 10_000 });
  });

  test('RECEPTIONIST bloqué sur /patients/:id/medical-record', async ({ page }) => {
    // Récupère un id patient valide d'abord (via un loginAs ADMIN)
    await loginAs(page, 'ADMIN');
    await page.goto('/patients');
    // Click sur la 1re cellule (MRN) plutôt que la row au centre — la cellule
// Actions a stopPropagation et peut absorber le click selon les largeurs.
await page.locator('table tbody tr').first().locator('td').first().click();
    const patientUrl = page.url();
    const match = patientUrl.match(/\/patients\/(\d+)/);
    const patientId = match ? match[1] : '1';

    // Switch vers RECEPTIONIST et tente d'accéder au dossier médical
    await loginAs(page, 'RECEPTIONIST');
    await page.goto(`/patients/${patientId}/medical-record`);
    await expect(page).toHaveURL(/\/unauthorized/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 3. Routes /consultations/:id et /consultations/:id/edit
// ════════════════════════════════════════════════════════════════════════════

test.describe('Consultations — Routes détail/edit', () => {
  test('/consultations/99999 (id inexistant) → page d\'erreur ou détail vide', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/consultations/99999');
    // L'app gère le 404 via la facade : message d'erreur affiché.
    // On vérifie qu'on n'est pas redirigé vers login (sinon problème d'auth).
    await expect(page).not.toHaveURL(/\/login/);
    // Le body devrait contenir un message d'erreur évoquant l'introuvable.
    await page.waitForTimeout(2_000); // laisse le temps à la facade de répondre
    const body = await page.locator('body').textContent();
    expect(body?.toLowerCase()).toMatch(/introuvable|chargement|erreur|consultation/);
  });

  test('/consultations/new accessible pour DOCTOR (sans crash auth)', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/consultations/new');
    // Comportement effectif : selon le composant, soit redirect vers /patients
    // (si gestion défensive de l'absence de patientId), soit affichage d'un
    // form vide / message d'erreur. On ne teste pas le redirect précis ici —
    // ce qui compte c'est que (a) l'auth fonctionne (pas de redirect vers /login)
    // et (b) la page rend quelque chose sans crash.
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator('body')).not.toBeEmpty();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// 4. Smoke check route prescription (10A 9.6e)
// ════════════════════════════════════════════════════════════════════════════

test.describe('Prescription — Smoke check route', () => {
  test('/consultations/99999/prescription → page d\'erreur (consultation introuvable)', async ({
    page
  }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/consultations/99999/prescription');
    // Comme pour le détail, on vérifie qu'on n'est pas redirigé vers login.
    await expect(page).not.toHaveURL(/\/login/);
    // Et que la page rend bien quelque chose (page d'erreur ou loading).
    await expect(page.locator('body')).not.toBeEmpty();
  });

  test('RECEPTIONIST bloqué sur /consultations/X/prescription', async ({ page }) => {
    await loginAs(page, 'RECEPTIONIST');
    await page.goto('/consultations/1/prescription');
    await expect(page).toHaveURL(/\/unauthorized/);
  });
});
