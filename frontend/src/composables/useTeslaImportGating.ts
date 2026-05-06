import { computed, type ComputedRef, type Ref } from 'vue'
import type { Car } from '../api/carService'

/**
 * Visibility gating for Tesla-related sections in /imports.
 *
 * - `hasAnyTesla`: user owns at least one Tesla, regardless of `status`. The
 *   AutoSync Tesla sub-section uses this to let users with not-yet-active
 *   Teslas still pair (selection happens via CarSelectDropdown filtered to
 *   brand=tesla).
 * - `showLegacyTeslaTab`: the standalone Tesla tab is reserved for the
 *   grandfathered audiences (ADMIN, BETA_TESTER, TESLA_FOUNDER). Premium-only
 *   users get the Tesla AutoSync sub-section in the AutoSync accordion instead
 *   - so the Smartcar-flavoured copy in the legacy tab no longer reaches them.
 */
export interface TeslaImportRoles {
    isAdmin: boolean
    isBetaTester: boolean
    isTeslaFounder: boolean
}

export function useTeslaImportGating(
    cars: Ref<Car[]>,
    roles: Ref<TeslaImportRoles> | TeslaImportRoles,
): { hasAnyTesla: ComputedRef<boolean>; showLegacyTeslaTab: ComputedRef<boolean> } {
    const hasAnyTesla = computed(() =>
        Array.isArray(cars.value) && cars.value.some(c => c.brand?.toLowerCase() === 'tesla'),
    )

    const showLegacyTeslaTab = computed(() => {
        const r = (roles as Ref<TeslaImportRoles>).value ?? (roles as TeslaImportRoles)
        return hasAnyTesla.value && (r.isAdmin || r.isBetaTester || r.isTeslaFounder)
    })

    return { hasAnyTesla, showLegacyTeslaTab }
}
