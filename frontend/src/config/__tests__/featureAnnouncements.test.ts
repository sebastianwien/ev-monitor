import { describe, it, expect, vi, beforeEach } from 'vitest'
import teslaFleetService from '../../api/teslaFleetService'
import { featureAnnouncements, type AnnouncementContext } from '../featureAnnouncements'

vi.mock('../../api/teslaFleetService')

const baseCtx: AnnouncementContext = {
    hasGoeConnection: false,
    isPremium: false,
    isAutoSyncLive: false,
    hasTeslaConnection: false,
    teslaLocationScopeGranted: false,
    hasVwEudaBrandCar: false,
    hasVwEudaConnection: false,
}

const reconnect = featureAnnouncements.find(a => a.key === 'tesla_location_reconnect_v1')!

beforeEach(() => vi.clearAllMocks())

describe('tesla_location_reconnect_v1 condition', () => {
    it('is hidden when the user has no Tesla connection', () => {
        expect(reconnect.condition!({ ...baseCtx, hasTeslaConnection: false, teslaLocationScopeGranted: false })).toBe(false)
    })

    it('is hidden once the location scope is confirmed granted', () => {
        expect(reconnect.condition!({ ...baseCtx, hasTeslaConnection: true, teslaLocationScopeGranted: true })).toBe(false)
    })

    it('shows only for a connected Tesla user still missing the scope', () => {
        expect(reconnect.condition!({ ...baseCtx, hasTeslaConnection: true, teslaLocationScopeGranted: false })).toBe(true)
    })
})

describe('tesla_location_reconnect_v1 ctaAction', () => {
    it('starts the reconnect flow for the connected car', async () => {
        vi.mocked(teslaFleetService.getStatus).mockResolvedValue({
            connected: true, vehicleName: 'Model 3', carId: 'car-1', lastSyncAt: null,
            autoImportEnabled: true, geocodingInProgress: false, vehicleState: null,
        })
        vi.mocked(teslaFleetService.startReconnect).mockResolvedValue('redirected')

        await reconnect.ctaAction!()

        expect(teslaFleetService.startReconnect).toHaveBeenCalledWith('car-1')
    })

    it('throws when the status has no carId, so the modal keeps the announcement open', async () => {
        vi.mocked(teslaFleetService.getStatus).mockResolvedValue({
            connected: false, vehicleName: null, carId: null, lastSyncAt: null,
            autoImportEnabled: false, geocodingInProgress: false, vehicleState: null,
        })

        await expect(reconnect.ctaAction!()).rejects.toThrow()

        expect(teslaFleetService.startReconnect).not.toHaveBeenCalled()
    })

    it('throws when startReconnect reports not_configured', async () => {
        vi.mocked(teslaFleetService.getStatus).mockResolvedValue({
            connected: true, vehicleName: 'Model 3', carId: 'car-1', lastSyncAt: null,
            autoImportEnabled: true, geocodingInProgress: false, vehicleState: null,
        })
        vi.mocked(teslaFleetService.startReconnect).mockResolvedValue('not_configured')

        await expect(reconnect.ctaAction!()).rejects.toThrow()
    })
})

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
