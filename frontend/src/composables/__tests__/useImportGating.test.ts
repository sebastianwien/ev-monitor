import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useImportGating, isXpengCar, isTeslaCar } from '../useImportGating'
import type { Car } from '../../api/carService'

function buildCar(overrides: Partial<Car>): Car {
    return {
        id: 'car-1',
        brand: 'Tesla',
        model: 'Model 3',
        year: 2022,
        status: 'ACTIVE',
        ...overrides,
    } as Car
}

const tesla = (o: Partial<Car> = {}) => buildCar({ id: 'tesla', brand: 'TESLA', ...o })
const xpeng = (o: Partial<Car> = {}) => buildCar({ id: 'xpeng', brand: 'XPENG', ...o })
const vw = (o: Partial<Car> = {}) => buildCar({ id: 'vw', brand: 'VW', ...o })
const polestar = (o: Partial<Car> = {}) => buildCar({ id: 'polestar', brand: 'Polestar', ...o })
const zeekr = (o: Partial<Car> = {}) => buildCar({ id: 'zeekr', brand: 'ZEEKR', ...o })
const leapmotor = (o: Partial<Car> = {}) => buildCar({ id: 'leapmotor', brand: 'LEAPMOTOR', ...o })
const landRover = (o: Partial<Car> = {}) => buildCar({ id: 'lr', brand: 'LAND_ROVER', ...o })

describe('useImportGating - aktives Auto', () => {
    it('erkennt den Polestar als aktives Auto, Schreibweise egal', () => {
        const g = useImportGating(ref([vw({ isPrimary: false }), polestar({ isPrimary: true })]))
        expect(g.activeCarIsPolestar.value).toBe(true)
        expect(useImportGating(ref([vw({ isPrimary: true }), polestar()])).activeCarIsPolestar.value).toBe(false)
    })

    it('erkennt den Tesla als aktives Auto (isPrimary)', () => {
        const g = useImportGating(ref([vw({ isPrimary: false }), tesla({ isPrimary: true })]))
        expect(g.activeCarIsTesla.value).toBe(true)
        expect(g.activeCarIsXpeng.value).toBe(false)
    })

    it('erkennt den XPeng als aktives Auto', () => {
        const g = useImportGating(ref([tesla({ isPrimary: false }), xpeng({ isPrimary: true })]))
        expect(g.activeCarIsXpeng.value).toBe(true)
        expect(g.activeCarIsTesla.value).toBe(false)
    })

    it('faellt auf das erste Auto zurueck, wenn keines als primary markiert ist (wie im Dashboard)', () => {
        const g = useImportGating(ref([tesla(), vw()]))
        expect(g.activeCarIsTesla.value).toBe(true)
    })

    it('markiert nichts als aktiv bei leerer Garage', () => {
        const g = useImportGating(ref([]))
        expect(g.activeCarIsTesla.value).toBe(false)
        expect(g.activeCarIsXpeng.value).toBe(false)
        expect(g.activeCarIsVwEudaBrand.value).toBe(false)
    })

    it('erkennt ein aktives VW-Group-Auto (EU-Data-Act-Tab soll offen starten)', () => {
        const g = useImportGating(ref([tesla({ isPrimary: false }), buildCar({ id: 'skoda', brand: 'SKODA', isPrimary: true })]))
        expect(g.activeCarIsVwEudaBrand.value).toBe(true)
        expect(g.activeCarIsTesla.value).toBe(false)
    })

    it('zaehlt einen VW-Group-Zweitwagen nicht, wenn ein anderes Auto aktiv ist', () => {
        const g = useImportGating(ref([tesla({ isPrimary: true }), vw({ isPrimary: false })]))
        expect(g.activeCarIsVwEudaBrand.value).toBe(false)
    })
})

describe('useImportGating - Tesla-Sektion', () => {
    it('zeigt die Tesla-Sektion jedem Tesla-Besitzer, egal welches Auto aktiv ist', () => {
        expect(useImportGating(ref([tesla({ isPrimary: false }), vw({ isPrimary: true })])).showTeslaSection.value).toBe(true)
    })

    it('zeigt sie auch fuer einen abgemeldeten Tesla (Telemetry ist gratis)', () => {
        expect(useImportGating(ref([tesla({ status: 'INACTIVE' as Car['status'] })])).showTeslaSection.value).toBe(true)
    })

    it('zeigt sie nicht ohne Tesla', () => {
        expect(useImportGating(ref([vw()])).showTeslaSection.value).toBe(false)
    })
})

