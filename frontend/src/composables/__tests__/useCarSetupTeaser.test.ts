import { describe, it, expect, beforeEach } from 'vitest'
import {
    carTeaserKind,
    isTeaserDismissed,
    dismissTeaser,
    TEASER_DISMISS_MS,
    type CarTeaserContext,
} from '../useCarSetupTeaser'
import type { Car } from '../../api/carService'

const car = (overrides: Partial<Car> = {}): Car => ({
    id: 'car-1',
    brand: 'BMW',
    model: 'i4',
    year: 2023,
    status: 'ACTIVE',
    isPrimary: true,
    ...overrides,
} as Car)

const ctx = (overrides: Partial<CarTeaserContext> = {}): CarTeaserContext => ({
    isPremium: false,
    isDemo: false,
    purchasesAvailable: true,
    teslaTelemetryActive: false,
    teslaConnectedCarId: null,
    ...overrides,
})

describe('carTeaserKind - Tesla', () => {
    const tesla = (overrides: Partial<Car> = {}) => car({ brand: 'Tesla', model: 'Model 3', ...overrides })

    it('zeigt den Telemetry-Teaser, wenn der Tesla-Account gar nicht verbunden ist', () => {
        expect(carTeaserKind(tesla(), ctx())).toBe('TELEMETRY')
    })

    it('zeigt den Telemetry-Teaser, wenn verbunden aber Telemetry nicht aktiv ist', () => {
        expect(carTeaserKind(tesla(), ctx({ teslaConnectedCarId: 'car-1' }))).toBe('TELEMETRY')
    })

    it('schweigt, wenn Telemetry fuer genau dieses Auto aktiv ist', () => {
        expect(carTeaserKind(tesla(), ctx({ teslaTelemetryActive: true, teslaConnectedCarId: 'car-1' }))).toBeNull()
    })

    it('schweigt, wenn Telemetry aktiv ist und das Backend kein Auto verknuepft hat', () => {
        // Gleiche Nachsicht wie das Tesla-Badge in der Car-Card: carId === null gilt als "dieses Auto".
        expect(carTeaserKind(tesla(), ctx({ teslaTelemetryActive: true, teslaConnectedCarId: null }))).toBeNull()
    })

    it('zeigt den Teaser fuer einen zweiten Tesla, dessen Auto nicht das verbundene ist', () => {
        const second = tesla({ id: 'car-2' })
        expect(carTeaserKind(second, ctx({ teslaTelemetryActive: true, teslaConnectedCarId: 'car-1' }))).toBe('TELEMETRY')
    })

    it('ignoriert Premium - Tesla-Telemetry ist gratis', () => {
        expect(carTeaserKind(tesla(), ctx({ isPremium: true }))).toBe('TELEMETRY')
    })

    it('zeigt den Telemetry-Teaser auch ohne Kaufmoeglichkeit (native App) - er verkauft nichts', () => {
        expect(carTeaserKind(tesla(), ctx({ purchasesAvailable: false }))).toBe('TELEMETRY')
    })
})

describe('carTeaserKind - AutoSync', () => {
    it('zeigt den AutoSync-Teaser fuer eine Smartcar-Marke ohne Abo', () => {
        expect(carTeaserKind(car({ brand: 'BMW' }), ctx())).toBe('AUTOSYNC')
        expect(carTeaserKind(car({ brand: 'Polestar' }), ctx())).toBe('AUTOSYNC')
    })

    it('schweigt, wenn AutoSync bereits gekauft ist', () => {
        expect(carTeaserKind(car({ brand: 'BMW' }), ctx({ isPremium: true }))).toBeNull()
    })

    it('schweigt in der nativen App - Kauf-CTAs sind dort gesperrt (Apple 3.1.1)', () => {
        expect(carTeaserKind(car({ brand: 'BMW' }), ctx({ purchasesAvailable: false }))).toBeNull()
    })

    it('schweigt bei Marken ohne Connector - das Abo wuerde ihre Daten nie anfassen', () => {
        expect(carTeaserKind(car({ brand: 'Rivian' }), ctx())).toBeNull()
        expect(carTeaserKind(car({ brand: 'Lucid' }), ctx())).toBeNull()
    })

    it('schweigt bei XPeng - laeuft ueber den EU-Data-Act-Weg, nicht ueber Smartcar', () => {
        expect(carTeaserKind(car({ brand: 'XPeng' }), ctx())).toBeNull()
    })
})

describe('carTeaserKind - generelle Sperren', () => {
    it('schweigt fuer abgemeldete Autos', () => {
        expect(carTeaserKind(car({ status: 'INACTIVE' }), ctx())).toBeNull()
        expect(carTeaserKind(car({ brand: 'Tesla', status: 'INACTIVE' }), ctx())).toBeNull()
    })

    it('teasert trotzdem, wenn das Backend gar keinen Status liefert', () => {
        expect(carTeaserKind(car({ status: undefined }), ctx())).toBe('AUTOSYNC')
    })

    it('schweigt im Demo-Account - dort wird nichts eingerichtet und nichts gekauft', () => {
        expect(carTeaserKind(car({ brand: 'BMW' }), ctx({ isDemo: true }))).toBeNull()
        expect(carTeaserKind(car({ brand: 'Tesla' }), ctx({ isDemo: true }))).toBeNull()
    })
})

describe('Dismiss-Persistenz', () => {
    beforeEach(() => localStorage.clear())

    it('gilt frisch nicht als weggeklickt', () => {
        expect(isTeaserDismissed('car-1', 'AUTOSYNC')).toBe(false)
    })

    it('unterdrueckt den Teaser direkt nach dem Wegklicken', () => {
        dismissTeaser('car-1', 'AUTOSYNC', 1_000)
        expect(isTeaserDismissed('car-1', 'AUTOSYNC', 1_000)).toBe(true)
    })

    it('haelt den Teaser die volle Dismiss-Dauer zurueck und laesst ihn danach wiederkommen', () => {
        dismissTeaser('car-1', 'AUTOSYNC', 1_000)
        expect(isTeaserDismissed('car-1', 'AUTOSYNC', 1_000 + TEASER_DISMISS_MS - 1)).toBe(true)
        expect(isTeaserDismissed('car-1', 'AUTOSYNC', 1_000 + TEASER_DISMISS_MS)).toBe(false)
    })

    it('trennt nach Auto und nach Teaser-Art', () => {
        dismissTeaser('car-1', 'AUTOSYNC', 1_000)
        expect(isTeaserDismissed('car-2', 'AUTOSYNC', 1_000)).toBe(false)
        expect(isTeaserDismissed('car-1', 'TELEMETRY', 1_000)).toBe(false)
    })

    it('ignoriert kaputte Eintraege, statt den Teaser fuer immer zu verschlucken', () => {
        localStorage.setItem('carTeaser.AUTOSYNC.car-1', 'nonsense')
        expect(isTeaserDismissed('car-1', 'AUTOSYNC')).toBe(false)
    })
})
