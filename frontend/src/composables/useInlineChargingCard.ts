import { computed, ref } from 'vue'
import api from '../api/axios'
import type { ChargingProvider } from './useChargingProviders'

/** Sentinel-Eintrag der EMP-Liste: der User tippt den Namen selbst. */
export const CUSTOM_PROVIDER = 'Anderer Anbieter'

/**
 * Ladekarte direkt im Log-Formular anlegen. Ein Link in die Settings wuerde den User aus
 * dem halb ausgefuellten Log werfen - deshalb fragt das Inline-Formular nur nach Name und
 * AC/DC-Preis. Grundgebuehr, Blockiergebuehr und Gueltigkeit bleiben den Settings vorbehalten.
 *
 * @param toEurPerKwh rechnet den eingetippten Preis in die Speichereinheit EUR/kWh um
 *                    (EUR-Laender tippen ct/kWh) - Umkehrung der Anzeige auf den Tarif-Chips.
 */
export function useInlineChargingCard(toEurPerKwh: (typedPrice: number) => number) {
  const isOpen = ref(false)
  const saving = ref(false)
  const failed = ref(false)

  const emptyDraft = () => ({
    providerName: '',
    customProviderName: '',
    acPrice: '' as string | number,
    dcPrice: '' as string | number,
  })
  const draft = ref(emptyDraft())

  const isCustom = computed(() => draft.value.providerName === CUSTOM_PROVIDER)

  const resolvedName = computed(() =>
    (isCustom.value ? draft.value.customProviderName : draft.value.providerName).trim())

  const canSave = computed(() => resolvedName.value.length > 0 && !saving.value)

  const open = () => { isOpen.value = true }

  const cancel = () => {
    isOpen.value = false
    failed.value = false
    draft.value = emptyDraft()
  }

  const toEurOrNull = (typed: string | number) =>
    typed === '' || typed == null ? null : toEurPerKwh(Number(typed))

  /** Legt die Karte an. Gibt sie zurueck, damit der Aufrufer sie sofort auswaehlen kann - null bei Fehler. */
  const save = async (): Promise<ChargingProvider | null> => {
    if (!canSave.value) return null
    saving.value = true
    failed.value = false
    try {
      const res = await api.post<ChargingProvider>('/users/me/charging-providers', {
        providerName: resolvedName.value,
        label: null,
        acPricePerKwh: toEurOrNull(draft.value.acPrice),
        dcPricePerKwh: toEurOrNull(draft.value.dcPrice),
        monthlyFeeEur: 0,
        sessionFeeEur: 0,
        activeFrom: new Date().toISOString().split('T')[0],
      })
      isOpen.value = false
      draft.value = emptyDraft()
      return res.data
    } catch {
      failed.value = true
      return null
    } finally {
      saving.value = false
    }
  }

  return { isOpen, saving, failed, draft, isCustom, resolvedName, canSave, open, cancel, save }
}
