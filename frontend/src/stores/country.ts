import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
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

    const unitSystem = computed<UnitSystem>(() => UNIT_SYSTEMS[country.value])

    const isEurZone = computed(() => EUR_ZONE_COUNTRIES.includes(country.value))

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
        unitSystem,
        isEurZone,
        setCountry,
        initFromJwt,
    }
})
