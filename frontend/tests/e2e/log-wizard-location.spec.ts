import { test, expect } from '@playwright/test'
import { TEST_USER } from './global-setup'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Standort blockiert: der Wizard zeigt die Anleitung fuer die Plattform des Browsers
 * (hier Desktop-Chromium) und bietet einen neuen Versuch an. Den Sprung in die
 * App-Einstellungen gibt es nur in der nativen App.
 */
test('blockierter Standort zeigt Desktop-Anleitung und Wiederholen', async ({ page }) => {
  await page.addInitScript((k: string[]) => {
    localStorage.setItem('seen-announcements', JSON.stringify(k))
    localStorage.setItem('ev_location_enabled', 'true')
    navigator.permissions.query = () => Promise.resolve({ state: 'denied' } as PermissionStatus)
  }, featureAnnouncements.map(a => a.key))
  await page.goto('/login')
  await page.fill('input[type="text"]', TEST_USER.email)
  await page.fill('input[type="password"]', TEST_USER.password)
  await page.click('button[type="submit"]')
  await page.waitForURL(/dashboard|logs|cars/)
  await page.goto('/erfassen')

  const blocked = page.locator('[data-testid="wizard-location-blocked"]')
  await expect(blocked).toBeVisible()
  await expect(blocked).toContainText('links neben der Adresse')
  await expect(blocked.getByRole('button', { name: 'Erneut versuchen' })).toBeVisible()
  await expect(blocked.getByRole('button', { name: 'Einstellungen öffnen' })).toHaveCount(0)
  await expect(page.locator('[data-testid="wizard-location"]')).toHaveCount(0)
})
