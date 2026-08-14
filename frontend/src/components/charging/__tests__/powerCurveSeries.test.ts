import { describe, it, expect } from 'vitest'
import { cumulativeKwh, buildSocSeries, socAtTs, yTickStepKw } from '../powerCurveSeries'

const MIN = 60_000

describe('cumulativeKwh', () => {
    it('integriert per Trapezregel ueber ungleichmaessige Abstaende', () => {
        // 60 kW konstant ueber 30 Min = 30 kWh, unabhaengig von der Sample-Dichte.
        const cum = cumulativeKwh([
            { ts: 0, kw: 60 },
            { ts: 1 * MIN, kw: 60 },
            { ts: 30 * MIN, kw: 60 },
        ])
        expect(cum[0]).toBe(0)
        expect(cum[2]).toBeCloseTo(30, 5)
    })

    it('liefert fuer einen Einzelpunkt nur den Nullwert', () => {
        expect(cumulativeKwh([{ ts: 0, kw: 100 }])).toEqual([0])
    })
})

describe('buildSocSeries', () => {
    it('nutzt die gemessenen SoC-Werte wenn alle Punkte einen haben', () => {
        const series = buildSocSeries(
            [
                { ts: 0, kw: 150, soc: 20 },
                { ts: 10 * MIN, kw: 100, soc: 45 },
                { ts: 20 * MIN, kw: 50, soc: 60 },
            ],
            10,
            99,
        )
        // Die gemessenen Werte gewinnen gegen die Log-Grenzen - der Log rundet,
        // die Telemetrie nicht.
        expect(series?.derived).toBe(false)
        expect(series?.values).toEqual([20, 45, 60])
    })

    it('leitet den Verlauf aus der kumulierten Energie ab wenn kein SoC gemessen wurde', () => {
        // Erste Haelfte 100 kW, zweite Haelfte 0 kW -> nach der Haelfte der Zeit
        // ist die gesamte Energie geflossen, der SoC also schon am Ende.
        const series = buildSocSeries(
            [
                { ts: 0, kw: 100 },
                { ts: 10 * MIN, kw: 100 },
                { ts: 20 * MIN, kw: 0 },
            ],
            20,
            60,
        )
        expect(series?.derived).toBe(true)
        expect(series!.values[0]).toBeCloseTo(20, 5)
        expect(series!.values[2]).toBeCloseTo(60, 5)
        // 100kW*10min = 16.67 kWh, danach das Trapez 100->0 ueber 10min = 8.33 kWh
        // -> nach 10 Min sind 2/3 der Energie geflossen.
        expect(series!.values[1]).toBeCloseTo(20 + (2 / 3) * 40, 3)
    })

    it('ist streng monoton steigend im abgeleiteten Modus', () => {
        const series = buildSocSeries(
            [
                { ts: 0, kw: 250 },
                { ts: 5 * MIN, kw: 180 },
                { ts: 10 * MIN, kw: 90 },
                { ts: 15 * MIN, kw: 40 },
            ],
            10,
            80,
        )
        const v = series!.values
        for (let i = 1; i < v.length; i++) expect(v[i]).toBeGreaterThan(v[i - 1])
    })

    it('faellt auf die Ableitung zurueck wenn nur ein Teil der Punkte SoC hat', () => {
        const series = buildSocSeries(
            [
                { ts: 0, kw: 100, soc: 20 },
                { ts: 10 * MIN, kw: 100 },
            ],
            20,
            40,
        )
        expect(series?.derived).toBe(true)
    })

    it('gibt null zurueck ohne SoC-Grenzen aus dem Log', () => {
        expect(buildSocSeries([{ ts: 0, kw: 100 }, { ts: MIN, kw: 100 }], null, null)).toBeNull()
        expect(buildSocSeries([{ ts: 0, kw: 100 }, { ts: MIN, kw: 100 }], 20, null)).toBeNull()
    })

    it('gibt null zurueck wenn der SoC nicht gestiegen ist', () => {
        // Gleicher oder fallender Ladestand ergibt keine sinnvolle Achse.
        expect(buildSocSeries([{ ts: 0, kw: 100 }, { ts: MIN, kw: 100 }], 50, 50)).toBeNull()
        expect(buildSocSeries([{ ts: 0, kw: 100 }, { ts: MIN, kw: 100 }], 50, 40)).toBeNull()
    })

    it('gibt null zurueck wenn keine Energie geflossen ist', () => {
        expect(buildSocSeries([{ ts: 0, kw: 0 }, { ts: MIN, kw: 0 }], 20, 60)).toBeNull()
    })

    it('gibt null zurueck bei weniger als zwei Punkten ohne gemessenen SoC', () => {
        expect(buildSocSeries([{ ts: 0, kw: 100 }], 20, 60)).toBeNull()
    })

    it('akzeptiert einen gemessenen SoC von 0 Prozent', () => {
        const series = buildSocSeries([{ ts: 0, kw: 100, soc: 0 }, { ts: MIN, kw: 100, soc: 4 }], null, null)
        expect(series?.derived).toBe(false)
        expect(series?.values).toEqual([0, 4])
    })
})

describe('socAtTs', () => {
    const points = [
        { ts: 0, kw: 100 },
        { ts: 10 * MIN, kw: 100 },
        { ts: 20 * MIN, kw: 100 },
    ]
    const values = [20, 40, 60]

    it('interpoliert linear zwischen zwei Stuetzpunkten', () => {
        expect(socAtTs(points, values, 5 * MIN)).toBeCloseTo(30, 5)
        expect(socAtTs(points, values, 15 * MIN)).toBeCloseTo(50, 5)
    })

    it('trifft die Stuetzpunkte exakt', () => {
        expect(socAtTs(points, values, 0)).toBe(20)
        expect(socAtTs(points, values, 20 * MIN)).toBe(60)
    })

    it('klemmt ausserhalb der Spanne auf die Randwerte', () => {
        expect(socAtTs(points, values, -MIN)).toBe(20)
        expect(socAtTs(points, values, 999 * MIN)).toBe(60)
    })

    it('gibt null zurueck ohne Werte', () => {
        expect(socAtTs([], [], 0)).toBeNull()
    })
})

describe('yTickStepKw', () => {
    const tickCount = (max: number) => Math.ceil(max / yTickStepKw(max)) - 1

    it('haelt die Zahl der Gitterlinien auch bei DC-Spitzen klein', () => {
        // 250 kW Peak ergab mit fester 25er-Schrittweite 10 Beschriftungen, die
        // sich gegenseitig und die Kurve ueberlagert haben.
        expect(yTickStepKw(263)).toBe(50)
        expect(tickCount(263)).toBeLessThanOrEqual(6)
    })

    it('bleibt bei AC-Leistungen fein genug', () => {
        expect(yTickStepKw(84)).toBe(25)
        expect(yTickStepKw(24)).toBe(10)
    })

    it('gibt fuer jede Spitze hoechstens 6 Gitterlinien', () => {
        for (const max of [20, 50, 84, 120, 180, 263, 420, 800]) {
            expect(tickCount(max)).toBeLessThanOrEqual(6)
            expect(tickCount(max)).toBeGreaterThanOrEqual(1)
        }
    })
})
