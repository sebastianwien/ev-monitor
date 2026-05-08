import { describe, it, expect } from 'vitest'
import { shouldAutoEnableTelemetry } from '../teslaTelemetryAutoEnable'
import type { TeslaPairingStatus } from '@/api/teslaFleetService'

function buildStatus(overrides: Partial<TeslaPairingStatus>): TeslaPairingStatus {
    return {
        keyPaired: false,
        telemetryConfigPushed: false,
        dataSource: 'POLLING',
        telemetryProfile: 'CHARGING_ONLY',
        vin: null,
        ...overrides,
    } as TeslaPairingStatus
}

describe('shouldAutoEnableTelemetry', () => {
    it('returns true when key is paired but telemetry config not yet pushed', () => {
        const status = buildStatus({ keyPaired: true, telemetryConfigPushed: false })
        expect(shouldAutoEnableTelemetry(status, null)).toBe(true)
    })

    it('returns false when key is not yet paired', () => {
        const status = buildStatus({ keyPaired: false, telemetryConfigPushed: false })
        expect(shouldAutoEnableTelemetry(status, null)).toBe(false)
    })

    it('returns false when telemetry config is already pushed (already enabled)', () => {
        const status = buildStatus({ keyPaired: true, telemetryConfigPushed: true })
        expect(shouldAutoEnableTelemetry(status, null)).toBe(false)
    })

    it('returns false when there is a pending error (avoid retry-storm on Tesla 4xx/5xx)', () => {
        const status = buildStatus({ keyPaired: true, telemetryConfigPushed: false })
        expect(shouldAutoEnableTelemetry(status, 'enable failed')).toBe(false)
    })

    it('returns false when status is null (load still in progress or 404)', () => {
        expect(shouldAutoEnableTelemetry(null, null)).toBe(false)
    })

    it('returns false when telemetry is already active (dataSource=TELEMETRY)', () => {
        // Defense in depth: if backend already reports TELEMETRY active we never want
        // to re-fire enable, even if config flag drift'd.
        const status = buildStatus({ keyPaired: true, telemetryConfigPushed: true, dataSource: 'TELEMETRY' })
        expect(shouldAutoEnableTelemetry(status, null)).toBe(false)
    })
})
