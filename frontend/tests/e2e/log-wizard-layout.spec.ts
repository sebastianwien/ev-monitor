import { test, expect } from '@playwright/test'
import { TEST_USER } from './global-setup'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Mobile-Vollbild des Wizards: die Seite darf nicht scrollen, sonst schiebt der Nutzer
 * Kopf und Fortschrittsbalken aus dem Bild. Nur der Inhalt zwischen Kopf und Footer scrollt.
 * Der Viewport ist bewusst kuerzer als Schritt 2, damit der Unterschied zwischen Seiten- und
 * Inhalts-Scroll sichtbar wird. Adressleisten-Kollaps und Tastatur von Android Chrome lassen sich hier nicht simulieren.
 */
test.use({ viewport: { width: 390, height: 520 }, hasTouch: true, isMobile: true })

test('Wizard fuellt den Viewport, Seite scrollt nicht, Balken bleibt sichtbar', async ({ page }) => {
  await page.addInitScript((k: string[]) => localStorage.setItem('seen-announcements', JSON.stringify(k)), featureAnnouncements.map(a => a.key))
  await page.goto('/login')
  await page.fill('input[type="text"]', TEST_USER.email)
  await page.fill('input[type="password"]', TEST_USER.password)
  await page.click('button[type="submit"]')
  await page.waitForURL(/dashboard|logs|cars/)
  await page.goto('/erfassen')
  await page.waitForLoadState('networkidle')
  await page.locator('[data-testid="place-home"]').click()
  await page.locator('#wizard-kwh').waitFor()

  const bar = page.locator('[role="progressbar"]')
  await expect(bar).toBeInViewport()
  await expect(page.locator('[data-testid="wizard-next"]')).toBeInViewport()

  const pageScrollable = await page.evaluate(() => {
    const el = document.scrollingElement!
    return el.scrollHeight > el.clientHeight
  })
  expect(pageScrollable).toBe(false)

  await page.mouse.wheel(0, 800)
  await page.evaluate(() => window.scrollTo(0, 10_000))
  await expect(bar).toBeInViewport()
  expect(await page.evaluate(() => window.scrollY)).toBe(0)
})
