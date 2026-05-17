import { test, expect } from '@playwright/test';
import { loginAs } from '../auth/auth.helpers';
import { SEED_USERS } from '../fixtures/users';

/**
 * Tests smoke — validation du setup Playwright et de l'auth Keycloak.
 *
 * <p>Doivent passer avant d'écrire les tests métier. Si un test smoke échoue,
 * c'est un problème d'infrastructure (services pas démarrés, seed Keycloak
 * cassé, frontend mal configuré) — pas de la logique métier.
 */
test.describe('@smoke setup E2E', () => {
  test('la page de login est servie sans authentification', async ({ page }) => {
    await page.goto('/login');
    // Sur la page login, on attend au moins le titre/contenu attendu.
    await expect(page).toHaveURL(/\/login/);
  });

  test('redirection /dashboard → /login si non authentifié', async ({ page }) => {
    await page.goto('/dashboard');
    // authGuard doit nous renvoyer vers login
    await expect(page).toHaveURL(/\/login/);
  });

  test('login programmatique ADMIN → accès /dashboard', async ({ page }) => {
    await loginAs(page, 'ADMIN');
    await page.goto('/dashboard');
    // On ne doit PAS être redirigé vers /login
    await expect(page).not.toHaveURL(/\/login/);
    // Marque visible — un dashboard bien chargé contient au moins une zone
    // avec le rôle ou le nom user. À adapter selon la sélection finale du
    // composant dashboard.
    await expect(page.locator('body')).not.toBeEmpty();
  });

  test('login programmatique DOCTOR → accès /dashboard', async ({ page }) => {
    await loginAs(page, 'DOCTOR');
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('login programmatique RECEPTIONIST → accès /dashboard', async ({ page }) => {
    await loginAs(page, 'RECEPTIONIST');
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('RECEPTIONIST ne peut pas accéder à /admin/staff (roleGuard ADMIN)', async ({ page }) => {
    await loginAs(page, 'RECEPTIONIST');
    await page.goto('/admin/staff');
    // Le roleGuard doit renvoyer vers /unauthorized
    await expect(page).toHaveURL(/\/unauthorized/);
  });

  test('ADMIN voit le seed user dr.martin', async ({ page }) => {
    // Vérifie que la fixture utilise bien le seed dev — sanity check.
    expect(SEED_USERS.DOCTOR.username).toBe('dr.martin');
    await loginAs(page, 'ADMIN');
    await page.goto('/admin/staff');
    // Ne pas asserter sur le contenu spécifique du tableau ici — c'est le rôle
    // des tests métier 10A.4. On se contente de vérifier que la page est servie.
    await expect(page).toHaveURL(/\/admin\/staff/);
  });
});
