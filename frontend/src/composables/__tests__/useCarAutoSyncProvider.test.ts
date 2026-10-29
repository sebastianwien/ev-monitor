import { describe, it, expect } from 'vitest'
import { brandDeliversTrips, tripsComeByImport,
    autoSyncProviderFor, providerLabel, hasFreeDataSource, isAutoSyncUnsupported, hasOnlyUnsupportedBrands,
} from '../useCarAutoSyncProvider'

describe('autoSyncProviderFor', () => {
    it.each([
        ['Tesla', 'TESLA'],
        ['TESLA', 'TESLA'],
        ['tesla', 'TESLA'],
    ] as const)('Tesla brand %s -> TESLA', (brand, expected) => {
        expect(autoSyncProviderFor({ brand })).toBe(expected)
    })

    it.each([
        'BMW', 'VW', 'Mercedes', 'Audi', 'Porsche', 'Skoda', 'Hyundai',
        'Kia', 'Volvo', 'Polestar', 'Renault', 'Ford', 'MG', 'BYD',
    ])('Smartcar brand %s -> SMARTCAR', (brand) => {
        expect(autoSyncProviderFor({ brand })).toBe('SMARTCAR')
    })

    it('VW (Group) routes through Smartcar in the new flow, not VW Group integration', () => {
        // Per directive 2026-05-05: VW Group integration is hidden from AutoSync UI.
        expect(autoSyncProviderFor({ brand: 'VW' })).toBe('SMARTCAR')
        expect(autoSyncProviderFor({ brand: 'Audi' })).toBe('SMARTCAR')
        expect(autoSyncProviderFor({ brand: 'CUPRA' })).toBe('SMARTCAR')
    })

    it.each(['Citroën', 'CITROËN', 'Citroen', 'CITROEN'])('Handles Citroen with/without diaeresis: %s', (brand) => {
        expect(autoSyncProviderFor({ brand })).toBe('SMARTCAR')
    })

    it('Unknown brand -> NONE', () => {
        expect(autoSyncProviderFor({ brand: 'Lucid' })).toBe('NONE')
        expect(autoSyncProviderFor({ brand: 'Rivian' })).toBe('NONE')
    })

    it('Empty/missing brand -> NONE', () => {
        expect(autoSyncProviderFor({ brand: '' })).toBe('NONE')
        expect(autoSyncProviderFor({ brand: undefined as unknown as string })).toBe('NONE')
    })
})

describe('providerLabel', () => {
    it('Tesla', () => expect(providerLabel('TESLA')).toBe('Tesla Telemetry'))
    it('Smartcar', () => expect(providerLabel('SMARTCAR')).toBe('Smartcar'))
    it('None', () => expect(providerLabel('NONE')).toBe('Nicht verfügbar'))
})

describe('hasFreeDataSource', () => {
    it.each(['Tesla', 'TESLA', 'tesla'])('%s has a free data source', (brand) => {
        expect(hasFreeDataSource({ brand })).toBe(true)
    })

    /** XPeng laeuft ueber den EU-Data-Act-Import und zaehlt hier bewusst nicht mehr mit -
     *  der Hinweis auf der Supporter-Seite nennt nur noch Tesla. */
    it('XPeng has no free data source', () => {
        expect(hasFreeDataSource({ brand: 'XPeng' })).toBe(false)
    })

    it('Smartcar brands have no free data source - AutoSync must be purchased', () => {
        expect(hasFreeDataSource({ brand: 'VW' })).toBe(false)
        expect(hasFreeDataSource({ brand: 'BMW' })).toBe(false)
    })

    it('Unknown/empty brand -> false', () => {
        expect(hasFreeDataSource({ brand: 'Lucid' })).toBe(false)
        expect(hasFreeDataSource({ brand: '' })).toBe(false)
    })
})

