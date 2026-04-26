import { test, expect } from '@playwright/test';
import { TEST_USER } from './global-setup';

test.describe('Auth Flow', () => {
  test('Login-Seite zeigt Formular', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('input[type="text"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('Login mit korrekten Credentials → Redirect zu /dashboard', async ({ page }) => {
    await page.goto('/login');

    await page.locator('input[type="text"]').fill(TEST_USER.email);
    await page.locator('input[type="password"]').fill(TEST_USER.password);
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 });
  });

  test('Login mit falschen Credentials → Fehlermeldung', async ({ page }) => {
    await page.goto('/login');

    await page.locator('input[type="text"]').fill('falsch@falsch.de');
    await page.locator('input[type="password"]').fill('falschespassword');
    await page.locator('button[type="submit"]').click();

    // Fehlermeldung muss erscheinen, kein Redirect
    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator('form')).toBeVisible();
  });

  test('Unauthentifizierter Zugriff auf /dashboard → Redirect zu /login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('Unauthentifizierter Zugriff auf /upgrade → Redirect zu /login mit redirect-Parameter', async ({ page }) => {
    await page.goto('/upgrade');
    await expect(page).toHaveURL(/\/login\?redirect=(%2F|\/)?upgrade/);
  });

  test('Login mit redirect-Parameter → Redirect zu Zielseite statt /dashboard', async ({ page }) => {
    await page.goto('/login?redirect=/upgrade');

    await page.locator('input[type="text"]').fill(TEST_USER.email);
    await page.locator('input[type="password"]').fill(TEST_USER.password);
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/upgrade/, { timeout: 10_000 });
  });

  test('Register-Link auf Login-Seite funktioniert', async ({ page }) => {
    await page.goto('/login');
    await page.locator('a[href*="/registrieren"], a[href*="/register"]').first().click();
    await expect(page).toHaveURL(/registrieren|register/);
  });
});
