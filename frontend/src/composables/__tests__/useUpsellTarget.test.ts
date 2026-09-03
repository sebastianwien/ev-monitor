import { describe, it, expect } from 'vitest'
import { analyticsUpsellTarget } from '../useUpsellTarget'

const tesla = { brand: 'Tesla' }
const xpeng = { brand: 'XPeng' }
const vw = { brand: 'VW' }
const lucid = { brand: 'Lucid' }

describe('analyticsUpsellTarget', () => {
    it('schickt Tesla-Fahrer ohne Abo auf die Supporter-Seite', () => {
        // Die Telemetrie laeuft bei ihnen gratis - es fehlt nur die Auswertung.
        expect(analyticsUpsellTarget([tesla], 'NONE')).toBe('/supporter')
    })

    it('schickt XPeng-Fahrer ohne Abo auf die Upgrade-Seite', () => {
        // Seit der Verengung von FREE_DATA_SOURCE_BRANDS auf Tesla zaehlt XPeng nicht
        // mehr als vorhandene Datenquelle. Wenn der EU-Data-Act-Weg dort weiterhin
        // gratis Daten liefert, ist das die falsche Seite - dann gehoert XPeng zurueck
        // ins Set und der Hinweistext auf der Supporter-Seite muss beide nennen.
        expect(analyticsUpsellTarget([xpeng], 'NONE')).toBe('/upgrade')
    })

    it('schickt Marken ohne Datenquelle auf die Upgrade-Seite', () => {
        // Ohne AutoSync gibt es nichts auszuwerten - Supporter waere Fehlverkauf.
        expect(analyticsUpsellTarget([vw], 'NONE')).toBe('/upgrade')
        expect(analyticsUpsellTarget([lucid], 'NONE')).toBe('/upgrade')
    })

    it('behandelt eine gemischte Garage wie Tesla', () => {
        // Eine laufende Datenquelle genuegt, damit das Supporter-Pack sofort etwas zeigt.
        expect(analyticsUpsellTarget([vw, tesla], 'NONE')).toBe('/supporter')
    })

    it('schickt AutoSync-Abonnenten auf die Upgrade-Seite', () => {
        // Fuer sie ist das In-Place-Upgrade auf AUTOSYNC_LIVE der passende Weg.
        expect(analyticsUpsellTarget([tesla], 'AUTOSYNC')).toBe('/upgrade')
        expect(analyticsUpsellTarget([vw], 'AUTOSYNC')).toBe('/upgrade')
    })

    it('faellt ohne bekannte Autos auf die Upgrade-Seite zurueck', () => {
        // Der Car-Store ist erst nach dem ersten Laden gefuellt - dann lieber die
        // allgemeine Seite als ein Pack, das der User vielleicht nicht nutzen kann.
        expect(analyticsUpsellTarget([], 'NONE')).toBe('/upgrade')
    })
})
