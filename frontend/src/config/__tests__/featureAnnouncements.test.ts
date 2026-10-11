import { describe, it, expect } from 'vitest'
import { featureAnnouncements, type AnnouncementContext } from '../featureAnnouncements'

const baseCtx: AnnouncementContext = {
    hasGoeConnection: false,
    isPremium: false,
    isAutoSyncLive: false,
    hasTeslaConnection: false,
    teslaLocationScopeGranted: false,
    hasVwEudaBrandCar: false,
    hasVwEudaConnection: false,
    ratableUnratedCars: 0,
}

const euda = featureAnnouncements.find(a => a.key === 'euda_autosync_v1')!

describe('euda_autosync_v1 condition', () => {
    it('is hidden for users without a VW-group car', () => {
        expect(euda.condition!({ ...baseCtx, hasVwEudaBrandCar: false })).toBe(false)
    })

    it('is hidden once that user already connected the portal', () => {
        expect(euda.condition!({ ...baseCtx, hasVwEudaBrandCar: true, hasVwEudaConnection: true })).toBe(false)
    })

    it('shows for a VW-group owner who has not connected yet', () => {
        expect(euda.condition!({ ...baseCtx, hasVwEudaBrandCar: true, hasVwEudaConnection: false })).toBe(true)
    })

    it('does not depend on the AutoSync entitlement - the trial is the hook', () => {
        expect(euda.condition!({ ...baseCtx, hasVwEudaBrandCar: true, isAutoSyncLive: false })).toBe(true)
    })
})

describe('euda_autosync_v1 Texte', () => {
    // Direkt aus den YAML-Quellen gelesen: der i18n-Plugin kompiliert Messages zu Funktionen,
    // ueber getLocaleMessage waere der Rohtext nicht mehr pruefbar.
    it('traegt den Marken-Platzhalter in allen vier Sprachen', async () => {
        const { readFileSync } = await import('node:fs')
        for (const locale of ['de', 'en', 'nb', 'sv']) {
            const yaml = readFileSync(new URL(`../../locales/${locale}.yaml`, import.meta.url), 'utf-8')
            const line = yaml.split('\n').find(l => l.includes(`  ${euda.bodyKey.split('.')[1]}: `))
            expect(line, locale).toContain('{brand}')
        }
    })
})

describe('vehicle_rating_v1', () => {
    const rating = featureAnnouncements.find(a => a.key === 'vehicle_rating_v1')!

    it('erscheint ab einem bewertbaren, unbewerteten Auto, auch bei mehreren', () => {
        expect(rating.condition!({ ...baseCtx, ratableUnratedCars: 0 })).toBe(false)
        expect(rating.condition!({ ...baseCtx, ratableUnratedCars: 1 })).toBe(true)
        expect(rating.condition!({ ...baseCtx, ratableUnratedCars: 3 })).toBe(true)
    })

    it('gibt es nur einmal, ohne eigene Variante für mehrere Autos', () => {
        expect(featureAnnouncements.some(a => a.key === 'vehicle_rating_v1_multi')).toBe(false)
    })

    it('verlinkt auf die Bewertung des Zielautos', () => {
        expect(rating.ctaRoute).toBe('/cars/{ratingCarId}/bewerten?src=announcement')
    })
})