describe('useImportGating - AutoSync-Sektion', () => {
    it('ist bei aktivem Tesla komplett ausgeblendet (Telemetry gratis, kein Upsell)', () => {
        expect(useImportGating(ref([tesla({ isPrimary: true })])).showAutoSyncSection.value).toBe(false)
    })

    it('ist bei aktivem XPeng sichtbar', () => {
        expect(useImportGating(ref([xpeng({ isPrimary: true })])).showAutoSyncSection.value).toBe(true)
    })

    it('ist sichtbar, wenn der Tesla zwar in der Garage steht, aber ein anderes Auto aktiv ist', () => {
        expect(useImportGating(ref([tesla({ isPrimary: false }), vw({ isPrimary: true })])).showAutoSyncSection.value).toBe(true)
    })
})

describe('useImportGating - Smartcar-Pitch (Kauf-Teaser + Picker)', () => {
    it('erscheint nicht fuer einen reinen XPeng-Fahrer - AutoSync-Abo wuerde seinem XPeng nichts bringen', () => {
        expect(useImportGating(ref([xpeng({ isPrimary: true })])).showSmartcarPitch.value).toBe(false)
    })

    it('erscheint, wenn der User ein Smartcar-Fahrzeug besitzt', () => {
        expect(useImportGating(ref([xpeng({ isPrimary: true }), vw()])).showSmartcarPitch.value).toBe(true)
    })

    it('erscheint nicht bei aktivem Tesla, auch wenn ein Smartcar-Auto in der Garage steht', () => {
        expect(useImportGating(ref([tesla({ isPrimary: true }), vw()])).showSmartcarPitch.value).toBe(false)
    })

    it('erscheint bei leerer Garage (Onboarding-Pitch bleibt erhalten)', () => {
        expect(useImportGating(ref([])).showSmartcarPitch.value).toBe(true)
    })

    it('ignoriert abgemeldete Smartcar-Fahrzeuge', () => {
        const g = useImportGating(ref([xpeng({ isPrimary: true }), vw({ status: 'INACTIVE' as Car['status'] })]))
        expect(g.showSmartcarPitch.value).toBe(false)
    })
})

describe('useImportGating - Marken ohne AutoSync (Zeekr, Leapmotor, ...)', () => {
    it('Smartcar-Pitch erscheint nicht für einen reinen Zeekr-Fahrer', () => {
        expect(useImportGating(ref([zeekr({ isPrimary: true })])).showSmartcarPitch.value).toBe(false)
    })

    it('Smartcar-Pitch erscheint nicht für Zeekr plus Leapmotor', () => {
        expect(useImportGating(ref([zeekr({ isPrimary: true }), leapmotor()])).showSmartcarPitch.value).toBe(false)
    })

    it('Smartcar-Pitch bleibt, wenn neben dem Zeekr ein Smartcar-Auto steht', () => {
        expect(useImportGating(ref([zeekr({ isPrimary: true }), vw()])).showSmartcarPitch.value).toBe(true)
    })

    it('smartcarCars enthält nur Marken, die Smartcar bedient - auch Land Rover (Enum-Name mit Unterstrich)', () => {
        const g = useImportGating(ref([zeekr({ isPrimary: true }), vw(), landRover(), tesla(), xpeng()]))
        expect(g.smartcarCars.value.map(c => c.id)).toEqual(['vw', 'lr'])
    })

    it('activeCarIsAutoSyncUnsupported folgt dem aktiven Auto, nicht der Garage', () => {
        expect(useImportGating(ref([zeekr({ isPrimary: true }), tesla()])).activeCarIsAutoSyncUnsupported.value).toBe(true)
        expect(useImportGating(ref([zeekr({ isPrimary: true }), vw()])).activeCarIsAutoSyncUnsupported.value).toBe(true)
        expect(useImportGating(ref([vw({ isPrimary: true }), zeekr()])).activeCarIsAutoSyncUnsupported.value).toBe(false)
        expect(useImportGating(ref([xpeng({ isPrimary: true })])).activeCarIsAutoSyncUnsupported.value).toBe(false)
        expect(useImportGating(ref([])).activeCarIsAutoSyncUnsupported.value).toBe(false)
    })

    it('onlyUnsupportedBrands trifft nur Garagen ohne ein Auto mit AutoSync-Weg', () => {
        expect(useImportGating(ref([zeekr(), leapmotor()])).onlyUnsupportedBrands.value).toBe(true)
        expect(useImportGating(ref([])).onlyUnsupportedBrands.value).toBe(false)
        expect(useImportGating(ref([zeekr(), vw()])).onlyUnsupportedBrands.value).toBe(false)
        expect(useImportGating(ref([zeekr(), xpeng()])).onlyUnsupportedBrands.value).toBe(false)
    })

    it('onlyUnsupportedBrands ignoriert abgemeldete Autos', () => {
        const g = useImportGating(ref([zeekr(), vw({ status: 'INACTIVE' as Car['status'] })]))
        expect(g.onlyUnsupportedBrands.value).toBe(true)
    })
})

