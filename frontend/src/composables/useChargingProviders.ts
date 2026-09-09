import { ref, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import api from '../api/axios'
import { useLocaleFormat } from './useLocaleFormat'
import { homeTariffConflict } from '../utils/homeTariff'

export interface ChargingProvider {
  id: string
  providerName: string
  label: string | null
  acPricePerKwh: number | null
  dcPricePerKwh: number | null
  monthlyFeeEur: number
  sessionFeeEur: number
  activeFrom: string
  activeUntil: string | null
  /** Privater Heimtarif (eigene Wallbox) statt Karte fuer oeffentliche Saeulen. */
  isPrivate: boolean
}

/** Der Heimtarif steht bewusst an erster Stelle - er ist fuer Import-Nutzer der haeufigste Fall. */
export const HOME_TARIFF_NAME = 'Zuhause (eigene Wallbox)'

export const KNOWN_EMPS = [
  HOME_TARIFF_NAME,
  'ADAC e-Charge', 'Aral Pulse', 'bp pulse', 'Charge Now (BMW)',
  'EnBW mobility+', 'Elli (VW)', 'E.ON Drive', 'EWE Go',
  'Fastned Gold', 'IONITY Passport', 'Lichtblick', 'Maingau Energie',
  'Mercedes me Charge', 'NewMotion', 'Plugsurfing', 'Shell Recharge',
  'Stadtwerke', 'Tesla',
  'Ella (AT)', 'SMATRICS EnBW (AT)',
  'Move (CH)',
  'Anderer Anbieter',
]

export function useChargingProviders(
  loading: Ref<boolean>,
  message: Ref<{ type: 'success' | 'error'; text: string } | null>,
) {
  const { t, locale } = useI18n()
  const { formatCostPerKwh } = useLocaleFormat()

  const chargingProviders = ref<ChargingProvider[]>([])
  const editingProviderId = ref<string | null>(null)
  const providerForm = ref({
    providerName: '',
    customProviderName: '',
    label: '',
    acPricePerKwh: '' as string | number,
    dcPricePerKwh: '' as string | number,
    monthlyFeeEur: 0,
    sessionFeeEur: 0,
    activeFrom: new Date().toISOString().split('T')[0],
    isPrivate: false,
  })

  const isCustomProvider = computed(() => providerForm.value.providerName === 'Anderer Anbieter')

  /**
   * Was das Speichern mit einem bestehenden Heimtarif macht - der Server beendet den bisherigen
   * automatisch bzw. lehnt eine Ueberschneidung ab. Das gehoert vor den Klick, nicht danach.
   */
  const homeConflict = computed(() => homeTariffConflict(
    chargingProviders.value, providerForm.value,
    editingProviderId.value === 'new' ? null : editingProviderId.value))

  /**
   * "Zuhause" im Anbieter-Feld setzt die Heimtarif-Markierung vor - die Checkbox bleibt aber
   * die einzige Wahrheit, damit auch ein Stadtwerke-Tarif als Heimtarif markiert werden kann.
   */
  const onProviderNameChange = () => {
    if (providerForm.value.providerName === HOME_TARIFF_NAME) providerForm.value.isPrivate = true
  }

  const resetProviderForm = () => {
    providerForm.value = {
      providerName: '', customProviderName: '', label: '',
      acPricePerKwh: '', dcPricePerKwh: '',
      monthlyFeeEur: 0, sessionFeeEur: 0,
      activeFrom: new Date().toISOString().split('T')[0],
      isPrivate: false,
      }
  }

  const startEditProvider = (provider: ChargingProvider) => {
    editingProviderId.value = provider.id
    const isKnown = KNOWN_EMPS.includes(provider.providerName)
    providerForm.value = {
      providerName: isKnown ? provider.providerName : 'Anderer Anbieter',
      customProviderName: isKnown ? '' : provider.providerName,
      label: provider.label || '',
      acPricePerKwh: provider.acPricePerKwh ?? '',
      dcPricePerKwh: provider.dcPricePerKwh ?? '',
      monthlyFeeEur: provider.monthlyFeeEur,
      sessionFeeEur: provider.sessionFeeEur,
      activeFrom: provider.activeFrom,
      isPrivate: provider.isPrivate,
    }
  }

  const fetchChargingProviders = async () => {
    try {
      const res = await api.get('/users/me/charging-providers')
      chargingProviders.value = res.data
    } catch { /* not critical */ }
  }

  const saveChargingProvider = async () => {
    const name = isCustomProvider.value
      ? providerForm.value.customProviderName.trim()
      : providerForm.value.providerName
    if (!name || !providerForm.value.activeFrom) return
    loading.value = true
    message.value = null
    try {
      const payload = {
        providerName: name,
        label: providerForm.value.label.trim() || null,
        acPricePerKwh: providerForm.value.acPricePerKwh !== '' ? providerForm.value.acPricePerKwh : null,
        dcPricePerKwh: providerForm.value.dcPricePerKwh !== '' ? providerForm.value.dcPricePerKwh : null,
        monthlyFeeEur: providerForm.value.monthlyFeeEur || 0,
        sessionFeeEur: providerForm.value.sessionFeeEur || 0,
        activeFrom: providerForm.value.activeFrom,
        isPrivate: providerForm.value.isPrivate,
      }
      if (editingProviderId.value === 'new') {
        await api.post('/users/me/charging-providers', payload)
      } else {
        await api.put(`/users/me/charging-providers/${editingProviderId.value}`, payload)
      }
      await fetchChargingProviders()
      editingProviderId.value = null
      resetProviderForm()
      message.value = { type: 'success', text: t('settings.tariff_ok') }
    } catch (err: any) {
      // Der Ueberschneidungs-Fall hat eine eigene, uebersetzte Erklaerung - die englische
      // Server-Meldung wuerde den User hier alleine lassen.
      const text = err.response?.data?.code === 'HOME_TARIFF_OVERLAP'
        ? t('settings.tariff_private_overlap')
        : err.response?.data?.message || t('settings.tariff_err_save')
      message.value = { type: 'error', text }
    } finally {
      loading.value = false
    }
  }

  const deleteChargingProvider = async (id: string) => {
    try {
      await api.delete(`/users/me/charging-providers/${id}`)
      await fetchChargingProviders()
    } catch {
      message.value = { type: 'error', text: t('settings.tariff_err_delete') }
    }
  }

  const formatPrice = (val: number | null) => val != null ? formatCostPerKwh(val) : '-'

  const formatDate = (dateStr: string) =>
    new Date(dateStr).toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })

  return {
    chargingProviders, editingProviderId, providerForm, isCustomProvider, homeConflict,
    KNOWN_EMPS, HOME_TARIFF_NAME,
    resetProviderForm, startEditProvider, onProviderNameChange,
    fetchChargingProviders, saveChargingProvider, deleteChargingProvider,
    formatPrice, formatDate,
  }
}
