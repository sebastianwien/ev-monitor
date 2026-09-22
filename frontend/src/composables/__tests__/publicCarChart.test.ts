import { describe, it, expect } from 'vitest'
import { buildStepChart, peerScale, isWinterMonth } from '../publicCarChart'

const months = [
    { month: '2026-03-01', consumptionKwhPer100km: 28.5 },
    { month: '2026-04-01', consumptionKwhPer100km: 19.8 },
    { month: '2026-05-01', consumptionKwhPer100km: null },
    { month: '2026-06-01', consumptionKwhPer100km: 18.4 },
    { month: '2026-09-01', consumptionKwhPer100km: 27.0 },
]

describe('buildStepChart', () => {
    it('laesst Monate ohne Wert weg und haelt die Reihenfolge', () => {
        const c = buildStepChart(months, 18.5)
        expect(c.points.map(p => p.value)).toEqual([28.5, 19.8, 18.4, 27.0])
    })

    it('skaliert auf ganze Achsenschritte, die alle Werte und den Schnitt einschliessen', () => {
        const c = buildStepChart(months, 18.5)
        expect(c.axisMin).toBeLessThanOrEqual(18.4)
        expect(c.axisMax).toBeGreaterThanOrEqual(28.5)
        expect(c.ticks.every(t => Number.isInteger(t.value))).toBe(true)
        expect(c.ticks[0].value).toBe(c.axisMin)
        expect(c.ticks[c.ticks.length - 1].value).toBe(c.axisMax)
    })

    it('zeichnet eine Stufenlinie: gleiche Hoehe innerhalb eines Monats, Sprung an der Grenze', () => {
        const c = buildStepChart(months, 18.5)
        // Ein Segment pro Punkt, jeweils horizontal (H), dazwischen vertikal (V)
        expect(c.path.startsWith('M')).toBe(true)
        expect((c.path.match(/H/g) ?? []).length).toBe(4)
        expect((c.path.match(/V/g) ?? []).length).toBe(3)
    })

    it('hoehere Werte liegen weiter oben', () => {
        const c = buildStepChart(months, 18.5)
        const y = (v: number) => c.points.find(p => p.value === v)!.y
        expect(y(28.5)).toBeLessThan(y(18.4))
    })

    it('legt die Schnittlinie auf denselben Massstab', () => {
        const c = buildStepChart(months, 18.5)
        const y184 = c.points.find(p => p.value === 18.4)!.y
        const y198 = c.points.find(p => p.value === 19.8)!.y
        expect(c.avgY).toBeGreaterThan(y198)
        expect(c.avgY).toBeLessThan(y184)
    })

    it('ohne Schnitt gibt es keine Schnittlinie', () => {
        expect(buildStepChart(months, null).avgY).toBeNull()
    })

    it('markiert Wintermonate als zusammenhaengende Baender', () => {
        const c = buildStepChart(months, 18.5)
        // Nur Maerz ist Winter (Okt-Maerz), September nicht
        expect(c.winterBands.length).toBe(1)
        expect(c.winterBands[0].x).toBe(c.points[0].x)
        expect(c.winterBands[0].width).toBe(c.slotWidth)
    })

    it('liefert bei weniger als zwei Werten nichts', () => {
        expect(buildStepChart([months[0]], 18.5).points).toEqual([])
    })
})

describe('isWinterMonth', () => {
    it('Oktober bis Maerz sind Winter', () => {
        expect(isWinterMonth('2026-10-01')).toBe(true)
        expect(isWinterMonth('2026-03-01')).toBe(true)
        expect(isWinterMonth('2026-04-01')).toBe(false)
        expect(isWinterMonth('2026-09-01')).toBe(false)
    })
})

describe('peerScale', () => {
    it('Schnitt sitzt bei 50 Prozent, plus 40 Prozent Verbrauch am rechten Rand', () => {
        const s = peerScale(18.8, 18.5)
        expect(s.avgPos).toBe(50)
        expect(s.youPos).toBeGreaterThan(50)
        expect(peerScale(18.5 * 1.4, 18.5).youPos).toBe(96)
        expect(peerScale(18.5 * 0.6, 18.5).youPos).toBe(4)
    })

    it('beschriftet die Enden mit runden Werten der Skala', () => {
        const s = peerScale(18.8, 18.5)
        expect(s.minLabel).toBe(11)
        expect(s.maxLabel).toBe(26)
    })
})
