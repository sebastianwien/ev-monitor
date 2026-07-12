import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import teslaFleetService, { type TeslaPairingStatus } from '../api/teslaFleetService'
import { shouldAutoEnableTelemetry } from '../utils/teslaTelemetryAutoEnable'

/**
 * Pairing-Lebenszyklus der Tesla Fleet-Telemetry: Status laden, Telemetry aktivieren
 * und deaktivieren. Geteilt zwischen der Tesla-Sektion in /imports und dem
 * Einrichtungs-Modal nach dem Anlegen eines Teslas in /cars.
 *
 * `isConnected` liefert, ob der Tesla-Account per OAuth verbunden ist - ohne
 * Verbindung antwortet der Pairing-Endpoint mit 404, wir fragen ihn dann gar nicht.
 */
export function useTeslaPairing(isConnected: () => boolean) {
    const { t } = useI18n()

    const pairingStatus = ref<TeslaPairingStatus | null>(null)
    const pairingStatusLoaded = ref(false)
    const pairingLoading = ref(false)
    const pairingError = ref<string | null>(null)

    const isTelemetryActive = computed(() => pairingStatus.value?.dataSource === 'TELEMETRY')
    const isFullProfile = computed(() => pairingStatus.value?.telemetryProfile === 'FULL')

    const loadPairingStatus = async () => {
        if (!isConnected()) {
            pairingStatusLoaded.value = true
            return
        }
        pairingError.value = null
        try {
            pairingStatus.value = await teslaFleetService.getPairingStatus()
        } catch (e: any) {
            pairingError.value = e.response?.status === 404
                ? t('tesla.pairing_err_not_connected')
                : e.response?.data?.message || t('tesla.pairing_err_status')
        } finally {
            pairingStatusLoaded.value = true
        }
        // Sobald der Virtual Key gepaart ist, Telemetry ohne Extra-Klick aktivieren - sonst
        // muesste der User den Button erst suchen. Idempotent: uebersprungen, wenn die Config
        // schon gepusht oder Telemetry aktiv ist, damit hier keine Schleife entsteht.
        if (shouldAutoEnableTelemetry(pairingStatus.value, pairingError.value)) {
            await enableTelemetry()
        }
    }

    const enableTelemetry = async () => {
        pairingLoading.value = true
        pairingError.value = null
        try {
            await teslaFleetService.enableTelemetry()
            await loadPairingStatus()
        } catch (e: any) {
            pairingError.value = e.response?.data?.message || t('tesla.pairing_err_enable')
        } finally {
            pairingLoading.value = false
        }
    }

    const disableTelemetry = async () => {
        pairingLoading.value = true
        pairingError.value = null
        try {
            await teslaFleetService.disableTelemetry()
            await loadPairingStatus()
        } catch (e: any) {
            pairingError.value = e.response?.data?.message || t('tesla.pairing_err_disable')
        } finally {
            pairingLoading.value = false
        }
    }

    return {
        pairingStatus,
        pairingStatusLoaded,
        pairingLoading,
        pairingError,
        isTelemetryActive,
        isFullProfile,
        loadPairingStatus,
        enableTelemetry,
        disableTelemetry,
    }
}
