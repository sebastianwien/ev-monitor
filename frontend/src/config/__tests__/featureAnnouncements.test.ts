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
