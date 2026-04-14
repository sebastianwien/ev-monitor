import { useCountryStore } from '../stores/country'
import { useAuthStore } from '../stores/auth'
import { isValidCountryCode, type CountryCode } from '../config/unitSystems'
import api from '../api/axios'

/**
 * Country detection waterfall (called once on app init):
 * 1. JWT claim 'country' (already handled by store.initFromJwt)
 * 2. localStorage 'ev-country' (only for unauthenticated users)
 * 3. GeoIP endpoint (nginx X-Country-Code header echo)
 * 4. navigator.language heuristic
 * 5. Default: DE
 *
 * For authenticated users without JWT country claim, GeoIP always runs
 * to populate the DB exactly once — localStorage alone is not sufficient.
 */
export async function detectCountry(): Promise<void> {
    const store = useCountryStore()
    const authStore = useAuthStore()

    // Step 1: JWT claim — if present, DB is already populated, nothing to do
    store.initFromJwt()
    const jwtCountry = authStore.user?.country
    if (jwtCountry && isValidCountryCode(jwtCountry)) {
        return
    }

    // Step 2: For unauthenticated users, localStorage is sufficient
    const saved = localStorage.getItem('ev-country')
    if (!authStore.isAuthenticated() && saved && isValidCountryCode(saved)) {
        return
    }

    // Step 3: Try GeoIP endpoint
    try {
        const response = await api.get('/geoip/country')
        const geoCountry = response.data?.country
        if (geoCountry && isValidCountryCode(geoCountry)) {
            store.setCountry(geoCountry as CountryCode)
            return
        }
    } catch {
        // GeoIP not available (local dev, network error) - continue to heuristic
    }

    // Step 4: navigator.language heuristic
    const detected = detectCountryFromLanguage()
    if (detected) {
        store.setCountry(detected)
        return
    }

    // Step 5: Default stays DE (set by store initialization)
}

const LANGUAGE_COUNTRY_MAP: Record<string, CountryCode> = {
    'en-GB': 'GB',
    'en-gb': 'GB',
    'nb-NO': 'NO',
    'nb-no': 'NO',
    'nn-NO': 'NO',
    'nn-no': 'NO',
    'sv-SE': 'SE',
    'sv-se': 'SE',
    'da-DK': 'DK',
    'da-dk': 'DK',
    'nl-NL': 'NL',
    'nl-nl': 'NL',
    'nl-BE': 'BE',
    'nl-be': 'BE',
    'fr-BE': 'BE',
    'fr-be': 'BE',
    'de-AT': 'AT',
    'de-at': 'AT',
    'de-CH': 'CH',
    'de-ch': 'CH',
    'fr-CH': 'CH',
    'fr-ch': 'CH',
    'it-CH': 'CH',
    'it-ch': 'CH',
    'de-DE': 'DE',
    'de-de': 'DE',
    'de':    'DE',
    'en-US': 'US',
    'en-us': 'US',
}

// Languages where detection maps to a country but the result is unreliable
// (en-US is used by many non-US English speakers, plain 'en' has no region)
const AMBIGUOUS_LANGUAGES = new Set(['en-US', 'en-us', 'en'])

export function detectCountryFromLanguage(): CountryCode | null {
    const languages = navigator.languages ?? [navigator.language]
    for (const lang of languages) {
        // Try exact match first (e.g. 'en-GB' -> GB)
        const exact = LANGUAGE_COUNTRY_MAP[lang]
        if (exact) return exact

        // Try with normalized case
        const normalized = LANGUAGE_COUNTRY_MAP[lang.toLowerCase()]
        if (normalized) return normalized
    }
    return null
}

/**
 * Returns true when the primary browser language is too vague to reliably
 * determine the user's country — e.g. plain 'en', 'en-US' (used by many
 * non-US English speakers), or when no language matches our map at all.
 * In these cases the registration form should ask the user to confirm.
 */
export function isDetectionAmbiguous(): boolean {
    const languages = navigator.languages ?? [navigator.language]
    const primary = languages[0] ?? ''
    // Can't map the browser language to any country
    if (!detectCountryFromLanguage()) return true
    // Non-specific English — could be UK, US, or anything else
    return AMBIGUOUS_LANGUAGES.has(primary)
}
