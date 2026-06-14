import { computed, type ComputedRef, type Ref } from 'vue'
import type { Car } from '../api/carService'

/**
 * Visibility gating for the standalone "Tesla Telemetry" tab in /imports.
 *
 * Tesla Fleet-Telemetry is free for all Tesla drivers, so the tab is shown to
 * anyone who owns a Tesla (regardless of `status` or subscription) - that is
 * where Tesla pairing happens now. It is fully decoupled from the paid AutoSync
 * section, which only covers Smartcar brands (VW etc.).
 */
export function useTeslaImportGating(
    cars: Ref<Car[]>,
): { hasAnyTesla: ComputedRef<boolean>; showTeslaTab: ComputedRef<boolean> } {
    const hasAnyTesla = computed(() =>
        Array.isArray(cars.value) && cars.value.some(c => c.brand?.toLowerCase() === 'tesla'),
    )
    return { hasAnyTesla, showTeslaTab: hasAnyTesla }
}
