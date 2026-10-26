<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrashIcon, PlusIcon, PencilIcon, HomeIcon } from '@heroicons/vue/24/outline'
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
  chargingProviders, editingProviderId, providerForm, isCustomProvider, homeConflict,
  KNOWN_EMPS,
  resetProviderForm, startEditProvider, onProviderNameChange,
  fetchChargingProviders, saveChargingProvider, deleteChargingProvider,
  formatPrice, formatDate,
} = useChargingProviders(loading, message)

fetchChargingProviders()
</script>

<template>
  <div>
    <!-- Meldung ohne Kante: sie ist kein Inhalt und nichts zum Antippen -->
    <p v-if="message" :class="[
      'mb-4 rounded-xl bg-gray-100 px-4 py-3 text-sm font-semibold dark:bg-white/5',
      message.type === 'success'
        ? 'text-gray-900 dark:text-gray-100'
        : 'text-amber-700 dark:text-amber-400'
    ]">{{ message.text }}</p>


    <div class="space-y-3">
      <!-- Bestehende Karten -->
      <template v-for="provider in chargingProviders" :key="provider.id">
        <!-- Card (normale Ansicht) -->
        <div v-if="editingProviderId !== provider.id"
          class="p-4 rounded-xl bg-white edge-card dark:bg-gray-700/50 flex flex-wrap sm:flex-nowrap items-start gap-3 sm:gap-4">
          <!-- Mobil: Kachel und Aktionen oben, Text in voller Breite darunter.
               Ab sm: Text links, Kachel und Aktionen rechts in einer Zeile. -->
          <div class="order-3 sm:order-1 basis-full sm:basis-auto min-w-0 sm:flex-1">
            <p class="font-semibold text-gray-900 dark:text-gray-100 truncate">
              {{ provider.providerName }}
              <span v-if="provider.isPrivate"
                class="ml-1.5 inline-flex items-center gap-1 align-middle rounded-full bg-gray-100 dark:bg-white/10 px-2 py-0.5 text-[11px] font-semibold text-gray-700 dark:text-gray-200">
                <HomeIcon class="w-3 h-3" aria-hidden="true" />{{ t('settings.tariff_private_badge') }}
              </span>
            </p>
            <p v-if="provider.label" class="text-sm text-gray-500 dark:text-gray-400 mt-0.5 truncate">{{ provider.label }}</p>
            <div class="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-sm tabular-nums text-gray-600 dark:text-gray-300">
              <span v-if="provider.acPricePerKwh != null">AC: {{ formatPrice(provider.acPricePerKwh) }}</span>
              <span v-if="provider.dcPricePerKwh != null">DC: {{ formatPrice(provider.dcPricePerKwh) }}</span>
              <span v-if="provider.monthlyFeeEur > 0">{{ formatCurrency(provider.monthlyFeeEur) }}/{{ t('settings.month_short') }}</span>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('settings.tariff_active_since') }} {{ formatDate(provider.activeFrom) }}</p>
          </div>
          <!-- Dieselbe Kachel wie im Log-Formular - der User erkennt seine Karte wieder.
               Hier groesser als im Formular, aber im selben Kartenformat (1.56:1). -->
          <ChargingCardTile
            class="order-1 sm:order-2 flex-shrink-0 w-28 h-[4.5rem] sm:w-36 sm:h-[5.75rem]"
            :id="provider.id"
            :title="provider.label || provider.providerName"
            :subtitle="provider.acPricePerKwh != null ? formatPrice(provider.acPricePerKwh) : null" />
          <div class="order-2 sm:order-3 ml-auto sm:ml-0 -mr-2 -mt-2 flex flex-shrink-0">
            <button @click="startEditProvider(provider)" :aria-label="t('settings.tariff_form_edit')"
              class="flex h-11 w-11 items-center justify-center rounded-xl text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-gray-100">
              <PencilIcon class="h-4 w-4" />
            </button>
            <button @click="deleteChargingProvider(provider.id)" :aria-label="t('settings.tariff_delete')"
              class="flex h-11 w-11 items-center justify-center rounded-xl text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-gray-100">
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
        </div>

        <!-- Inline-Edit Formular -->
        <div v-else class="p-4 rounded-xl bg-white edge-card dark:bg-gray-700/50 space-y-3">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.tariff_form_edit') }}</p>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_provider_label') }}</label>
            <select v-model="providerForm.providerName" @change="onProviderNameChange"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600">
              <option value="" disabled>{{ t('settings.tariff_provider_placeholder') }}</option>
              <option v-for="emp in KNOWN_EMPS" :key="emp" :value="emp">{{ emp }}</option>
            </select>
          </div>
          <div v-if="isCustomProvider">
            <input v-model="providerForm.customProviderName" type="text"
              :placeholder="t('settings.tariff_provider_custom_placeholder')" maxlength="100"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_label_label') }}</label>
            <input v-model="providerForm.label" type="text"
              :placeholder="t('settings.tariff_label_placeholder')" maxlength="100"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_ac_label') }}</label>
              <input v-model="providerForm.acPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.39"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_dc_label') }}</label>
              <input v-model="providerForm.dcPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.49"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_monthly_label') }}</label>
              <input v-model="providerForm.monthlyFeeEur" type="number" step="0.01" min="0" placeholder="0.00"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_session_label') }}</label>
              <input v-model="providerForm.sessionFeeEur" type="number" step="0.0001" min="0" placeholder="0.00"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_active_from_label') }}</label>
            <input v-model="providerForm.activeFrom" type="date" :max="new Date().toISOString().split('T')[0]"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
          <!-- Heimtarif: die einzige Markierung, die eine Ladung ohne Ortsnachweis bepreisen darf.
               Import-Quellen wie XPeng liefern keinen Ort - ohne sie bleibt jede Heimladung offen. -->
          <label class="flex items-start gap-2.5 cursor-pointer">
            <input v-model="providerForm.isPrivate" type="checkbox"
              class="mt-0.5 h-4 w-4 flex-shrink-0 rounded-sm border-gray-300 dark:border-gray-600 text-green-600 focus:ring-green-600" />
            <span class="text-xs">
              <span class="block font-medium text-gray-700 dark:text-gray-200">{{ t('settings.tariff_private_label') }}</span>
              <span class="block text-gray-500 dark:text-gray-400">{{ t('settings.tariff_private_hint') }}</span>
    <span v-if="homeConflict?.type === 'ends'" class="mt-1 block text-amber-700 dark:text-amber-400">
      {{ t('settings.tariff_private_ends_previous', { card: homeConflict.card.label || homeConflict.card.providerName, date: formatDate(homeConflict.endsOn) }) }}
    </span>
    <span v-else-if="homeConflict?.type === 'overlap'" class="mt-1 block font-semibold text-amber-700 dark:text-amber-400">
      {{ t('settings.tariff_private_overlap') }}
    </span>
            </span>
          </label>
          <div class="flex gap-2 pt-1">
            <button @click="saveChargingProvider"
              :disabled="loading || !providerForm.providerName || (isCustomProvider && !providerForm.customProviderName)"
              class="btn-3d flex-1 min-h-11 px-4 rounded-xl border-2 border-edge bg-green-600 text-sm font-semibold text-white hover:bg-green-700 disabled:opacity-50">
              {{ t('settings.tariff_save') }}
            </button>
            <button @click="editingProviderId = null; resetProviderForm()"
              class="btn-3d flex-1 min-h-11 px-4 rounded-xl border-2 border-edge bg-white text-sm font-semibold text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
              {{ t('settings.tariff_cancel') }}
            </button>
          </div>
        </div>
      </template>

      <!-- Empty State -->
      <div v-if="chargingProviders.length === 0 && editingProviderId !== 'new'"
        class="p-4 rounded-xl bg-white edge-card dark:bg-gray-700/50">
        <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.tariff_none_title') }}</p>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ t('settings.tariff_none_desc') }}</p>
      </div>

      <!-- Neue Karte Formular -->
      <div v-if="editingProviderId === 'new'"
        class="p-4 rounded-xl bg-white edge-card dark:bg-gray-700/50 space-y-3">
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.tariff_form_title_new') }}</p>
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_provider_label') }}</label>
          <select v-model="providerForm.providerName" @change="onProviderNameChange"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600">
            <option value="" disabled>{{ t('settings.tariff_provider_placeholder') }}</option>
            <option v-for="emp in KNOWN_EMPS" :key="emp" :value="emp">{{ emp }}</option>
          </select>
        </div>
        <div v-if="isCustomProvider">
          <input v-model="providerForm.customProviderName" type="text"
            :placeholder="t('settings.tariff_provider_custom_placeholder')" maxlength="100"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_label_label') }}</label>
          <input v-model="providerForm.label" type="text"
            :placeholder="t('settings.tariff_label_placeholder')" maxlength="100"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_ac_label') }}</label>
            <input v-model="providerForm.acPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.39"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_dc_label') }}</label>
            <input v-model="providerForm.dcPricePerKwh" type="number" step="0.0001" min="0" max="5" placeholder="0.49"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_monthly_label') }}</label>
            <input v-model="providerForm.monthlyFeeEur" type="number" step="0.01" min="0" placeholder="0.00"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_session_label') }}</label>
            <input v-model="providerForm.sessionFeeEur" type="number" step="0.0001" min="0" placeholder="0.00"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
          </div>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_active_from_label') }}</label>
          <input v-model="providerForm.activeFrom" type="date" :max="new Date().toISOString().split('T')[0]"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-xl text-sm focus:ring-green-600 focus:border-green-600" />
        </div>
        <!-- Heimtarif: die einzige Markierung, die eine Ladung ohne Ortsnachweis bepreisen darf.
             Import-Quellen wie XPeng liefern keinen Ort - ohne sie bleibt jede Heimladung offen. -->
        <label class="flex items-start gap-2.5 cursor-pointer">
          <input v-model="providerForm.isPrivate" type="checkbox"
            class="mt-0.5 h-4 w-4 flex-shrink-0 rounded-sm border-gray-300 dark:border-gray-600 text-green-600 focus:ring-green-600" />
          <span class="text-xs">
            <span class="block font-medium text-gray-700 dark:text-gray-200">{{ t('settings.tariff_private_label') }}</span>
            <span class="block text-gray-500 dark:text-gray-400">{{ t('settings.tariff_private_hint') }}</span>
    <span v-if="homeConflict?.type === 'ends'" class="mt-1 block text-amber-700 dark:text-amber-400">
      {{ t('settings.tariff_private_ends_previous', { card: homeConflict.card.label || homeConflict.card.providerName, date: formatDate(homeConflict.endsOn) }) }}
    </span>
    <span v-else-if="homeConflict?.type === 'overlap'" class="mt-1 block font-semibold text-amber-700 dark:text-amber-400">
      {{ t('settings.tariff_private_overlap') }}
    </span>
          </span>
        </label>
        <div class="flex gap-2 pt-1">
          <button @click="saveChargingProvider"
            :disabled="loading || !providerForm.providerName || (isCustomProvider && !providerForm.customProviderName)"
            class="btn-3d flex-1 min-h-11 px-4 rounded-xl border-2 border-edge bg-green-600 text-sm font-semibold text-white hover:bg-green-700 disabled:opacity-50">
            {{ t('settings.tariff_save') }}
          </button>
          <button @click="editingProviderId = null; resetProviderForm()"
            class="btn-3d flex-1 min-h-11 px-4 rounded-xl border-2 border-edge bg-white text-sm font-semibold text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
            {{ t('settings.tariff_cancel') }}
          </button>
        </div>
      </div>

      <!-- Neue Ladekarte Button -->
      <button v-if="editingProviderId === null"
        @click="editingProviderId = 'new'; resetProviderForm()"
        class="btn-3d inline-flex min-h-11 items-center gap-2 rounded-xl border-2 border-edge bg-white px-4 text-sm font-semibold text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15">
        <PlusIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('settings.tariff_add_btn') }}
      </button>
    </div>
  </div>
</template>
