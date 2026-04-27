import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { detectMarket, getMarketBasePath, type Market } from './useMarketRoute'
import { useCountryStore } from '../stores/country'
import { EUR_ZONE_COUNTRIES, type UnitSystem } from '../config/unitSystems'
import { convertFromEur } from '../config/exchangeRates'
import {
    convertConsumption as pureConvertConsumption,
    convertDistance as pureConvertDistance,
    convertCostPerDistance as pureConvertCostPerDistance,
    consumptionDeltaPercent as pureConsumptionDeltaPercent,
} from '../utils/unitConversions'

// ────────────────────────────────────────────────────────────────
// Existing: Number Formatting (unchanged API)
// ────────────────────────────────────────────────────────────────

export function useLocaleFormat() {
    const { locale } = useI18n()
    const countryStore = useCountryStore()

    const numberLocale = computed(() => locale.value === 'en' ? 'en-GB' : 'de-DE')

    function formatNumber(value: number): string {
        return value.toLocaleString(numberLocale.value)
    }

    function formatDecimal(value: number, decimals = 1): string {
        return value.toLocaleString(numberLocale.value, {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        })
    }

    // ────────────────────────────────────────────────────────────
    // Unit System (reactive, from country store)
    // ────────────────────────────────────────────────────────────

    const unitSystem = computed<UnitSystem>(() => countryStore.unitSystem)
    const isImperial = computed(() => unitSystem.value.distanceUnit === 'miles')
    const isEurZone = computed(() => EUR_ZONE_COUNTRIES.includes(countryStore.country))

    // ────────────────────────────────────────────────────────────
    // Consumption (input always kWh/100km from backend)
    // ────────────────────────────────────────────────────────────

    /** Convert kWh/100km to the user's consumption unit */
    function convertConsumption(kwhPer100km: number): number {
        return pureConvertConsumption(kwhPer100km, unitSystem.value.consumptionUnit)
    }

    /**
     * Format consumption with unit label.
     * @param kwhPer100km Raw value from backend
     * @param opts.decimals Decimal places (default 1)
     * @param opts.showUnit Append unit label (default true)
     */
    function formatConsumption(kwhPer100km: number | null, opts?: { decimals?: number, showUnit?: boolean }): string {
        if (kwhPer100km === null || kwhPer100km === undefined) return '–'
        const decimals = opts?.decimals ?? 1
        const showUnit = opts?.showUnit ?? true
        const converted = convertConsumption(kwhPer100km)
        const formatted = converted.toLocaleString(numberLocale.value, {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals,
        })
        return showUnit ? `${formatted} ${unitSystem.value.consumptionUnit}` : formatted
    }

    /** The consumption unit label string */
    function consumptionUnitLabel(): string {
        return unitSystem.value.consumptionUnit
    }

    // ────────────────────────────────────────────────────────────
    // Distance (input always km from backend)
    // ────────────────────────────────────────────────────────────

    /** Convert km to user's distance unit */
    function convertDistance(km: number): number {
        return pureConvertDistance(km, isImperial.value)
    }

    /**
     * Format distance with unit label.
     * @param km Raw km value from backend
     * @param opts.showUnit Append unit label (default true)
     * @param opts.round Round to integer (default true)
     */
    function formatDistance(km: number, opts?: { showUnit?: boolean, round?: boolean }): string {
        const showUnit = opts?.showUnit ?? true
        const round = opts?.round ?? true
        const converted = convertDistance(km)
        const formatted = round
            ? Math.round(converted).toLocaleString(numberLocale.value)
            : converted.toLocaleString(numberLocale.value, { maximumFractionDigits: 1 })
        return showUnit ? `${formatted} ${unitSystem.value.distanceUnit}` : formatted
    }

    /** The distance unit label string */
    function distanceUnitLabel(): string {
        return unitSystem.value.distanceUnit
    }

    // ────────────────────────────────────────────────────────────
    // Currency (input always EUR from backend)
    // ────────────────────────────────────────────────────────────

    const currency = computed(() => unitSystem.value.currency)
    const currencySymbol = computed(() => unitSystem.value.currencySymbol)

    /**
     * Format a cost value (EUR from backend) in user's currency.
     * e.g. '42,50 EUR', '£37.20', '480 kr'
     */
    function formatCurrency(eur: number, opts?: { decimals?: number }): string {
        const decimals = opts?.decimals ?? 2
        const converted = convertFromEur(eur, currency.value)
        const formatted = converted.toLocaleString(numberLocale.value, {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals,
        })
        // GBP: symbol before value, others: value + symbol after
        if (currency.value === 'GBP') {
            return `${unitSystem.value.currencySymbol}${formatted}`
        }
        return `${formatted} ${unitSystem.value.currencySymbol}`
    }

    /**
     * Format cost per kWh (EUR/kWh from backend) in user's currency.
     * Uses subunit where available: '32 ct/kWh', '28p/kWh', '3,68 kr/kWh'
     */
    function formatCostPerKwh(eurPerKwh: number): string {
        const converted = convertFromEur(eurPerKwh, currency.value)
        const sub = unitSystem.value.currencySubunit
        if (sub) {
            // Use subunit (ct, p) - multiply by 100
            const subValue = converted * unitSystem.value.currencySubunitDivisor
            const formatted = subValue.toLocaleString(numberLocale.value, {
                minimumFractionDigits: 1,
                maximumFractionDigits: 1,
            })
            // p/kWh (GBP) vs ct/kWh (EUR)
            return currency.value === 'GBP' ? `${formatted}${sub}/kWh` : `${formatted} ${sub}/kWh`
        }
        // NOK/SEK/DKK: use main unit
        const formatted = converted.toLocaleString(numberLocale.value, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        })
        return `${formatted} ${unitSystem.value.currencySymbol}/kWh`
    }

    /**
     * Format cost per distance unit.
     * e.g. '5,80 EUR/100km', '£4.10/100mi', '43 kr/100km'
     */
    function formatCostPerDistance(eurPer100km: number): string {
        const perDistanceUnit = pureConvertCostPerDistance(eurPer100km, unitSystem.value)
        const formatted = perDistanceUnit.toLocaleString(numberLocale.value, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        })
        const distUnit = isImperial.value ? '100mi' : '100km'
        if (currency.value === 'GBP') {
            return `${unitSystem.value.currencySymbol}${formatted}/${distUnit}`
        }
        return `${formatted} ${unitSystem.value.currencySymbol}/${distUnit}`
    }

    // ────────────────────────────────────────────────────────────
    // Consumption Comparison Utilities
    // ────────────────────────────────────────────────────────────

    /**
     * Compute the WLTP delta percentage, accounting for unit direction.
     * For kWh/100km and kWh/mil: positive = worse (higher consumption).
     * For mi/kWh: positive = worse means the converted value is LOWER.
     */
    function consumptionDeltaPercent(realKwhPer100km: number, wltpKwhPer100km: number): number {
        return pureConsumptionDeltaPercent(realKwhPer100km, wltpKwhPer100km)
    }

    /**
     * Format the delta label in display unit space.
     * kWh/100km: negative = better (less consumption). mi/kWh: positive = better (more range).
     */
    function consumptionDeltaLabel(realKwhPer100km: number | null, wltpKwhPer100km: number | null): string {
        if (!realKwhPer100km || !wltpKwhPer100km) return ''
        let displayDelta: number
        if (unitSystem.value.consumptionInverse) {
            // mi/kWh space: (official - real) / real * 100  →  positive = more mi/kWh = better
            displayDelta = (wltpKwhPer100km - realKwhPer100km) / realKwhPer100km * 100
        } else {
            displayDelta = consumptionDeltaPercent(realKwhPer100km, wltpKwhPer100km)
        }
        const sign = displayDelta > 0 ? '+' : ''
        return `${sign}${displayDelta.toFixed(1)}%`
    }

    /**
     * CSS class for delta display (green = better, red = worse).
     * Always computed in kWh/100km space so the semantics are consistent.
     */
    function consumptionDeltaClass(realKwhPer100km: number | null, wltpKwhPer100km: number | null): string {
        if (!realKwhPer100km || !wltpKwhPer100km) return ''
        const delta = consumptionDeltaPercent(realKwhPer100km, wltpKwhPer100km)
        if (delta > 5) return 'text-red-600 dark:text-red-400'
        if (delta < -5) return 'text-green-600 dark:text-green-400'
        return 'text-gray-600 dark:text-gray-400'
    }

    return {
        // Existing
        locale,
        formatNumber,
        formatDecimal,

        // Unit system state
        unitSystem,
        isImperial,
        isEurZone,

        // Consumption
        convertConsumption,
        formatConsumption,
        consumptionUnitLabel,

        // Distance
        convertDistance,
        formatDistance,
        distanceUnitLabel,

        // Currency
        currency,
        currencySymbol,
        formatCurrency,
        formatCostPerKwh,
        formatCostPerDistance,

        // Comparison
        consumptionDeltaPercent,
        consumptionDeltaLabel,
        consumptionDeltaClass,
    }
}

export function useLocaleRoutes() {
    const route = useRoute()

    const LOCALE_TO_MARKET: Record<string, Market> = {
        de: 'de', en: 'en', nb: 'no', sv: 'se',
    }

    const LOCALE_ROOTS: Record<string, string> = {
        de: '/', en: '/en', nb: '/en', sv: '/en',
    }

    function getAlternateUrl(targetLocale: string): string {
        const path = route.path
        const currentMarket = detectMarket(path)
        const currentBase = getMarketBasePath(currentMarket)

        // On known market routes: preserve the brand/model suffix
        if (path.startsWith(currentBase)) {
            const suffix = path.slice(currentBase.length)
            const targetMarket = LOCALE_TO_MARKET[targetLocale]
            if (targetMarket) return getMarketBasePath(targetMarket) + suffix
        }

        // Non-market routes (login, dashboard, ...): fall back to locale root
        return LOCALE_ROOTS[targetLocale] ?? '/en'
    }

    function getCanonicalBase(): string {
        return 'https://ev-monitor.net'
    }

    return { getAlternateUrl, getCanonicalBase }
}
