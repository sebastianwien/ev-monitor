import { test, expect, type Browser } from '@playwright/test'

/**
 * Creates a browser context with a specific navigator.languages mock.
 * Also clears ev-country from localStorage so country detection runs fresh,
 * and mocks the GeoIP endpoint (not implemented yet) to return 404 immediately.
 */
async function contextWithLocale(browser: Browser, languages: string[]) {
    const context = await browser.newContext({ locale: languages[0] })

    // Override navigator.languages before any page scripts run
    await context.addInitScript((langs) => {
        Object.defineProperty(navigator, 'languages', {
            get: () => langs,
            configurable: true,
        })
        localStorage.removeItem('ev-country')
    }, languages)

    // GeoIP not implemented yet — short-circuit to avoid waiting for timeout
    await context.route('**/api/geoip/country', route => route.fulfill({ status: 404 }))

    return context
}

test.describe('Country picker on register', () => {
    test('shown for en-US (ambiguous)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['en-US'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).toBeVisible()
        await context.close()
    })

    test('shown for plain en (ambiguous)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['en'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).toBeVisible()
        await context.close()
    })

    test('shown for unknown language (no match in map)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['ja-JP'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).toBeVisible()
        await context.close()
    })

    test('hidden for de-DE (unambiguous)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['de-DE'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).not.toBeVisible()
        await context.close()
    })

    test('hidden for en-GB (unambiguous)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['en-GB'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).not.toBeVisible()
        await context.close()
    })

    test('hidden for nb-NO (unambiguous)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['nb-NO'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).not.toBeVisible()
        await context.close()
    })

    test('hidden for sv-SE (unambiguous)', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['sv-SE'])
        const page = await context.newPage()
        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).not.toBeVisible()
        await context.close()
    })
})
