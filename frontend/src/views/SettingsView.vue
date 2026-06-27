<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCountryStore } from '../stores/country'
import { useCarStore } from '../stores/car'
import { purchasesAvailable } from '../utils/iapPolicy'
import { COUNTRY_OPTIONS } from '../config/countries'
import { UserIcon, KeyIcon, TrashIcon, ArrowDownTrayIcon, AcademicCapIcon, ShareIcon, ClipboardDocumentIcon, CheckIcon, HeartIcon, ArrowRightOnRectangleIcon, BoltIcon, CreditCardIcon, PlusIcon, PencilIcon, EyeIcon } from '@heroicons/vue/24/outline'
import SupportPopover from '../components/settings/SupportPopover.vue'
import DemoSettingsModal from '../components/demo/DemoSettingsModal.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useAccountSettings } from '../composables/useAccountSettings'
import { useChargingProviders } from '../composables/useChargingProviders'

const { t, locale } = useI18n()
const { formatCurrency } = useLocaleFormat()
const countryStore = useCountryStore()

// Shared state
const loading = ref(false)
const message = ref<{ type: 'success' | 'error', text: string } | null>(null)

// -- Account Settings --
const {
  email, username, registeredSince, totalLogs, totalKwh, totalCostEur,
  coinBalance, referralCode, referralCopied, leaderboardVisible,
  subscriptionPeriodEnd, subscriptionTier, portalLoading,
  tierActionLoading, tierActionError,
  showEmailForm, showUsernameForm, showPasswordForm,
  newEmail, emailCurrentPassword, newUsername,
  currentPassword, newPassword, confirmPassword,
  showDeleteConfirm, deletePassword,
  referralLink, copyReferralLink, openPortal,
  fetchUserData, changeEmail, changeUsername, changePassword,
  exportData, deleteAccount, toggleLeaderboardVisible, restartOnboarding,
  initSubscription, downgradeToAutoSync, cancelSubscription,
  authStore,
} = useAccountSettings(loading, message)

// -- Charging Providers --
const {
  chargingProviders, editingProviderId, providerForm, isCustomProvider,
  KNOWN_EMPS,
  resetProviderForm, startEditProvider,
  fetchChargingProviders, saveChargingProvider, deleteChargingProvider,
  formatPrice, formatDate,
} = useChargingProviders(loading, message)

// -- Display Preferences --
const LS_IMPLAUSIBLE_BANNER_DISMISSED = 'implausible_banner_dismissed'
const implausibleBannerEnabled = ref(localStorage.getItem(LS_IMPLAUSIBLE_BANNER_DISMISSED) !== 'true')
function toggleImplausibleBanner() {
  implausibleBannerEnabled.value = !implausibleBannerEnabled.value
  if (implausibleBannerEnabled.value) {
    localStorage.removeItem(LS_IMPLAUSIBLE_BANNER_DISMISSED)
  } else {
    localStorage.setItem(LS_IMPLAUSIBLE_BANNER_DISMISSED, 'true')
  }
}

// Tesla-Garage detection - controls visibility of the Live upgrade CTA
// AND the Live-tier disconnected warning.
const carStore = useCarStore()
const userCarBrands = ref<string[]>([])
const hasTesla = computed(() => userCarBrands.value.some(b => b === 'TESLA'))
// Live tier without a Tesla in the garage = stuck state. User pays for a
// feature with nothing to stream from. Surface a repair-CTA toward /imports.
const liveButNoTesla = computed(() => subscriptionTier.value === 'AUTOSYNC_LIVE' && !hasTesla.value)

const formattedPeriodEnd = computed(() => subscriptionPeriodEnd.value
  ? new Date(subscriptionPeriodEnd.value).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric' })
  : '-')

const tierConfirmAction = ref<'downgrade' | 'cancel' | null>(null)
const tierConfirmTitle = computed(() => tierConfirmAction.value === 'downgrade'
  ? t('settings.downgrade_confirm_title')
  : t('settings.cancel_confirm_title'))
const tierConfirmBody = computed(() => tierConfirmAction.value === 'downgrade'
  ? t('settings.downgrade_confirm_body', { date: formattedPeriodEnd.value })
  : t('settings.cancel_confirm_body', { date: formattedPeriodEnd.value }))

