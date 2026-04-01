import { test, expect } from '@playwright/test';

test.describe('Landing Page', () => {
  test('lädt korrekt und zeigt Hero-Content', async ({ page }) => {
    await page.goto('/');

    // H1 muss sichtbar sein
    const h1 = page.locator('h1').first();
    await expect(h1).toBeVisible();

    // CTA-Buttons vorhanden
    await expect(page.getByRole('link', { name: /modelle|models/i }).first()).toBeVisible();
  });

  test('Live Model Preview lädt (API-Daten erscheinen)', async ({ page }) => {
    await page.goto('/');

    // Warte auf mindestens eine Model-Card im DOM (können im Scroll-Container außerhalb Viewport sein)
    const modelCards = page.locator('a[href*="/modelle/"]');
    await expect(modelCards.first()).toBeAttached({ timeout: 10_000 });
    expect(await modelCards.count()).toBeGreaterThan(0);
  });

  test('Navigation zu /modelle funktioniert', async ({ page }) => {
    await page.goto('/');

    // Klick auf den exakten Models-CTA-Link (nicht die Model-Cards in der Preview)
    await page.locator('a[href="/modelle"]').first().click();

    await expect(page).toHaveURL(/\/modelle/);
    await expect(page.locator('h1').first()).toBeVisible();
  });

  test('Login-Link ist vorhanden', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('a[href="/login"]').first()).toBeVisible();
  });
});
