import { test, expect, type Browser } from '@playwright/test'

/**
 * Creates a browser context with a specific navigator.languages mock.
 * Also clears ev-country from localStorage so country detection runs fresh.
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

    return context
}

test.describe('Country is submitted on register', () => {
    /**
     * Regression: the unambiguous case used to send country: undefined, so exactly
     * the users whose country WAS reliably detected ended up with country = null.
     */
    test('sends detected country even when the picker is hidden', async ({ browser }) => {
        const context = await contextWithLocale(browser, ['de-DE'])
        const page = await context.newPage()

        let submittedCountry: string | undefined
        await context.route('**/api/auth/register', async route => {
            submittedCountry = route.request().postDataJSON()?.country
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({ status: 'PENDING_VERIFICATION', email: 'country-probe@example.com' }),
            })
        })

        await page.goto('/register')
        await expect(page.locator('[data-testid="country-picker"]')).not.toBeVisible()
        await page.locator('input[type="email"]').fill('country-probe@example.com')
        await page.locator('input[type="password"]').fill('Sicher123!')
        await page.locator('button[type="submit"]').click()

        await expect.poll(() => submittedCountry).toBe('DE')
        await context.close()
    })
})

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
