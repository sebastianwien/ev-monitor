import { describe, it, expect } from 'vitest'
import {
    convertConsumption,
    convertDistance,
    convertCurrency,
    convertCostPerDistance,
    consumptionDeltaPercent,
    odometerKmToLocal,
    odometerLocalToKm,
    KM_PER_MILE,
} from '../unitConversions'
import { UNIT_SYSTEMS } from '../../config/unitSystems'

describe('convertConsumption', () => {
    // Tesla Model 3 typical: 18.3 kWh/100km
    const typical = 18.3

    it('passes through for kWh/100km (DE/NL/BE/DK)', () => {
        expect(convertConsumption(typical, 'kWh/100km')).toBe(typical)
    })

    it('converts to kWh/mil for NO/SE (divide by 10)', () => {
        expect(convertConsumption(typical, 'kWh/mil')).toBeCloseTo(1.83, 2)
    })

    it('converts to mi/kWh for GB (inverse)', () => {
        // 62.1371 / 18.3 = ~3.395
        expect(convertConsumption(typical, 'mi/kWh')).toBeCloseTo(3.395, 2)
    })

    it('returns 0 for zero consumption', () => {
        expect(convertConsumption(0, 'mi/kWh')).toBe(0)
    })

    it('returns 0 for negative consumption', () => {
        expect(convertConsumption(-5, 'kWh/mil')).toBe(0)
    })

    it('handles very efficient car (10 kWh/100km)', () => {
        expect(convertConsumption(10, 'mi/kWh')).toBeCloseTo(6.214, 2)
        expect(convertConsumption(10, 'kWh/mil')).toBeCloseTo(1.0, 2)
    })

    it('handles high consumption (30 kWh/100km, e.g. SUV winter)', () => {
        expect(convertConsumption(30, 'mi/kWh')).toBeCloseTo(2.071, 2)
        expect(convertConsumption(30, 'kWh/mil')).toBeCloseTo(3.0, 2)
    })
})

describe('convertDistance', () => {
    it('passes through for km', () => {
        expect(convertDistance(100, false)).toBe(100)
    })

    it('converts km to miles', () => {
        // 100 km = 62.14 miles
        expect(convertDistance(100, true)).toBeCloseTo(62.14, 1)
    })

    it('converts 1 km to miles', () => {
        expect(convertDistance(1, true)).toBeCloseTo(0.6214, 3)
    })

    it('handles zero', () => {
        expect(convertDistance(0, true)).toBe(0)
    })
})

describe('odometerKmToLocal', () => {
    it('passes through when not miles', () => {
        expect(odometerKmToLocal(12345, false)).toBe(12345)
    })

    it('converts km to miles and rounds to integer', () => {
        // 161 km / 1.60934 = 100.04 → rounded to 100
        expect(odometerKmToLocal(161, true)).toBe(100)
    })

    it('rounds correctly for non-integer results', () => {
        // 100 km / 1.60934 = 62.14 → rounded to 62
        expect(odometerKmToLocal(100, true)).toBe(62)
    })

    it('handles zero', () => {
        expect(odometerKmToLocal(0, true)).toBe(0)
    })
})

describe('odometerLocalToKm', () => {
    it('passes through when not miles', () => {
        expect(odometerLocalToKm(12345, false)).toBe(12345)
    })

    it('converts miles to km and rounds to integer', () => {
        // 100 miles * 1.60934 = 160.934 → rounded to 161
        expect(odometerLocalToKm(100, true)).toBe(161)
    })

    it('handles zero', () => {
        expect(odometerLocalToKm(0, true)).toBe(0)
    })

    it('roundtrip: km → miles → km stays within 1 km', () => {
        const original = 50000
        const miles = odometerKmToLocal(original, true)
        const roundtripped = odometerLocalToKm(miles, true)
        expect(Math.abs(roundtripped - original)).toBeLessThanOrEqual(1)
    })
})

describe('KM_PER_MILE', () => {
    it('is exported and equals 1.60934', () => {
        expect(KM_PER_MILE).toBe(1.60934)
    })
})

describe('convertCurrency', () => {
    it('EUR to EUR is 1:1', () => {
        expect(convertCurrency(10, 'EUR')).toBe(10)
    })

    it('EUR to GBP', () => {
        // 10 EUR * 0.86 = 8.6 GBP
        expect(convertCurrency(10, 'GBP')).toBeCloseTo(8.6, 2)
    })

    it('EUR to NOK', () => {
        // 10 EUR * 11.5 = 115 NOK
        expect(convertCurrency(10, 'NOK')).toBeCloseTo(115, 0)
    })

    it('EUR to SEK', () => {
        // 10 EUR * 11.2 = 112 SEK
        expect(convertCurrency(10, 'SEK')).toBeCloseTo(112, 0)
    })

    it('EUR to DKK', () => {
        // 10 EUR * 7.45 = 74.5 DKK
        expect(convertCurrency(10, 'DKK')).toBeCloseTo(74.5, 1)
    })

    it('handles zero', () => {
        expect(convertCurrency(0, 'GBP')).toBe(0)
    })
})

