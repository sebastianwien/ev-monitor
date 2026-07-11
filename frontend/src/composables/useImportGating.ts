import { computed, type ComputedRef, type Ref } from 'vue'
import type { Car } from '../api/carService'

const brandIs = (car: Car | null | undefined, brand: string) => car?.brand?.toLowerCase() === brand

/**
 * Sichtbarkeits-Regeln der /imports-Sektionen. Alles haengt am **aktiven Auto**
 * (`isPrimary`, genau eines pro User) - dieselbe Auswahl, die der User im
 * Dashboard trifft.
 *
 * - **Tesla aktiv:** Fleet-Telemetry ist gratis, ein AutoSync-Upsell waere
 *   sinnlos. Die AutoSync-Sektion entfaellt komplett (samt Live-Promo), die
 *   Tesla-Sektion steht oben.
 * - **XPeng aktiv:** AutoSync wird angeboten - aber der XPeng-Weg (EU Data Act,
 *   wir fragen die Excel an, wir verarbeiten die Antwort). Kein Live: Live ist
 *   serverseitig Tesla-only. Kein Smartcar-Kauf-Pitch, solange der User kein
 *   Smartcar-Fahrzeug besitzt - das Abo wuerde seine XPeng-Daten nicht anfassen.
 * - **Sonst:** unveraenderter Smartcar-Pitch inkl. Kauf-Teaser.
 */
export function useImportGating(cars: Ref<Car[]>): {
    activeCarIsTesla: ComputedRef<boolean>
    activeCarIsXpeng: ComputedRef<boolean>
    showTeslaSection: ComputedRef<boolean>
    showAutoSyncSection: ComputedRef<boolean>
    showSmartcarPitch: ComputedRef<boolean>
    showXpengAutoSync: ComputedRef<boolean>
    allowLivePromo: ComputedRef<boolean>
    smartcarCars: ComputedRef<Car[]>
} {
    const carList = computed(() => (Array.isArray(cars.value) ? cars.value : []))
    const registeredCars = computed(() => carList.value.filter(c => c.status === 'ACTIVE'))

    // Aktives Auto: dieselbe Regel wie im Dashboard - primary, sonst das erste.
    const activeCar = computed(() => carList.value.find(c => c.isPrimary) ?? carList.value[0] ?? null)
    const activeCarIsTesla = computed(() => brandIs(activeCar.value, 'tesla'))
    const activeCarIsXpeng = computed(() => brandIs(activeCar.value, 'xpeng'))

    const hasAnyTesla = computed(() => carList.value.some(c => brandIs(c, 'tesla')))
    const hasAnyXpeng = computed(() => carList.value.some(c => brandIs(c, 'xpeng')))

    // Smartcar deckt weder Tesla (eigene Fleet-API) noch XPeng (EU-Data-Act-Mail) ab.
    const smartcarCars = computed(() =>
        registeredCars.value.filter(c => !brandIs(c, 'tesla') && !brandIs(c, 'xpeng')),
    )

    const showAutoSyncSection = computed(() => !activeCarIsTesla.value)

    return {
        activeCarIsTesla,
        activeCarIsXpeng,
        smartcarCars,
        showTeslaSection: hasAnyTesla,
        showAutoSyncSection,
        // Leere Garage: Pitch bleibt, sonst saehe ein frisch registrierter User eine leere Sektion.
        showSmartcarPitch: computed(() =>
            showAutoSyncSection.value && (smartcarCars.value.length > 0 || carList.value.length === 0),
        ),
        showXpengAutoSync: computed(() => showAutoSyncSection.value && hasAnyXpeng.value),
        allowLivePromo: computed(() =>
            hasAnyTesla.value && !activeCarIsTesla.value && !activeCarIsXpeng.value,
        ),
    }
}