describe('useImportGating - XPeng-AutoSync', () => {
    it('wird jedem XPeng-Besitzer angeboten, solange die AutoSync-Sektion sichtbar ist', () => {
        expect(useImportGating(ref([xpeng({ isPrimary: true })])).showXpengAutoSync.value).toBe(true)
        expect(useImportGating(ref([vw({ isPrimary: true }), xpeng()])).showXpengAutoSync.value).toBe(true)
    })

    it('verschwindet mit der AutoSync-Sektion, wenn der Tesla das aktive Auto ist', () => {
        expect(useImportGating(ref([tesla({ isPrimary: true }), xpeng()])).showXpengAutoSync.value).toBe(false)
    })

    it('wird ohne XPeng nicht angeboten', () => {
        expect(useImportGating(ref([vw({ isPrimary: true })])).showXpengAutoSync.value).toBe(false)
    })
})

describe('useImportGating - Live-Promo', () => {
    it('ist bei aktivem Tesla gesperrt', () => {
        expect(useImportGating(ref([tesla({ isPrimary: true })])).allowLivePromo.value).toBe(false)
    })

    it('ist bei aktivem XPeng gesperrt - Live ist fuer XPeng kein Thema', () => {
        expect(useImportGating(ref([xpeng({ isPrimary: true }), tesla()])).allowLivePromo.value).toBe(false)
    })

    it('ist erlaubt, wenn ein Tesla in der Garage steht und ein Smartcar-Auto aktiv ist', () => {
        expect(useImportGating(ref([vw({ isPrimary: true }), tesla()])).allowLivePromo.value).toBe(true)
    })

    it('ist ohne Tesla gesperrt - Live ist serverseitig Tesla-only', () => {
        expect(useImportGating(ref([vw({ isPrimary: true })])).allowLivePromo.value).toBe(false)
    })
})

describe('isXpengCar', () => {
    it('erkennt ein frisch angelegtes XPeng-Fahrzeug', () => {
        expect(isXpengCar(xpeng())).toBe(true)
    })

    it('ist unabhaengig von der Schreibweise der Marke', () => {
        expect(isXpengCar(buildCar({ brand: 'xpeng' }))).toBe(true)
    })

    it('trifft nicht auf andere Marken zu', () => {
        expect(isXpengCar(tesla())).toBe(false)
        expect(isXpengCar(vw())).toBe(false)
    })

    it('kommt mit fehlendem Auto klar', () => {
        expect(isXpengCar(null)).toBe(false)
    })
})

describe('isTeslaCar', () => {
    it('erkennt ein frisch angelegtes Tesla-Fahrzeug', () => {
        expect(isTeslaCar(tesla())).toBe(true)
    })

    it('ist unabhaengig von der Schreibweise der Marke', () => {
        expect(isTeslaCar(buildCar({ brand: 'tesla' }))).toBe(true)
    })

    it('trifft nicht auf andere Marken zu', () => {
        expect(isTeslaCar(xpeng())).toBe(false)
        expect(isTeslaCar(vw())).toBe(false)
    })

    it('kommt mit fehlendem Auto klar', () => {
        expect(isTeslaCar(null)).toBe(false)
    })
})
