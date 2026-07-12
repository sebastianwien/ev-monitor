import { describe, it, expect, vi, beforeEach } from 'vitest'
import teslaFleetService, { type TeslaPairingStatus } from '../../api/teslaFleetService'
import { useTeslaPairing } from '../useTeslaPairing'

vi.mock('../../api/teslaFleetService')
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

const status = (o: Partial<TeslaPairingStatus> = {}): TeslaPairingStatus => ({
    vin: 'VIN1',
    keyPaired: false,
    telemetryConfigPushed: false,
    ...o,
})

const httpError = (httpStatus: number, message?: string) => ({
    response: { status: httpStatus, data: message ? { message } : {} },
})

beforeEach(() => vi.clearAllMocks())

describe('useTeslaPairing - Status laden', () => {
    it('fragt den Pairing-Status gar nicht ab, solange kein Tesla-Account verbunden ist', async () => {
        const p = useTeslaPairing(() => false)
        await p.loadPairingStatus()

        expect(teslaFleetService.getPairingStatus).not.toHaveBeenCalled()
        expect(p.pairingStatus.value).toBeNull()
        expect(p.pairingStatusLoaded.value).toBe(true)
    })

    it('laedt den Status, sobald der Account verbunden ist', async () => {
        vi.mocked(teslaFleetService.getPairingStatus).mockResolvedValue(status({ keyPaired: false }))
        const p = useTeslaPairing(() => true)
        await p.loadPairingStatus()

        expect(p.pairingStatus.value?.vin).toBe('VIN1')
        expect(p.pairingStatusLoaded.value).toBe(true)
        expect(p.pairingError.value).toBeNull()
    })

    it('meldet einen 404 als "noch nicht verbunden"', async () => {
        vi.mocked(teslaFleetService.getPairingStatus).mockRejectedValue(httpError(404))
        const p = useTeslaPairing(() => true)
        await p.loadPairingStatus()

        expect(p.pairingError.value).toBe('tesla.pairing_err_not_connected')
        expect(p.pairingStatusLoaded.value).toBe(true)
    })

    it('reicht die Server-Meldung durch, wenn eine da ist', async () => {
        vi.mocked(teslaFleetService.getPairingStatus).mockRejectedValue(httpError(500, 'Tesla down'))
        const p = useTeslaPairing(() => true)
        await p.loadPairingStatus()

        expect(p.pairingError.value).toBe('Tesla down')
    })
})

describe('useTeslaPairing - Auto-Aktivierung', () => {
    it('aktiviert Telemetry ohne Extra-Klick, sobald der Virtual Key gepaart ist', async () => {
        vi.mocked(teslaFleetService.getPairingStatus)
            .mockResolvedValueOnce(status({ keyPaired: true, telemetryConfigPushed: false }))
            .mockResolvedValue(status({ keyPaired: true, telemetryConfigPushed: true, dataSource: 'TELEMETRY' }))
        vi.mocked(teslaFleetService.enableTelemetry).mockResolvedValue({ alreadyEnabled: false })

        const p = useTeslaPairing(() => true)
        await p.loadPairingStatus()

        expect(teslaFleetService.enableTelemetry).toHaveBeenCalledOnce()
        expect(p.isTelemetryActive.value).toBe(true)
    })

    it('aktiviert nicht, solange der Virtual Key fehlt', async () => {
        vi.mocked(teslaFleetService.getPairingStatus).mockResolvedValue(status({ keyPaired: false }))
        const p = useTeslaPairing(() => true)
        await p.loadPairingStatus()

        expect(teslaFleetService.enableTelemetry).not.toHaveBeenCalled()
    })
})

describe('useTeslaPairing - Telemetry schalten', () => {
    it('laedt den Status nach dem Aktivieren neu', async () => {
        vi.mocked(teslaFleetService.enableTelemetry).mockResolvedValue({ alreadyEnabled: false })
        vi.mocked(teslaFleetService.getPairingStatus).mockResolvedValue(
            status({ keyPaired: true, telemetryConfigPushed: true, dataSource: 'TELEMETRY', telemetryProfile: 'FULL' }),
        )

        const p = useTeslaPairing(() => true)
        await p.enableTelemetry()

        expect(p.isTelemetryActive.value).toBe(true)
        expect(p.isFullProfile.value).toBe(true)
        expect(p.pairingLoading.value).toBe(false)
    })

    it('haelt den Fehler fest, wenn Tesla das Aktivieren ablehnt', async () => {
        vi.mocked(teslaFleetService.enableTelemetry).mockRejectedValue(httpError(400, 'key not paired'))
        const p = useTeslaPairing(() => true)
        await p.enableTelemetry()

        expect(p.pairingError.value).toBe('key not paired')
        expect(p.pairingLoading.value).toBe(false)
    })

    it('laedt den Status nach dem Deaktivieren neu', async () => {
        vi.mocked(teslaFleetService.disableTelemetry).mockResolvedValue()
        vi.mocked(teslaFleetService.getPairingStatus).mockResolvedValue(
            status({ keyPaired: true, telemetryConfigPushed: false }),
        )

        const p = useTeslaPairing(() => true)
        await p.disableTelemetry()

        expect(p.isTelemetryActive.value).toBe(false)
        expect(teslaFleetService.getPairingStatus).toHaveBeenCalled()
    })

    it('haelt den Fehler fest, wenn das Deaktivieren fehlschlaegt', async () => {
        vi.mocked(teslaFleetService.disableTelemetry).mockRejectedValue(httpError(500))
        const p = useTeslaPairing(() => true)
        await p.disableTelemetry()

        expect(p.pairingError.value).toBe('tesla.pairing_err_disable')
    })
})
