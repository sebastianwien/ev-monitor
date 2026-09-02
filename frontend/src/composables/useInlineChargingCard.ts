import { computed, ref } from 'vue'
import api from '../api/axios'
import { KNOWN_EMPS, type ChargingProvider } from './useChargingProviders'

/** Sentinel-Eintrag der EMP-Liste: der User tippt den Namen selbst. */
export const CUSTOM_PROVIDER = 'Anderer Anbieter'

/**
 * Ladekarte direkt im Log-Formular anlegen. Ein Link in die Settings wuerde den User aus
 * dem halb ausgefuellten Log werfen - deshalb fragt das Inline-Formular nur nach Name und
 * AC/DC-Preis. Grundgebuehr, Blockiergebuehr und Gueltigkeit bleiben den Settings vorbehalten.
 *
 * @param toEurPerKwh rechnet den eingetippten Preis in die Speichereinheit EUR/kWh um
 *                    (EUR-Laender tippen ct/kWh) - Umkehrung der Anzeige auf den Tarif-Chips.
 * @param fromEurPerKwh Umkehrung von toEurPerKwh - fuellt beim Nachtragen (openEdit) die
 *                      gespeicherten EUR/kWh in die getippte Anzeigeeinheit zurueck.
 */
export function useInlineChargingCard(
  toEurPerKwh: (typedPrice: number) => number,
  fromEurPerKwh: (eurPrice: number) => number = (v) => v,
) {
  const isOpen = ref(false)
  const saving = ref(false)
  const failed = ref(false)
  // Gesetzt, sobald wir eine bestehende Karte bearbeiten - dann PUT statt POST und die
  // Basis-Felder (Grundgebuehr, Gueltigkeit, Heimstrom) der Karte bleiben erhalten.
  const editingBase = ref<ChargingProvider | null>(null)

  const emptyDraft = () => ({
    providerName: '',
    customProviderName: '',
    acPrice: '' as string | number,
    dcPrice: '' as string | number,
  })
  const draft = ref(emptyDraft())

  const isCustom = computed(() => draft.value.providerName === CUSTOM_PROVIDER)
  const isEditing = computed(() => editingBase.value !== null)

  const resolvedName = computed(() =>
    (isCustom.value ? draft.value.customProviderName : draft.value.providerName).trim())

  const hasPrice = computed(() => draft.value.acPrice !== '' || draft.value.dcPrice !== '')

  // Beim Nachtragen ist der Name fix - der einzige Sinn ist der Preis, also muss einer da sein.
  const canSave = computed(() =>
    resolvedName.value.length > 0 && !saving.value && (!isEditing.value || hasPrice.value))

  const open = () => { isOpen.value = true }

  /** Oeffnet das Formular zum Nachtragen des Preises einer bereits angelegten Karte. */
  const openEdit = (provider: ChargingProvider) => {
    editingBase.value = provider
    const isKnown = KNOWN_EMPS.includes(provider.providerName)
    draft.value = {
      providerName: isKnown ? provider.providerName : CUSTOM_PROVIDER,
      customProviderName: isKnown ? '' : provider.providerName,
      acPrice: provider.acPricePerKwh == null ? '' : fromEurPerKwh(provider.acPricePerKwh),
      dcPrice: provider.dcPricePerKwh == null ? '' : fromEurPerKwh(provider.dcPricePerKwh),
    }
    failed.value = false
    isOpen.value = true
  }

  const cancel = () => {
    isOpen.value = false
    failed.value = false
    editingBase.value = null
    draft.value = emptyDraft()
  }

  const toEurOrNull = (typed: string | number) =>
    typed === '' || typed == null ? null : toEurPerKwh(Number(typed))

  /**
   * Speichert die Karte (POST neu / PUT beim Nachtragen) und gibt sie zurueck, damit der
   * Aufrufer sie sofort auswaehlen bzw. neu bepreisen kann - null bei Fehler.
   */
  const save = async (): Promise<ChargingProvider | null> => {
    if (!canSave.value) return null
    saving.value = true
    failed.value = false
    const base = editingBase.value
    const payload = {
      providerName: resolvedName.value,
      label: base?.label ?? null,
      acPricePerKwh: toEurOrNull(draft.value.acPrice),
      dcPricePerKwh: toEurOrNull(draft.value.dcPrice),
      monthlyFeeEur: base?.monthlyFeeEur ?? 0,
      sessionFeeEur: base?.sessionFeeEur ?? 0,
      activeFrom: base?.activeFrom ?? new Date().toISOString().split('T')[0],
    }
    try {
      const res = base
        ? await api.put<ChargingProvider>(`/users/me/charging-providers/${base.id}`, payload)
        : await api.post<ChargingProvider>('/users/me/charging-providers', payload)
      isOpen.value = false
      editingBase.value = null
      draft.value = emptyDraft()
      return res.data
    } catch {
      failed.value = true
      return null
    } finally {
      saving.value = false
    }
  }

  return { isOpen, saving, failed, draft, isCustom, isEditing, resolvedName, canSave, open, openEdit, cancel, save }
}
