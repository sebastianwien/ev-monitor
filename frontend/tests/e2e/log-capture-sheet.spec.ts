import { test, expect, type Page } from '@playwright/test'
import { TEST_USER } from './global-setup'
import { featureAnnouncements } from '../../src/config/featureAnnouncements'

/**
 * Erfassen als Sheet auf dem Handy: öffnet über der Seite (kein Seitenwechsel), ohne Eingaben
 * schließt ein Zug nach unten, mit Eingaben minimiert er zur Leiste über der Bottom-Nav.
 */
test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true,
  geolocation: { latitude: 52.52, longitude: 13.4 }, permissions: ['geolocation'] })

async function login(page: Page) {
  await page.addInitScript((k: string[]) => localStorage.setItem('seen-announcements', JSON.stringify(k)), featureAnnouncements.map(a => a.key))
  await page.route('**/api/charging-provider-tariffs/cpos/nearby-stations**', r => r.fulfill({ json: [] }))
  await page.goto('/login')
  await page.fill('input[type="text"]', TEST_USER.email)
  await page.fill('input[type="password"]', TEST_USER.password)
  await page.click('button[type="submit"]')
  await page.waitForURL(/dashboard|logs|cars|story/)
}

const sheet = (page: Page) => page.getByTestId('log-form-sheet')
const bar = (page: Page) => page.getByTestId('log-form-bar')
const openCapture = (page: Page) => page.locator('nav.bottom-nav').getByRole('button', { name: 'Erfassen' }).click()
/** Zug nach unten über den Kopf des Sheets (Touch-Events wie im BottomSheet-Unit-Test) */
const dragDown = (page: Page) => sheet(page).locator('header').evaluate((el) => {
  const ys = [100, 140, 200, 260]
  const fire = (type: string, i: number) => {
    const e = new Event(type, { bubbles: true, cancelable: true })
    Object.defineProperty(e, 'touches', { value: type === 'touchend' ? [] : [{ clientX: 100, clientY: ys[i] }] })
    Object.defineProperty(e, 'timeStamp', { value: i * 16 })
    el.dispatchEvent(e)
  }
  fire('touchstart', 0)
  ys.slice(1).forEach((_, i) => fire('touchmove', i + 1))
  fire('touchend', ys.length - 1)
})

test('Erfassen öffnet als Sheet, mit Eingaben minimiert es zur Leiste', async ({ page }) => {
  await login(page)
  const url = page.url()

  await openCapture(page)
  await expect(sheet(page)).toBeVisible()
  expect(page.url()).toBe(url)

  // Ohne Eingaben: der Zug schließt
  await dragDown(page)
  await expect(sheet(page)).toHaveCount(0)

  // Mit Ort gewählt: der Zug minimiert, die Eingabe bleibt
  await openCapture(page)
  await page.getByTestId('place-here').click()
  await page.locator('#wizard-kwh').waitFor()
  await dragDown(page)
  await expect(bar(page)).toBeVisible()
  await expect(bar(page)).toBeInViewport()
  await expect(page.locator('nav.bottom-nav')).toBeVisible()

  // Tipp auf die Leiste macht beim selben Schritt weiter
  await bar(page).getByRole('button', { name: 'Ladevorgang erfassen' }).click()
  await expect(bar(page)).toHaveCount(0)
  await expect(page.locator('#wizard-kwh')).toBeVisible()

  // Erneut minimiert, der Erfassen-Knopf klappt denselben Entwurf auf
  await dragDown(page)
  await expect(bar(page)).toBeVisible()
  await openCapture(page)
  await expect(page.locator('#wizard-kwh')).toBeVisible()

  // Das X in der Leiste verwirft
  await dragDown(page)
  await bar(page).getByRole('button', { name: 'Abbrechen' }).click()
  await expect(sheet(page)).toHaveCount(0)
})
