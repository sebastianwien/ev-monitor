import { ref, readonly } from 'vue'

/** Von wo aus das Teilen gestartet wurde - landet als Prop im Plausible-Event. */
export type CarShareSource = 'car_header' | 'peer_card' | 'car_management'

export interface CarShareSheetState {
    carId: string
    title: string
    source: CarShareSource
}

/**
 * Ein Sheet fuer alle Einstiege: Auto-Karte im Header, Vergleichskarte, spaeter mehr.
 * Der Zustand lebt einmal pro App, gerendert wird das Sheet im CarContextLayout.
 */
const state = ref<CarShareSheetState | null>(null)

export function useCarShareSheet() {
    function open(carId: string, title: string, source: CarShareSource): void {
        state.value = { carId, title, source }
    }
    function close(): void {
        state.value = null
    }
    return { sheet: readonly(state), open, close }
}
