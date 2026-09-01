<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrashIcon, PlusIcon, PencilIcon } from '@heroicons/vue/24/outline'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { useChargingProviders } from '../../composables/useChargingProviders'
import ChargingCardTile from '../shared/ChargingCardTile.vue'

/**
 * Verwaltung der Ladekarten (EMPs) des Users: anlegen, bearbeiten, aus dem Portfolio nehmen.
 * Eigenstaendig - haelt seinen eigenen loading/message-State, damit die Komponente ueberall
 * eingehaengt werden kann.
 */
const { t } = useI18n()
const { formatCurrency } = useLocaleFormat()

const loading = ref(false)
const message = ref<{ type: 'success' | 'error', text: string } | null>(null)

const {
  chargingProviders, editingProviderId, providerForm, isCustomProvider,
  KNOWN_EMPS,
  resetProviderForm, startEditProvider,
  fetchChargingProviders, saveChargingProvider, deleteChargingProvider,
  formatPrice, formatDate,
} = useChargingProviders(loading, message)

fetchChargingProviders()
</script>

<template>
  <div>
    <p v-if="message" :class="[
      'mb-4 p-3 rounded-sm text-sm',
      message.type === 'success'
        ? 'bg-green-50 text-green-800 dark:bg-green-900/30 dark:text-green-300'
        : 'bg-red-50 text-red-800 dark:bg-red-900/30 dark:text-red-300'
    ]">{{ message.text }}</p>


    <div class="space-y-3">
      <!-- Bestehende Karten -->
      <template v-for="provider in chargingProviders" :key="provider.id">
        <!-- Card (normale Ansicht) -->
        <div v-if="editingProviderId !== provider.id"
          class="p-4 bg-gray-50 dark:bg-gray-700 rounded-sm flex items-start gap-4">
          <div class="min-w-0 flex-1">
            <p class="font-semibold text-gray-800 dark:text-gray-100 truncate">{{ provider.providerName }}</p>
            <p v-if="provider.label" class="text-sm text-gray-500 dark:text-gray-400 mt-0.5 truncate">{{ provider.label }}</p>
            <div class="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-sm text-gray-600 dark:text-gray-300">
              <span v-if="provider.acPricePerKwh != null">AC: {{ formatPrice(provider.acPricePerKwh) }}</span>
              <span v-if="provider.dcPricePerKwh != null">DC: {{ formatPrice(provider.dcPricePerKwh) }}</span>
              <span v-if="provider.monthlyFeeEur > 0">{{ formatCurrency(provider.monthlyFeeEur) }}/{{ t('settings.month_short') }}</span>
            </div>
            <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">{{ t('settings.tariff_active_since') }} {{ formatDate(provider.activeFrom) }}</p>
          </div>
          <!-- Dieselbe Kachel wie im Log-Formular - der User erkennt seine Karte wieder.
               Hier groesser als im Formular, aber im selben Kartenformat (1.56:1). -->
          <ChargingCardTile
            class="flex-shrink-0 w-36 h-[5.75rem]"
            :id="provider.id"
            :title="provider.label || provider.providerName"
            :subtitle="provider.acPricePerKwh != null ? formatPrice(provider.acPricePerKwh) : null" />
          <div class="flex gap-1 flex-shrink-0 mt-0.5">
            <button @click="startEditProvider(provider)"
              class="p-1.5 text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition rounded">
              <PencilIcon class="h-4 w-4" />
            </button>
            <button @click="deleteChargingProvider(provider.id)"
              class="p-1.5 text-gray-400 hover:text-red-500 transition rounded">
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
        </div>

        <!-- Inline-Edit Formular -->
        <div v-else class="p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-sm space-y-3">
          <p class="text-sm font-medium text-indigo-800 dark:text-indigo-300">{{ t('settings.tariff_form_edit') }}</p>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_provider_label') }}</label>
            <select v-model="providerForm.providerName"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500">
              <option value="" disabled>{{ t('settings.tariff_provider_placeholder') }}</option>
              <option v-for="emp in KNOWN_EMPS" :key="emp" :value="emp">{{ emp }}</option>
            </select>
          </div>
          <div v-if="isCustomProvider">
            <input v-model="providerForm.customProviderName" type="text"
              :placeholder="t('settings.tariff_provider_custom_placeholder')" maxlength="100"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_label_label') }}</label>
            <input v-model="providerForm.label" type="text"
              :placeholder="t('settings.tariff_label_placeholder')" maxlength="100"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_ac_label') }}</label>
              <input v-model="providerForm.acPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.39"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_dc_label') }}</label>
              <input v-model="providerForm.dcPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.49"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_monthly_label') }}</label>
              <input v-model="providerForm.monthlyFeeEur" type="number" step="0.01" min="0" placeholder="0.00"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_session_label') }}</label>
              <input v-model="providerForm.sessionFeeEur" type="number" step="0.0001" min="0" placeholder="0.00"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_active_from_label') }}</label>
            <input v-model="providerForm.activeFrom" type="date" :max="new Date().toISOString().split('T')[0]"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <!-- Heimstrom: Basis der Heimlade-Ersparnis. Bewusst vom Nutzer gesetzt - aus den
               Logs ist es nicht ableitbar, weil oeffentliche Karten dort wie Heimladungen
               aussehen koennen. -->
          <label class="flex items-start gap-2.5 cursor-pointer">
            <input v-model="providerForm.isHome" type="checkbox"
              class="mt-0.5 h-4 w-4 flex-none rounded-sm border-gray-300 dark:border-gray-600 text-green-600 focus:ring-green-500" />
            <span class="min-w-0">
              <span class="block text-xs font-medium text-gray-700 dark:text-gray-300">{{ t('settings.tariff_is_home') }}</span>
              <span class="block text-xs text-gray-500 dark:text-gray-400">{{ t('settings.tariff_is_home_hint') }}</span>
            </span>
          </label>
          <div class="flex gap-2 pt-1">
            <button @click="saveChargingProvider"
              :disabled="loading || !providerForm.providerName || (isCustomProvider && !providerForm.customProviderName)"
              class="btn-3d flex-1 px-4 py-2 bg-green-600 text-white rounded-sm hover:bg-green-700 disabled:opacity-50 transition text-sm">
              {{ t('settings.tariff_save') }}
            </button>
            <button @click="editingProviderId = null; resetProviderForm()"
              class="btn-3d flex-1 px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-sm hover:bg-gray-400 transition text-sm">
              {{ t('settings.tariff_cancel') }}
            </button>
          </div>
        </div>
      </template>

      <!-- Empty State -->
      <div v-if="chargingProviders.length === 0 && editingProviderId !== 'new'"
        class="p-4 bg-gray-50 dark:bg-gray-700 rounded-sm">
        <p class="font-medium text-gray-700 dark:text-gray-300">{{ t('settings.tariff_none_title') }}</p>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ t('settings.tariff_none_desc') }}</p>
      </div>

      <!-- Neue Karte Formular -->
      <div v-if="editingProviderId === 'new'"
        class="p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-sm space-y-3">
        <p class="text-sm font-medium text-indigo-800 dark:text-indigo-300">{{ t('settings.tariff_form_title_new') }}</p>
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_provider_label') }}</label>
          <select v-model="providerForm.providerName"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500">
            <option value="" disabled>{{ t('settings.tariff_provider_placeholder') }}</option>
            <option v-for="emp in KNOWN_EMPS" :key="emp" :value="emp">{{ emp }}</option>
          </select>
        </div>
        <div v-if="isCustomProvider">
          <input v-model="providerForm.customProviderName" type="text"
            :placeholder="t('settings.tariff_provider_custom_placeholder')" maxlength="100"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_label_label') }}</label>
          <input v-model="providerForm.label" type="text"
            :placeholder="t('settings.tariff_label_placeholder')" maxlength="100"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_ac_label') }}</label>
            <input v-model="providerForm.acPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.39"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_dc_label') }}</label>
            <input v-model="providerForm.dcPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.49"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_monthly_label') }}</label>
            <input v-model="providerForm.monthlyFeeEur" type="number" step="0.01" min="0" placeholder="0.00"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_session_label') }}</label>
            <input v-model="providerForm.sessionFeeEur" type="number" step="0.0001" min="0" placeholder="0.00"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_active_from_label') }}</label>
          <input v-model="providerForm.activeFrom" type="date" :max="new Date().toISOString().split('T')[0]"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm text-sm focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <label class="flex items-start gap-2.5 cursor-pointer">
          <input v-model="providerForm.isHome" type="checkbox"
            class="mt-0.5 h-4 w-4 flex-none rounded-sm border-gray-300 dark:border-gray-600 text-green-600 focus:ring-green-500" />
          <span class="min-w-0">
            <span class="block text-xs font-medium text-gray-700 dark:text-gray-300">{{ t('settings.tariff_is_home') }}</span>
            <span class="block text-xs text-gray-500 dark:text-gray-400">{{ t('settings.tariff_is_home_hint') }}</span>
          </span>
        </label>
        <div class="flex gap-2 pt-1">
          <button @click="saveChargingProvider"
            :disabled="loading || !providerForm.providerName || (isCustomProvider && !providerForm.customProviderName)"
            class="btn-3d flex-1 px-4 py-2 bg-green-600 text-white rounded-sm hover:bg-green-700 disabled:opacity-50 transition text-sm">
            {{ t('settings.tariff_save') }}
          </button>
          <button @click="editingProviderId = null; resetProviderForm()"
            class="btn-3d flex-1 px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-sm hover:bg-gray-400 transition text-sm">
            {{ t('settings.tariff_cancel') }}
          </button>
        </div>
      </div>

      <!-- Neue Ladekarte Button -->
      <button v-if="editingProviderId === null"
        @click="editingProviderId = 'new'; resetProviderForm()"
        class="w-full flex items-center justify-center gap-2 py-3 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-sm text-gray-500 dark:text-gray-400 hover:border-indigo-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition text-sm">
        <PlusIcon class="h-4 w-4" />
        {{ t('settings.tariff_add_btn') }}
      </button>
    </div>
  </div>
</template>
