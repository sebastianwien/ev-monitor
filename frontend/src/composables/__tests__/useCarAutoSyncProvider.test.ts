import { describe, it, expect } from 'vitest'
import { autoSyncProviderFor, providerLabel, hasFreeDataSource } from '../useCarAutoSyncProvider'

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
