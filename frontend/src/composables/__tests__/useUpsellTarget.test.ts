import { describe, it, expect } from 'vitest'
import { analyticsUpsellTarget, upsellLink } from '../useUpsellTarget'

const tesla = { brand: 'Tesla' }
const xpeng = { brand: 'XPeng' }
const vw = { brand: 'VW' }
const lucid = { brand: 'Lucid' }

describe('analyticsUpsellTarget', () => {
    it('schickt Tesla-Fahrer ohne Abo auf die Supporter-Seite', () => {
        // Die Telemetrie laeuft bei ihnen gratis - es fehlt nur die Auswertung.
        expect(analyticsUpsellTarget([tesla], 'NONE')).toBe('/supporter')
    })

    it('schickt Polestar- und XPeng-Fahrer ohne Abo auf die Supporter-Seite', () => {
        // Ihre Fahrten kommen gratis (Data Portal, Data-Act-Import) - es fehlt nur die Auswertung.
        expect(analyticsUpsellTarget([xpeng], 'NONE')).toBe('/supporter')
        expect(analyticsUpsellTarget([{ brand: 'Polestar' }], 'NONE')).toBe('/supporter')
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

describe('upsellLink', () => {
    it('hängt die Herkunft an, damit Plausible den Platz kennt', () => {
        expect(upsellLink('/supporter', 'story_flow', true)).toBe('/supporter?from=story_flow')
    })

    it('liefert ohne Kaufrecht (native App) keinen Link', () => {
        expect(upsellLink('/upgrade', 'story_cost', false)).toBeNull()
    })
})
