import { test, expect } from '@playwright/test';

test.describe('Öffentliche Modellseiten (SEO-kritisch)', () => {
  test('/modelle lädt und zeigt Modell-Cards', async ({ page }) => {
    await page.goto('/modelle');

    await expect(page.locator('h1').first()).toBeVisible();

    // Mindestens eine Modell-Card mit Link
    const modelLinks = page.locator('a[href*="/modelle/"]');
    await expect(modelLinks.first()).toBeVisible({ timeout: 10_000 });
  });

  test('/modelle zeigt Marken-Filter', async ({ page }) => {
    await page.goto('/modelle');

    // Marken-Abschnitt muss sichtbar sein
    const teslaLink = page.locator('a[href*="/modelle/Tesla"]').first();
    await expect(teslaLink).toBeVisible({ timeout: 10_000 });
  });

  test('/modelle/Tesla/Model_3 lädt korrekt', async ({ page }) => {
    await page.goto('/modelle/Tesla/Model_3');

    await expect(page.locator('h1').first()).toBeVisible({ timeout: 10_000 });
    // Seite darf keine 404-Meldung zeigen
    await expect(page.locator('text=404')).not.toBeVisible();
  });

  test('Breadcrumb-Navigation von Modell-Detail zurück zu /modelle', async ({ page }) => {
    await page.goto('/modelle/Tesla/Model_3');

    // Breadcrumb-Link zu /modelle
    const breadcrumb = page.locator('a[href="/modelle"]');
    await expect(breadcrumb.first()).toBeVisible({ timeout: 10_000 });
  });

  test('Brand-Seite /modelle/Tesla lädt', async ({ page }) => {
    await page.goto('/modelle/Tesla');

    await expect(page.locator('h1').first()).toBeVisible({ timeout: 10_000 });
    // Links zu Modell-Detail-Seiten vorhanden
    const modelLinks = page.locator('a[href*="/modelle/Tesla/"]');
    await expect(modelLinks.first()).toBeVisible({ timeout: 10_000 });
  });
});