describe('convertCostPerDistance', () => {
    // Typical: 5.80 EUR/100km

    it('EUR zone: passes through with currency conversion only', () => {
        const result = convertCostPerDistance(5.80, UNIT_SYSTEMS.DE)
        expect(result).toBeCloseTo(5.80, 2) // EUR * 1 = EUR
    })

    it('DK: converts to DKK/100km', () => {
        const result = convertCostPerDistance(5.80, UNIT_SYSTEMS.DK)
        // 5.80 * 7.45 = 43.21 DKK/100km
        expect(result).toBeCloseTo(43.21, 0)
    })

    it('NO: converts to NOK/100km', () => {
        const result = convertCostPerDistance(5.80, UNIT_SYSTEMS.NO)
        // 5.80 * 11.5 = 66.70 NOK/100km
        expect(result).toBeCloseTo(66.70, 0)
    })

    it('GB: converts to GBP/100mi (more expensive because 100mi > 100km)', () => {
        const result = convertCostPerDistance(5.80, UNIT_SYSTEMS.GB)
        // 5.80 * 0.86 * 1.60934 = 8.03 GBP/100mi
        expect(result).toBeCloseTo(8.03, 1)
    })
})

describe('consumptionDeltaPercent', () => {
    it('positive when real > WLTP (worse)', () => {
        // Real: 20 kWh/100km, WLTP: 18 kWh/100km
        const delta = consumptionDeltaPercent(20, 18)
        expect(delta).toBeCloseTo(11.1, 1) // +11.1%
    })

    it('negative when real < WLTP (better)', () => {
        // Real: 16 kWh/100km, WLTP: 18 kWh/100km
        const delta = consumptionDeltaPercent(16, 18)
        expect(delta).toBeCloseTo(-11.1, 1) // -11.1%
    })

    it('zero when equal', () => {
        expect(consumptionDeltaPercent(18, 18)).toBe(0)
    })

    it('returns 0 for zero WLTP (prevents division by zero)', () => {
        expect(consumptionDeltaPercent(18, 0)).toBe(0)
    })

    it('works consistently regardless of unit system (computed in kWh/100km space)', () => {
        // The delta is always computed in kWh/100km space
        // So +11.1% always means "worse than WLTP" regardless of display unit
        const real = 20
        const wltp = 18
        const delta = consumptionDeltaPercent(real, wltp)

        // For GB (mi/kWh): 18 kWh/100km = 3.45 mi/kWh, 20 kWh/100km = 3.11 mi/kWh
        // mi/kWh is LOWER when consumption is HIGHER = worse
        // Delta should still be +11.1% (positive = worse)
        expect(delta).toBeGreaterThan(0)
    })
})

describe('unit system configuration consistency', () => {
    it('all EUR zone countries use EUR currency', () => {
        for (const code of ['DE', 'AT', 'CH', 'NL', 'BE'] as const) {
            expect(UNIT_SYSTEMS[code].currency).toBe('EUR')
        }
    })

    it('only GB and US use miles', () => {
        const milesCountries = new Set(['GB', 'US'])
        for (const [code, sys] of Object.entries(UNIT_SYSTEMS)) {
            if (milesCountries.has(code)) {
                expect(sys.distanceUnit).toBe('miles')
            } else {
                expect(sys.distanceUnit).toBe('km')
            }
        }
    })

    it('only GB and US have consumptionInverse = true', () => {
        const inverseCountries = new Set(['GB', 'US'])
        for (const [code, sys] of Object.entries(UNIT_SYSTEMS)) {
            if (inverseCountries.has(code)) {
                expect(sys.consumptionInverse).toBe(true)
            } else {
                expect(sys.consumptionInverse).toBe(false)
            }
        }
    })

    it('NO and SE use kWh/mil', () => {
        expect(UNIT_SYSTEMS.NO.consumptionUnit).toBe('kWh/mil')
        expect(UNIT_SYSTEMS.SE.consumptionUnit).toBe('kWh/mil')
    })

    it('subunit divisor is 100 for ct and p, 1 for null', () => {
        for (const sys of Object.values(UNIT_SYSTEMS)) {
            if (sys.currencySubunit) {
                expect(sys.currencySubunitDivisor).toBe(100)
            } else {
                expect(sys.currencySubunitDivisor).toBe(1)
            }
        }
    })
})
