export type CountryCode = 'DE' | 'AT' | 'CH' | 'GB' | 'NL' | 'BE' | 'DK' | 'NO' | 'SE'

export type ConsumptionUnit = 'kWh/100km' | 'mi/kWh' | 'kWh/mil'
export type DistanceUnit = 'km' | 'miles'
export type CurrencyCode = 'EUR' | 'GBP' | 'DKK' | 'NOK' | 'SEK'

export interface UnitSystem {
    consumptionUnit: ConsumptionUnit
    distanceUnit: DistanceUnit
    currency: CurrencyCode
    currencySymbol: string
    /** Subunit name for per-kWh display: 'ct', 'p', or null (use main unit) */
    currencySubunit: string | null
    /** Subunit divisor: 100 for ct/p, 1 if no subunit */
    currencySubunitDivisor: number
    numberLocale: string
    /** Whether consumption metric is inverse (higher = better, e.g. mi/kWh) */
    consumptionInverse: boolean
}

export const UNIT_SYSTEMS: Record<CountryCode, UnitSystem> = {
    DE: {
        consumptionUnit: 'kWh/100km',
        distanceUnit: 'km',
        currency: 'EUR',
        currencySymbol: '\u20ac',
        currencySubunit: 'ct',
        currencySubunitDivisor: 100,
        numberLocale: 'de-DE',
        consumptionInverse: false,
    },
    AT: {
        consumptionUnit: 'kWh/100km',
        distanceUnit: 'km',
        currency: 'EUR',
        currencySymbol: '\u20ac',
        currencySubunit: 'ct',
        currencySubunitDivisor: 100,
        numberLocale: 'de-AT',
        consumptionInverse: false,
    },
    CH: {
        consumptionUnit: 'kWh/100km',
        distanceUnit: 'km',
        currency: 'EUR',
        currencySymbol: '\u20ac',
        currencySubunit: 'ct',
        currencySubunitDivisor: 100,
        numberLocale: 'de-CH',
        consumptionInverse: false,
    },
    NL: {
        consumptionUnit: 'kWh/100km',
        distanceUnit: 'km',
        currency: 'EUR',
        currencySymbol: '\u20ac',
        currencySubunit: 'ct',
        currencySubunitDivisor: 100,
        numberLocale: 'nl-NL',
        consumptionInverse: false,
    },
    BE: {
        consumptionUnit: 'kWh/100km',
        distanceUnit: 'km',
        currency: 'EUR',
        currencySymbol: '\u20ac',
        currencySubunit: 'ct',
        currencySubunitDivisor: 100,
        numberLocale: 'nl-BE',
        consumptionInverse: false,
    },
    DK: {
        consumptionUnit: 'kWh/100km',
        distanceUnit: 'km',
        currency: 'DKK',
        currencySymbol: 'kr',
        currencySubunit: null,
        currencySubunitDivisor: 1,
        numberLocale: 'da-DK',
        consumptionInverse: false,
    },
    NO: {
        consumptionUnit: 'kWh/mil',
        distanceUnit: 'km',
        currency: 'NOK',
        currencySymbol: 'kr',
        currencySubunit: null,
        currencySubunitDivisor: 1,
        numberLocale: 'nb-NO',
        consumptionInverse: false,
    },
    SE: {
        consumptionUnit: 'kWh/mil',
        distanceUnit: 'km',
        currency: 'SEK',
        currencySymbol: 'kr',
        currencySubunit: null,
        currencySubunitDivisor: 1,
        numberLocale: 'sv-SE',
        consumptionInverse: false,
    },
    GB: {
        consumptionUnit: 'mi/kWh',
        distanceUnit: 'miles',
        currency: 'GBP',
        currencySymbol: '\u00a3',
        currencySubunit: 'p',
        currencySubunitDivisor: 100,
        numberLocale: 'en-GB',
        consumptionInverse: true,
    },
}

export const SUPPORTED_COUNTRIES: CountryCode[] = ['DE', 'AT', 'CH', 'GB', 'NL', 'BE', 'DK', 'NO', 'SE']

export const EUR_ZONE_COUNTRIES: CountryCode[] = ['DE', 'AT', 'CH', 'NL', 'BE']

export function isValidCountryCode(code: string): code is CountryCode {
    return SUPPORTED_COUNTRIES.includes(code as CountryCode)
}
