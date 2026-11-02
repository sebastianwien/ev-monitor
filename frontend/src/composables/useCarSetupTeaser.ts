import { ref, type Ref } from 'vue'
import type { Car } from '../api/carService'
import type { TeslaConnectionStatus } from '../api/teslaFleetService'
import teslaFleetService from '../api/teslaFleetService'
import { autoSyncProviderFor } from './useCarAutoSyncProvider'
import { purchasesAvailable } from '../utils/iapPolicy'
import { useAuthStore } from '../stores/auth'

/**
 * Teaser ueber einer Auto-Card in /cars: das Auto koennte seine Daten automatisch
 * liefern, tut es aber nicht.
 *
 * - **TELEMETRY** - Tesla ohne aktive Fleet-Telemetry. Gratis, also unabhaengig vom Abo.
 * - **AUTOSYNC** - Smartcar-Marke ohne AutoSync-Abo. Kauf-CTA.
 *
 * Marken ohne Connector (Provider NONE, z.B. Lucid, Rivian) und XPeng (EU-Data-Act-Weg
 * statt Smartcar) bekommen bewusst keinen Teaser: das Abo wuerde ihre Daten nicht anfassen.
 */
export type CarTeaserKind = 'TELEMETRY' | 'AUTOSYNC'

export interface CarTeaserContext {
    isPremium: boolean
    isDemo: boolean
    /** Kauf-CTAs sind in der nativen App gesperrt (Apple Guideline 3.1.1). */
    purchasesAvailable: boolean
    teslaTelemetryActive: boolean
    /** Das Auto, mit dem der Tesla-Account verknuepft ist - null, wenn keine Zuordnung existiert. */
    teslaConnectedCarId: string | null
}

/** Reine Regel - ohne Stores, ohne Netzwerk, damit sie testbar bleibt. */
export function carTeaserKind(car: Car, ctx: CarTeaserContext): CarTeaserKind | null {
    // Nur explizit abgemeldete Autos schweigen - ein fehlender Status ist kein Grund.
    if (car.status === 'INACTIVE') return null
    if (ctx.isDemo) return null

    switch (autoSyncProviderFor(car)) {
        case 'TESLA': {
            // carId === null gilt als "dieses Auto" - dieselbe Nachsicht wie beim Tesla-Badge
            // in der Car-Card, sonst nervt der Teaser Accounts ohne Auto-Verknuepfung.
            const setUpForThisCar = ctx.teslaTelemetryActive
                && (ctx.teslaConnectedCarId === null || ctx.teslaConnectedCarId === car.id)
            return setUpForThisCar ? null : 'TELEMETRY'
        }
        case 'SMARTCAR':
            if (ctx.isPremium) return null
            return ctx.purchasesAvailable ? 'AUTOSYNC' : null
        case 'NONE':
            return null
    }
}

/** Weggeklickte Teaser kommen nach dieser Frist wieder - dezent, aber nicht vergesslich. */
export const TEASER_DISMISS_MS = 30 * 24 * 60 * 60 * 1000

const dismissKey = (carId: string, kind: CarTeaserKind) => `carTeaser.${kind}.${carId}`

export function isTeaserDismissed(carId: string, kind: CarTeaserKind, now: number = Date.now()): boolean {
    const raw = localStorage.getItem(dismissKey(carId, kind))
    if (!raw) return false
    const dismissedAt = Number(raw)
    if (!Number.isFinite(dismissedAt)) return false
    return now - dismissedAt < TEASER_DISMISS_MS
}

export function dismissTeaser(carId: string, kind: CarTeaserKind, now: number = Date.now()): void {
    localStorage.setItem(dismissKey(carId, kind), String(now))
}

/**
 * Bindet die Regel an Auth-Store und Tesla-Pairing-Status.
 *
 * `teslaStatus` kommt vom Aufrufer (in /cars laedt `useCarForm` ihn ohnehin schon) und
 * liefert nur die Auto-Zuordnung. Ob Telemetry laeuft, verraet allein der Pairing-Status.
 * Der wird bewusst ohne Blick auf `teslaStatus` abgefragt: das Status-Polling startet
 * ohne await und ist beim ersten Laden noch leer.
 */
export function useCarSetupTeaser(cars: Ref<Car[]>, teslaStatus: Ref<TeslaConnectionStatus | null>) {
    const authStore = useAuthStore()
    const telemetryActive = ref(false)
    // Bis der Pairing-Status da ist, schweigt der Telemetry-Teaser, sonst blitzt er bei
    // Fahrern mit laufender Telemetry kurz auf.
    const telemetryChecked = ref(false)
    // localStorage ist nicht reaktiv - dieser Tick zieht die Teaser nach einem Dismiss neu durch.
    const dismissTick = ref(0)

    const loadTelemetryStatus = async () => {
        if (authStore.isDemoAccount) return
        if (!cars.value.some(c => autoSyncProviderFor(c) === 'TESLA')) return
        // Ohne verbundenen Account antwortet der Endpoint mit 404: dann eben nicht aktiv.
        const pairing = await teslaFleetService.getPairingStatus().catch(() => null)
        telemetryActive.value = pairing?.dataSource === 'TELEMETRY'
        telemetryChecked.value = true
    }

    const teaserFor = (car: Car): CarTeaserKind | null => {
        void dismissTick.value
        const kind = carTeaserKind(car, {
            isPremium: authStore.isPremium,
            isDemo: authStore.isDemoAccount,
            purchasesAvailable: purchasesAvailable(),
            teslaTelemetryActive: telemetryActive.value,
            teslaConnectedCarId: teslaStatus.value?.carId ?? null,
        })
        if (!kind) return null
        if (kind === 'TELEMETRY' && !telemetryChecked.value) return null
        return isTeaserDismissed(car.id, kind) ? null : kind
    }

    const dismiss = (car: Car, kind: CarTeaserKind) => {
        dismissTeaser(car.id, kind)
        dismissTick.value++
    }

    return { teaserFor, dismiss, loadTelemetryStatus }
}