function openDowngradeConfirm() { tierConfirmAction.value = 'downgrade' }
function openCancelConfirm() { tierConfirmAction.value = 'cancel' }
function closeTierConfirm() { tierConfirmAction.value = null }

async function confirmTierAction() {
  const action = tierConfirmAction.value
  closeTierConfirm()
  if (action === 'downgrade') {
    await downgradeToAutoSync()
  } else if (action === 'cancel') {
    await cancelSubscription()
  }
}

onMounted(async () => {
  fetchUserData()
  fetchChargingProviders()
  initSubscription()
  try {
    const cars = await carStore.getCars()
    userCarBrands.value = cars.map(c => c.brand)
  } catch { /* non-critical */ }
})
</script>

<template>
<div>
  <div class="md:max-w-4xl md:mx-auto md:p-6">
    <div class="bg-white dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)] p-4 md:p-6">
      <!-- Header -->
      <div class="flex items-center gap-3 mb-6">
        <UserIcon class="h-8 w-8 text-gray-700 dark:text-gray-300" />
        <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-200">{{ t('settings.title') }}</h1>
      </div>

      <!-- Message Banner -->
      <div v-if="message" :class="[
        'mb-6 p-4 rounded-sm',
        message.type === 'success' ? 'bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 text-green-700 dark:text-green-300' : 'bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300'
      ]">
        {{ message.text }}
      </div>

      <!-- Account Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <UserIcon class="h-6 w-6" />
          {{ t('settings.account') }}
          <button
            @click="authStore.logout()"
            class="ml-auto flex items-center gap-2 px-3 py-1.5 rounded-sm border border-red-300 dark:border-red-700 text-red-600 dark:text-red-400 text-sm font-medium hover:bg-red-50 dark:hover:bg-red-900/30 transition shadow-[0_4px_0_0_#fca5a5] dark:shadow-[0_4px_0_0_#7f1d1d] active:shadow-none active:translate-y-1 cursor-pointer" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
            <ArrowRightOnRectangleIcon class="h-4 w-4" />
            Logout
          </button>
        </h2>

        <!-- Email -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-sm">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.email_label') }}</p>
              <p class="font-medium">{{ email }}</p>
            </div>
            <button
              v-if="authStore.user?.authProvider === 'LOCAL'"
              @click="showEmailForm = !showEmailForm"
              class="btn-3d px-3 py-1 text-sm bg-indigo-600 text-white rounded-sm hover:bg-indigo-700 transition">
              {{ t('settings.email_change') }}
            </button>
            <span v-else class="text-xs text-gray-400">{{ t('settings.via_provider', { provider: authStore.user?.authProvider }) }}</span>
          </div>

          <div v-if="showEmailForm" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600 space-y-3">
            <input
              v-model="newEmail"
              type="email"
              :placeholder="t('settings.email_new_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="emailCurrentPassword"
              type="password"
              :placeholder="t('settings.email_password_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-indigo-500 focus:border-indigo-500">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('settings.email_logout_hint') }}</p>
            <div class="flex gap-2">
              <button
                @click="changeEmail"
                :disabled="loading || !newEmail || !emailCurrentPassword"
                class="btn-3d px-4 py-2 bg-green-600 text-white rounded-sm hover:bg-green-700 disabled:opacity-50 transition">
                {{ t('settings.save') }}
              </button>
              <button
                @click="showEmailForm = false; newEmail = ''; emailCurrentPassword = ''"
                class="btn-3d px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-sm hover:bg-gray-400 dark:hover:bg-gray-500 transition">
                {{ t('settings.cancel') }}
              </button>
            </div>
          </div>
        </div>

        <!-- Username -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-sm">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.username_label') }}</p>
              <p class="font-medium">{{ username }}</p>
            </div>
            <button
              @click="showUsernameForm = !showUsernameForm"
              class="btn-3d px-3 py-1 text-sm bg-indigo-600 text-white rounded-sm hover:bg-indigo-700 transition">
              {{ t('settings.email_change') }}
            </button>
          </div>

          <div v-if="showUsernameForm" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600">
            <input
              v-model="newUsername"
              type="text"
              :placeholder="t('settings.username_new_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-indigo-500 focus:border-indigo-500 mb-3">
            <div class="flex gap-2">
              <button
                @click="changeUsername"
                :disabled="loading"
                class="btn-3d px-4 py-2 bg-green-600 text-white rounded-sm hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showUsernameForm = false; newUsername = ''"
                class="btn-3d px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-sm hover:bg-gray-400 dark:hover:bg-gray-500 transition">
                Abbrechen
              </button>
            </div>
          </div>
        </div>

        <!-- Password -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-sm">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.password_label') }}</p>
              <p class="font-medium">••••••••••</p>
            </div>
            <button
              @click="showPasswordForm = !showPasswordForm"
              class="btn-3d px-3 py-1 text-sm bg-indigo-600 text-white rounded-sm hover:bg-indigo-700 transition">
              {{ t('settings.email_change') }}
            </button>
          </div>

          <div v-if="showPasswordForm" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600 space-y-3">
            <input
              v-model="currentPassword"
              type="password"
              :placeholder="t('settings.password_current_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="newPassword"
              type="password"
              :placeholder="t('settings.password_new_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="confirmPassword"
              type="password"
              :placeholder="t('settings.password_confirm_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-indigo-500 focus:border-indigo-500">
            <div class="flex gap-2">
              <button
                @click="changePassword"
                :disabled="loading"
                class="btn-3d px-4 py-2 bg-green-600 text-white rounded-sm hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showPasswordForm = false; currentPassword = ''; newPassword = ''; confirmPassword = ''"
                class="btn-3d px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-sm hover:bg-gray-400 dark:hover:bg-gray-500 transition">
                Abbrechen
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pro Subscription -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          {{ t('upgrade.pro_section_title') }}
          <span class="text-xs font-bold bg-indigo-600 text-white px-2 py-0.5 rounded-full">PRO</span>
        </h2>
        <div v-if="authStore.isPremium" class="space-y-3">
          <div class="flex items-center justify-between p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-800 rounded-sm">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 bg-indigo-100 dark:bg-indigo-900/40 rounded-full flex items-center justify-center shrink-0">
                <svg class="w-4 h-4 text-indigo-600 dark:text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
              </div>
              <div>
                <span class="text-sm font-medium text-indigo-800 dark:text-indigo-200">
                  {{ subscriptionTier === 'AUTOSYNC_LIVE' ? t('settings.tier_live') : t('settings.tier_autosync') }}
                </span>
                <p v-if="subscriptionPeriodEnd" class="text-xs text-indigo-600/70 dark:text-indigo-400/70 mt-0.5">
                  {{ t('settings.tier_period_end', { date: formattedPeriodEnd }) }}
                </p>
              </div>
            </div>
            <button
              v-if="purchasesAvailable()"
              @click="openPortal"
              :disabled="portalLoading"
              class="text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-200 disabled:opacity-50 transition-colors"
            >
              {{ portalLoading ? t('upgrade.pro_manage_loading') : t('upgrade.pro_manage_btn') }}
            </button>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 px-1 -mt-1">{{ t('settings.portal_hint') }}</p>

          <!-- Live tier but no Tesla in garage -> repair-CTA -->
          <router-link
            v-if="liveButNoTesla"
            to="/imports"
            class="block bg-amber-50 dark:bg-amber-900/20 border border-amber-300 dark:border-amber-800 rounded-sm p-3 hover:bg-amber-100 dark:hover:bg-amber-900/30 transition-colors"
          >
            <div class="flex items-center gap-2">
              <svg class="w-5 h-5 shrink-0 text-amber-600 dark:text-amber-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
              </svg>
              <div class="flex-1">
                <p class="text-sm font-medium text-amber-800 dark:text-amber-200">{{ t('settings.tier_live_disconnected_warning') }}</p>
                <p class="text-xs text-amber-700 dark:text-amber-300">{{ t('settings.tier_live_repair_cta') }} →</p>
              </div>
            </div>
          </router-link>

          <!-- AUTOSYNC tier with Tesla in garage -> upgrade CTA -->
          <router-link
            v-if="subscriptionTier === 'AUTOSYNC' && hasTesla && purchasesAvailable()"
            to="/upgrade"
            class="block text-center bg-gradient-to-br from-indigo-600 to-purple-700 text-white text-sm font-semibold py-3 rounded-sm shadow-[3px_3px_0_rgba(0,0,0,0.25)] dark:shadow-[3px_3px_0_rgba(255,255,255,0.25)] hover:opacity-90 transition-opacity"
          >
            {{ t('settings.upgrade_to_live_cta') }}
          </router-link>

          <!-- AUTOSYNC_LIVE tier -> downgrade + cancel actions -->
          <div v-if="subscriptionTier === 'AUTOSYNC_LIVE'" class="flex flex-col gap-2 pt-1">
            <button
              @click="openDowngradeConfirm"
              :disabled="tierActionLoading"
              class="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 border border-gray-300 dark:border-gray-600 rounded-sm py-2 disabled:opacity-50"
            >
              {{ t('settings.downgrade_to_autosync_cta') }}
            </button>
            <button
              @click="openCancelConfirm"
              :disabled="tierActionLoading"
              class="text-xs text-gray-400 dark:text-gray-500 hover:text-red-600 dark:hover:text-red-400 underline disabled:opacity-50"
            >
              {{ t('settings.cancel_live_cta') }}
            </button>
          </div>
          <p v-if="tierActionError" class="text-xs text-red-600 dark:text-red-400 text-center">{{ tierActionError }}</p>
        </div>
        <div v-else-if="purchasesAvailable()" class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700/50 border border-gray-200 dark:border-gray-600 rounded-sm">
          <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('upgrade.pro_upgrade_hint', { priceMonthly: t('upgrade.price_monthly') }) }}</p>
          <router-link to="/upgrade" class="shrink-0 ml-4 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-200 transition-colors whitespace-nowrap">
            {{ t('upgrade.pro_upgrade_btn') }}
          </router-link>
        </div>
      </div>

      <!-- Country / Region -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-1">{{ t('settings.country_title') }}</h2>
        <p class="text-sm text-gray-500 dark:text-gray-400 mb-4">{{ t('settings.country_desc') }}</p>
        <div class="flex gap-2 overflow-x-auto pb-2 -mx-1 px-1 scrollbar-hide">
          <button
            v-for="c in COUNTRY_OPTIONS"
            :key="c.code"
            @click="countryStore.setCountry(c.code)"
            class="flex flex-col items-center gap-1 min-w-[72px] px-3 py-2.5 rounded-sm border-2 transition-all shrink-0 cursor-pointer"
            :class="countryStore.country === c.code
              ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
              : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
            <span class="text-2xl">{{ c.flag }}</span>
            <span class="text-xs font-medium" :class="countryStore.country === c.code ? 'text-green-700 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'">{{ c.name[locale] || c.name.en }}</span>
          </button>
        </div>
      </div>

      <!-- Anzeige Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <EyeIcon class="h-6 w-6" />
          {{ t('settings.display_title') }}
        </h2>
        <div class="p-4 bg-gray-50 dark:bg-gray-700 rounded-sm">
          <div class="flex items-center justify-between gap-4">
            <div>
              <p class="font-medium text-gray-800 dark:text-gray-200 text-sm">{{ t('settings.implausible_banner_label') }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('settings.implausible_banner_hint') }}</p>
            </div>
            <button
              @click="toggleImplausibleBanner"
              :class="[
                'relative flex-shrink-0 w-11 h-6 rounded-full transition-colors duration-200 focus:outline-none',
                implausibleBannerEnabled ? 'bg-green-500' : 'bg-gray-300'
              ]">
              <span
                :class="[
                  'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200',
                  implausibleBannerEnabled ? 'translate-x-5' : 'translate-x-0'
                ]" />
            </button>
          </div>
        </div>
      </div>

      <!-- Meine Ladekarten Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <CreditCardIcon class="h-6 w-6" />
          {{ t('settings.tariff_title') }}
        </h2>

        <div class="space-y-3">
          <!-- Bestehende Karten -->
          <template v-for="provider in chargingProviders" :key="provider.id">
            <!-- Card (normale Ansicht) -->
            <div v-if="editingProviderId !== provider.id"
              class="p-4 bg-gray-50 dark:bg-gray-700 rounded-sm flex items-start justify-between gap-4">
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

      <!-- Watt Balance -->
      <div class="mb-8 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm p-6">
        <div class="flex items-center gap-3 mb-3">
          <BoltIcon class="h-6 w-6 text-gray-500 dark:text-gray-400" />
          <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">{{ t('settings.watt_account') }}</h2>
          <span class="ml-auto text-3xl font-bold text-amber-600">{{ coinBalance }}</span>
        </div>
        <div class="flex items-center justify-between mb-4">
          <router-link to="/coins/history" class="btn-3d px-3 py-1.5 rounded-sm bg-amber-100 hover:bg-amber-200 text-amber-800 dark:bg-amber-900/30 dark:hover:bg-amber-900/50 dark:text-amber-300 text-xs font-semibold border border-amber-300 dark:border-amber-700 transition">
            {{ t('settings.watt_history') }}
          </router-link>
          <router-link to="/leaderboard" class="btn-3d px-3 py-1.5 rounded-sm bg-amber-100 hover:bg-amber-200 text-amber-800 dark:bg-amber-900/30 dark:hover:bg-amber-900/50 dark:text-amber-300 text-xs font-semibold border border-amber-300 dark:border-amber-700 transition">
            {{ t('coins.to_leaderboard') }}
          </router-link>
        </div>
        <div class="pt-4 border-t border-gray-100 dark:border-gray-700 flex items-center justify-between gap-4">
          <div>
            <p class="font-medium text-gray-800 dark:text-gray-200 text-sm">{{ t('settings.leaderboard_visible_label') }}</p>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('settings.leaderboard_visible_hint') }}</p>
          </div>
          <button
            @click="toggleLeaderboardVisible"
            :class="[
              'relative flex-shrink-0 w-11 h-6 rounded-full transition-colors duration-200 focus:outline-none',
              leaderboardVisible ? 'bg-green-500' : 'bg-gray-300'
            ]"
            :title="leaderboardVisible ? t('settings.leaderboard_disable') : t('settings.leaderboard_enable')">
            <span
              :class="[
                'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200',
                leaderboardVisible ? 'translate-x-5' : 'translate-x-0'
              ]" />
          </button>
        </div>
      </div>

      <!-- Referral Section -->
      <div v-if="referralCode" class="mb-8 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm p-6">
        <div class="flex items-center gap-3 mb-2">
          <ShareIcon class="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
          <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">{{ t('settings.referral_title') }}</h2>
        </div>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-4" v-html="t('settings.referral_desc')" />
        <div class="flex gap-2">
          <input
            :value="referralLink()"
            readonly
            class="flex-1 min-w-0 px-3 py-2 text-sm bg-white dark:bg-gray-700 border border-indigo-200 dark:border-indigo-700 rounded-sm text-gray-700 dark:text-gray-300 focus:outline-none cursor-default select-all" />
          <button
            @click="copyReferralLink"
            class="btn-3d flex-shrink-0 flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-sm transition"
            :class="referralCopied
              ? 'bg-green-600 text-white'
              : 'bg-indigo-600 text-white hover:bg-indigo-700'">
            <CheckIcon v-if="referralCopied" class="h-4 w-4" />
            <ClipboardDocumentIcon v-else class="h-4 w-4" />
            {{ referralCopied ? t('settings.copied') : t('settings.copy') }}
          </button>
        </div>
      </div>

      <!-- Help & Support Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <AcademicCapIcon class="h-6 w-6" />
          {{ t('settings.help_title') }}
        </h2>
        <div class="p-4 bg-gradient-to-r from-indigo-50 to-purple-50 dark:from-indigo-900/30 dark:to-purple-900/30 border border-indigo-200 dark:border-indigo-700 rounded-sm">
          <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-2 flex items-center gap-2">
            <span class="text-xl">👋</span>
            {{ t('settings.tutorial_title') }}
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
            {{ t('settings.tutorial_desc') }}
          </p>
          <button
            @click="restartOnboarding"
            class="btn-3d w-full flex items-center justify-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-sm hover:bg-indigo-700 transition">
            <AcademicCapIcon class="h-5 w-5" />
            <span>{{ t('settings.tutorial_btn') }}</span>
          </button>
        </div>
      </div>

      <!-- Support Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <HeartIcon class="h-6 w-6 text-red-500" />
          {{ t('settings.support_title') }}
        </h2>
        <div class="p-4 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm">
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
            {{ t('settings.support_desc') }}
          </p>
          <SupportPopover variant="block" />
        </div>
      </div>

      <!-- Data & Privacy Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <KeyIcon class="h-6 w-6" />
          {{ t('settings.privacy_title') }}
        </h2>

        <!-- Stats -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-sm">
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-2">{{ t('settings.registered_since') }}</p>
          <p class="font-medium mb-3">{{ registeredSince }}</p>

          <p class="text-sm text-gray-600 dark:text-gray-400">
            <strong>{{ totalLogs }}</strong> Ladevorgänge ·
            <strong>{{ Math.round(totalKwh) }}</strong> kWh ·
            <strong>{{ formatCurrency(totalCostEur ?? 0) }}</strong>
          </p>
        </div>

        <!-- Export Data -->
        <button
          @click="exportData"
          :disabled="loading"
          class="btn-3d w-full mb-4 flex items-center justify-center gap-2 px-4 py-3 bg-indigo-600 text-white rounded-sm hover:bg-indigo-700 disabled:opacity-50 transition">
          <ArrowDownTrayIcon class="h-5 w-5" />
          <span>{{ t('settings.export_btn') }}</span>
        </button>

        <!-- Delete Account -->
        <button
          @click="showDeleteConfirm = true"
          class="btn-3d w-full flex items-center justify-center gap-2 px-4 py-3 bg-red-600 text-white rounded-sm hover:bg-red-700 transition">
          <TrashIcon class="h-5 w-5" />
          <span>{{ t('settings.delete_btn') }}</span>
        </button>
      </div>

      <!-- Delete Confirmation Modal -->
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showDeleteConfirm = false">
        <div class="bg-white dark:bg-gray-800 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] max-w-md w-full p-6">
          <h3 class="text-2xl font-bold text-red-600 mb-4">{{ t('settings.delete_modal_title') }}</h3>
          <p class="text-gray-700 dark:text-gray-300 mb-4" v-html="t('settings.delete_modal_desc')" />

          <input
            v-model="deletePassword"
            type="password"
            :placeholder="t('settings.delete_password_placeholder')"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-sm focus:ring-red-500 focus:border-red-500 mb-4">

          <div class="flex gap-3">
            <button
              @click="deleteAccount"
              :disabled="loading || !deletePassword"
              class="btn-3d flex-1 px-4 py-2 bg-red-600 text-white rounded-sm hover:bg-red-700 disabled:opacity-50 transition">
              {{ t('settings.delete_confirm_btn') }}
            </button>
            <button
              @click="showDeleteConfirm = false; deletePassword = ''"
              class="btn-3d flex-1 px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-sm hover:bg-gray-400 dark:hover:bg-gray-500 transition">
              {{ t('settings.cancel') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
  <DemoSettingsModal />

  <!-- Tier-action confirmation dialog (downgrade or cancel) -->
  <Teleport to="body">
    <div
      v-if="tierConfirmAction"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
      @click.self="closeTierConfirm"
    >
      <div class="bg-white dark:bg-gray-800 rounded-sm shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] max-w-sm w-full p-6 border border-gray-200 dark:border-gray-700">
        <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100 mb-3">{{ tierConfirmTitle }}</h3>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">{{ tierConfirmBody }}</p>
        <div class="flex gap-2 justify-end">
          <button
            @click="closeTierConfirm"
            class="text-sm font-medium text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 px-4 py-2"
          >
            {{ t('settings.cancel_confirm_keep') }}
          </button>
          <button
            @click="confirmTierAction"
            :disabled="tierActionLoading"
            class="text-sm font-medium bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded-sm disabled:opacity-50"
          >
            {{ t('settings.cancel_confirm_proceed') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</div>
</template>