describe('autoSyncProviderFor - Enum-Namen mit Unterstrich', () => {
    /** car.brand ist der CarBrand-Enum-Name (LAND_ROVER), die Smartcar-Liste führt "Land Rover". */
    it('LAND_ROVER ist eine Smartcar-Marke', () => {
        expect(autoSyncProviderFor({ brand: 'LAND_ROVER' })).toBe('SMARTCAR')
        expect(autoSyncProviderFor({ brand: 'Land Rover' })).toBe('SMARTCAR')
    })
})

describe('isAutoSyncUnsupported', () => {
    it.each(['ZEEKR', 'Zeekr', 'LEAPMOTOR', 'Leapmotor', 'LUCID', 'RIVIAN'])('%s hat keinen AutoSync-Weg', (brand) => {
        expect(isAutoSyncUnsupported({ brand })).toBe(true)
    })

    it('XPeng hat den EU-Data-Act-Weg und zählt nicht dazu', () => {
        expect(isAutoSyncUnsupported({ brand: 'XPENG' })).toBe(false)
        expect(isAutoSyncUnsupported({ brand: 'XPeng' })).toBe(false)
    })

    it('Tesla und Smartcar-Marken zählen nicht dazu', () => {
        expect(isAutoSyncUnsupported({ brand: 'TESLA' })).toBe(false)
        expect(isAutoSyncUnsupported({ brand: 'VW' })).toBe(false)
        expect(isAutoSyncUnsupported({ brand: 'LAND_ROVER' })).toBe(false)
    })

    it('eine fehlende Marke gilt nicht als nicht unterstützt', () => {
        expect(isAutoSyncUnsupported({ brand: '' })).toBe(false)
        expect(isAutoSyncUnsupported({ brand: undefined as unknown as string })).toBe(false)
    })
})

describe('hasOnlyUnsupportedBrands', () => {
    const zeekr = { brand: 'ZEEKR' }
    const leapmotor = { brand: 'LEAPMOTOR' }

    it('trifft eine Garage nur aus Marken ohne AutoSync', () => {
        expect(hasOnlyUnsupportedBrands([zeekr])).toBe(true)
        expect(hasOnlyUnsupportedBrands([zeekr, leapmotor])).toBe(true)
    })

    it('trifft eine leere Garage nicht - der Onboarding-Pitch bleibt', () => {
        expect(hasOnlyUnsupportedBrands([])).toBe(false)
    })

    it('trifft nicht, sobald ein Auto AutoSync nutzen kann', () => {
        expect(hasOnlyUnsupportedBrands([zeekr, { brand: 'VW' }])).toBe(false)
        expect(hasOnlyUnsupportedBrands([zeekr, { brand: 'TESLA' }])).toBe(false)
    })

    it('lässt XPeng-Garagen unberührt - dafür gibt es die eigene Seite', () => {
        expect(hasOnlyUnsupportedBrands([{ brand: 'XPENG' }])).toBe(false)
        expect(hasOnlyUnsupportedBrands([zeekr, { brand: 'XPENG' }])).toBe(false)
    })
})

describe('brandDeliversTrips', () => {
  it('Tesla, XPeng und Polestar liefern Fahrten, auch klein geschrieben', () => {
    for (const brand of ['TESLA', 'XPENG', 'POLESTAR', 'tesla']) expect(brandDeliversTrips({ brand })).toBe(true)
  })
  it('andere Marken und eine fehlende Marke nicht', () => {
    for (const brand of ['VOLKSWAGEN', 'BMW', '', null]) expect(brandDeliversTrips({ brand } as any)).toBe(false)
  })
})

describe('tripsComeByImport', () => {
  it('nur XPeng, auch mit Leerzeichen oder klein geschrieben', () => {
    expect(tripsComeByImport({ brand: ' xpeng ' })).toBe(true)
    expect(tripsComeByImport({ brand: 'TESLA' })).toBe(false)
    expect(tripsComeByImport({ brand: 'POLESTAR' })).toBe(false)
  })
})
