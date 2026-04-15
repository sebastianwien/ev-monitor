import { defineStore } from 'pinia'
import { ref, computed, readonly } from 'vue'
import { UNIT_SYSTEMS, isValidCountryCode, type CountryCode, type UnitSystem } from '../config/unitSystems'
import { EUR_ZONE_COUNTRIES } from '../config/unitSystems'
import api from '../api/axios'
import { useAuthStore } from './auth'

function safeLocalStorage(op: () => void): void {
    try { op() } catch { /* localStorage blocked */ }
}

function safeLocalStorageGet(key: string): string | null {
    try { return localStorage.getItem(key) } catch { return null }
}

export const useCountryStore = defineStore('country', () => {
    const savedCountry = safeLocalStorageGet('ev-country')
    const initialCountry: CountryCode = (savedCountry && isValidCountryCode(savedCountry)) ? savedCountry : 'DE'

    const country = ref<CountryCode>(initialCountry)

    /** In-memory only - wird vom Router für Market-spezifische URLs gesetzt.
     *  Überschreibt `country` für UnitSystem-Berechnung, ohne den User-State zu verändern.
     *  Wird beim Verlassen von Market-Routes vom Router gecleart (null). */
    const previewCountry = ref<CountryCode | null>(null)

    const unitSystem = computed<UnitSystem>(() => UNIT_SYSTEMS[previewCountry.value ?? country.value])

    const isEurZone = computed(() => EUR_ZONE_COUNTRIES.includes(previewCountry.value ?? country.value))

    function setCountry(code: CountryCode) {
        country.value = code
        safeLocalStorage(() => localStorage.setItem('ev-country', code))

        // If authenticated, persist to backend and get fresh JWT
        const authStore = useAuthStore()
        if (authStore.isAuthenticated() && !authStore.isExpired()) {
            api.put(`/users/me/country?country=${code}`)
                .then(response => {
                    if (response.data?.token) {
                        authStore.setToken(response.data.token)
                    }
                })
                .catch(() => { /* best-effort */ })
        }
    }

    /** Setzt temporären Country-Override für Market-Routes (kein localStorage, kein API-Call).
     *  null = Preview deaktivieren, zurück zum gespeicherten Country. */
    function setPreviewCountry(code: CountryCode | null) {
        previewCountry.value = code
    }

    /** Initialize from JWT claim if available */
    function initFromJwt() {
        const authStore = useAuthStore()
        if (authStore.user) {
            const jwtCountry = authStore.user?.country
            if (jwtCountry && isValidCountryCode(jwtCountry)) {
                country.value = jwtCountry
                safeLocalStorage(() => localStorage.setItem('ev-country', jwtCountry))
            }
        }
    }

    return {
        country,
        previewCountry: readonly(previewCountry),
        unitSystem,
        isEurZone,
        setCountry,
        setPreviewCountry,
        initFromJwt,
    }
})
