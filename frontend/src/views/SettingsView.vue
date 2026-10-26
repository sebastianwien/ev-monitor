<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCountryStore } from '../stores/country'
import { useCarStore } from '../stores/car'
import { purchasesAvailable, donationsAvailable } from '../utils/iapPolicy'
import { hasOnlyUnsupportedBrands } from '../composables/useCarAutoSyncProvider'
import { COUNTRY_OPTIONS } from '../config/countries'
import { TrashIcon, ArrowDownTrayIcon, AcademicCapIcon, ClipboardDocumentIcon, CheckIcon, CheckCircleIcon, ExclamationCircleIcon, ExclamationTriangleIcon, ArrowRightOnRectangleIcon, CreditCardIcon, ChevronRightIcon, TrophyIcon, ClockIcon } from '@heroicons/vue/24/outline'
import SupportPopover from '../components/settings/SupportPopover.vue'
import DemoSettingsModal from '../components/demo/DemoSettingsModal.vue'
import ModelBox from '../components/models/ModelBox.vue'
import ChoiceChip from '../components/shared/ChoiceChip.vue'
import ToggleSwitch from '../components/shared/ToggleSwitch.vue'
import BottomSheet from '../components/shared/BottomSheet.vue'
import Collapse from '../components/shared/Collapse.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useStoryOptIn } from '../composables/useStoryOptIn'
import { useAccountSettings } from '../composables/useAccountSettings'
import dashboardPreferencesService from '../api/dashboardPreferencesService'

const { t, locale } = useI18n()
const { formatCurrency } = useLocaleFormat()
const countryStore = useCountryStore()

// Shared state
const loading = ref(false)
const message = ref<{ type: 'success' | 'error', text: string } | null>(null)

// -- Account Settings --
const {
  email, username, registeredSince, totalLogs, totalKwh, totalCostEur,
  coinBalance, referralCode, referralCopied, leaderboardVisible, tickerShareCharges,
  hasPassword, setPasswordLinkSent, sendSetPasswordLink,
  subscriptionPeriodEnd, subscriptionTier, portalLoading,
  tierActionLoading, tierActionError,
  showEmailForm, showUsernameForm, showPasswordForm,
  newEmail, emailCurrentPassword, newUsername,
  currentPassword, newPassword, confirmPassword,
  showDeleteConfirm, deletePassword, deleteCode, deleteCodeSent, sendDeleteCode,
  referralLink, copyReferralLink, openPortal,
  fetchUserData, changeEmail, changeUsername, changePassword,
  exportData, deleteAccount, toggleLeaderboardVisible, toggleTickerShareCharges, restartOnboarding,
  initSubscription, downgradeToAutoSync, cancelSubscription,
  authStore,
} = useAccountSettings(loading, message)

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

// Neues Dashboard: Widerruf des Moment-Heros, nur auf diesem Geraet (wie der Opt-in selbst).
const { enabled: storyEnabled, alwaysMonth, setAlwaysMonth } = useStoryOptIn()

// Ersparnis-Kachel: serverseitig gehalten, damit das Ausblenden auf allen Geraeten gilt.
// Der Toggle bildet die Sicht ab (an = sichtbar), die Persistenz das Gegenteil (dismissed).
const savingsCardVisible = ref(true)
onMounted(async () => {
  try {
    const prefs = await dashboardPreferencesService.get()
    savingsCardVisible.value = !prefs.savingsCardDismissed
  } catch {
    // Nicht angemeldet oder Ladefehler: Standard sichtbar, der Toggle bleibt bedienbar.
  }
})
async function toggleSavingsCard() {
  savingsCardVisible.value = !savingsCardVisible.value
  try {
    await dashboardPreferencesService.setSavingsCardDismissed(!savingsCardVisible.value)
  } catch {
    savingsCardVisible.value = !savingsCardVisible.value
  }
}

// Tesla-Garage detection - controls visibility of the Live upgrade CTA
// AND the Live-tier disconnected warning.
const carStore = useCarStore()
const userCarBrands = ref<string[]>([])
const hasTesla = computed(() => userCarBrands.value.some(b => b === 'TESLA'))
// Garage nur aus Marken ohne AutoSync-Weg (Zeekr, Leapmotor, ...): kein AutoSync-Upgrade-Hinweis.
const onlyUnsupportedBrands = computed(() => hasOnlyUnsupportedBrands(userCarBrands.value.map(brand => ({ brand }))))
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

// `close` faehrt das Sheet animiert zu; erst danach meldet es `close` und setzt die Aktion zurueck.
async function confirmTierAction(close: () => void) {
  const action = tierConfirmAction.value
  close()
  if (action === 'downgrade') {
    await downgradeToAutoSync()
  } else if (action === 'cancel') {
    await cancelSubscription()
  }
}

function resetDeleteConfirm() {
  showDeleteConfirm.value = false
  deletePassword.value = ''
  deleteCode.value = ''
  deleteCodeSent.value = false
}

// App-CI (docs/design/ci.md): Kante, harter Schatten, 44 px. Sekundaer weiss, primaer gruen, rot nur beim Loeschen.
const BTN = 'btn-3d cta-shadow-sm inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border-2 border-edge px-4 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 disabled:opacity-50'
const BTN_SECONDARY = `${BTN} bg-white text-gray-900 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15`
const BTN_PRIMARY = `${BTN} bg-green-600 text-white hover:bg-green-700`
const BTN_DANGER = `${BTN} bg-red-600 text-white hover:bg-red-700`
const INPUT = 'h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-base text-gray-900 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100'
const LABEL = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
const H2 = 'text-[18px] font-bold tracking-tight text-gray-900 md:text-[20px] dark:text-gray-100'
const ROW = 'flex items-center justify-between gap-4 border-t border-gray-200 py-3 first:border-t-0 first:pt-0 last:pb-0 dark:border-gray-700'

onMounted(async () => {
  fetchUserData()
  initSubscription()
  try {
    const cars = await carStore.getCars()
    userCarBrands.value = cars.map(c => c.brand)
  } catch { /* non-critical */ }
})
</script>

<template>
<div class="grid gap-2 pb-6 max-md:-mt-[var(--ticker-lasche-h)] md:mx-auto md:max-w-2xl md:gap-4 md:px-6 md:pb-6 md:pt-[var(--page-top,1.5rem)]">
  <!-- Konto -->
  <!-- Handy: erste Box läuft unter die Ticker-Lasche, Inhalt rückt um die Lasche nach unten -->
  <ModelBox class="max-md:pt-[calc(1.5rem+var(--ticker-lasche-h))]">
    <header class="flex items-center justify-between gap-3">
      <h1 class="text-[28px] font-bold leading-[1.1] text-gray-900 md:text-[36px] dark:text-gray-50">{{ t('settings.title') }}</h1>
      <button type="button" :class="BTN_SECONDARY" @click="authStore.logout()">
        <ArrowRightOnRectangleIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('settings.logout') }}
      </button>
    </header>

    <div v-if="message" role="status"
      class="flex items-start gap-2 rounded-xl bg-gray-100 px-3 py-2.5 text-sm font-semibold text-gray-900 dark:bg-gray-800/60 dark:text-gray-100">
      <CheckCircleIcon v-if="message.type === 'success'" class="mt-0.5 h-5 w-5 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
      <ExclamationCircleIcon v-else class="mt-0.5 h-5 w-5 shrink-0 text-gray-700 dark:text-gray-300" aria-hidden="true" />
      <span>{{ message.text }}</span>
    </div>

    <section class="grid gap-3">
      <h2 :class="H2">{{ t('settings.account') }}</h2>
      <div>
        <!-- E-Mail -->
        <div :class="ROW" class="flex-wrap">
          <div class="min-w-0">
            <p :class="LABEL">{{ t('settings.email_label') }}</p>
            <p class="truncate font-semibold text-gray-900 dark:text-gray-100">{{ email }}</p>
          </div>
          <button v-if="authStore.user?.authProvider === 'LOCAL'" type="button" :class="BTN_SECONDARY" :aria-expanded="showEmailForm" @click="showEmailForm = !showEmailForm">
            {{ t('settings.email_change') }}
          </button>
          <span v-else class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('settings.via_provider', { provider: authStore.user?.authProvider }) }}</span>
          <Collapse :open="showEmailForm" class="w-full">
          <div class="grid gap-3 pt-1">
            <input v-model="newEmail" type="email" :placeholder="t('settings.email_new_placeholder')" :aria-label="t('settings.email_new_placeholder')" :class="INPUT">
            <input v-model="emailCurrentPassword" type="password" :placeholder="t('settings.email_password_placeholder')" :aria-label="t('settings.email_password_placeholder')" :class="INPUT">
            <p class="text-[12.5px] text-gray-500 dark:text-gray-400">{{ t('settings.email_logout_hint') }}</p>
            <div class="flex gap-2">
              <button type="button" :class="BTN_PRIMARY" :disabled="loading || !newEmail || !emailCurrentPassword" @click="changeEmail">{{ t('settings.save') }}</button>
              <button type="button" :class="BTN_SECONDARY" @click="showEmailForm = false; newEmail = ''; emailCurrentPassword = ''">{{ t('settings.cancel') }}</button>
            </div>
          </div>
          </Collapse>
        </div>

        <!-- Benutzername -->
        <div :class="ROW" class="flex-wrap">
          <div class="min-w-0">
            <p :class="LABEL">{{ t('settings.username_label') }}</p>
            <p class="truncate font-semibold text-gray-900 dark:text-gray-100">{{ username }}</p>
          </div>
          <button type="button" :class="BTN_SECONDARY" :aria-expanded="showUsernameForm" @click="showUsernameForm = !showUsernameForm">
            {{ t('settings.email_change') }}
          </button>
          <Collapse :open="showUsernameForm" class="w-full">
          <div class="grid gap-3 pt-1">
            <input v-model="newUsername" type="text" :placeholder="t('settings.username_new_placeholder')" :aria-label="t('settings.username_new_placeholder')" :class="INPUT">
            <div class="flex gap-2">
              <button type="button" :class="BTN_PRIMARY" :disabled="loading" @click="changeUsername">{{ t('settings.save') }}</button>
              <button type="button" :class="BTN_SECONDARY" @click="showUsernameForm = false; newUsername = ''">{{ t('settings.cancel') }}</button>
            </div>
          </div>
          </Collapse>
        </div>

        <!-- Passwort: Konten aus dem Mail-Code-Gate haben noch keins -->
        <div v-if="!hasPassword" :class="ROW" class="flex-wrap">
          <div class="min-w-0">
            <p :class="LABEL">{{ t('settings.password_label') }}</p>
            <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.no_password') }}</p>
            <p class="mt-0.5 text-[13px] text-gray-600 dark:text-gray-400">{{ t('settings.no_password_hint') }}</p>
            <p v-if="setPasswordLinkSent" class="mt-1 text-[13px] font-semibold text-gray-900 dark:text-gray-100" role="status">{{ t('settings.set_password_sent') }}</p>
          </div>
          <button v-if="!setPasswordLinkSent" type="button" :class="BTN_SECONDARY" :disabled="loading" @click="sendSetPasswordLink">
            {{ t('settings.set_password_btn') }}
          </button>
        </div>
        <div v-else :class="ROW" class="flex-wrap">
          <div class="min-w-0">
            <p :class="LABEL">{{ t('settings.password_label') }}</p>
            <p class="font-semibold text-gray-900 dark:text-gray-100" aria-hidden="true">••••••••••</p>
          </div>
          <button type="button" :class="BTN_SECONDARY" :aria-expanded="showPasswordForm" @click="showPasswordForm = !showPasswordForm">
            {{ t('settings.email_change') }}
          </button>
          <Collapse :open="showPasswordForm" class="w-full">
          <div class="grid gap-3 pt-1">
            <input v-model="currentPassword" type="password" :placeholder="t('settings.password_current_placeholder')" :aria-label="t('settings.password_current_placeholder')" :class="INPUT">
            <input v-model="newPassword" type="password" :placeholder="t('settings.password_new_placeholder')" :aria-label="t('settings.password_new_placeholder')" :class="INPUT">
            <input v-model="confirmPassword" type="password" :placeholder="t('settings.password_confirm_placeholder')" :aria-label="t('settings.password_confirm_placeholder')" :class="INPUT">
            <div class="flex gap-2">
              <button type="button" :class="BTN_PRIMARY" :disabled="loading" @click="changePassword">{{ t('settings.save') }}</button>
              <button type="button" :class="BTN_SECONDARY" @click="showPasswordForm = false; currentPassword = ''; newPassword = ''; confirmPassword = ''">{{ t('settings.cancel') }}</button>
            </div>
          </div>
          </Collapse>
        </div>
      </div>
    </section>
  </ModelBox>

  <!-- Abo -->
  <ModelBox v-if="authStore.isPremium || authStore.isSupporter || (purchasesAvailable() && !onlyUnsupportedBrands)">
    <h2 :class="H2">{{ t('upgrade.subscription_title') }}</h2>
    <div v-if="authStore.isPremium || authStore.isSupporter" class="grid gap-3">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-3">
          <CheckCircleIcon class="h-6 w-6 shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
          <div class="min-w-0">
            <p class="font-semibold text-gray-900 dark:text-gray-100">
              {{ subscriptionTier === 'AUTOSYNC_LIVE' ? t('settings.tier_live') : subscriptionTier === 'SUPPORTER' ? t('settings.tier_supporter') : t('settings.tier_autosync') }}
            </p>
            <p v-if="subscriptionPeriodEnd" class="text-[13px] text-gray-500 tabular-nums dark:text-gray-400">
              {{ t('settings.tier_period_end', { date: formattedPeriodEnd }) }}
            </p>
          </div>
        </div>
        <button v-if="purchasesAvailable()" type="button" :class="BTN_SECONDARY" :disabled="portalLoading" @click="openPortal">
          {{ portalLoading ? t('upgrade.pro_manage_loading') : t('upgrade.pro_manage_btn') }}
        </button>
      </div>
      <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('settings.portal_hint') }}</p>

      <!-- Live ohne Tesla in der Garage: Amber, weil etwas fehlt, mit Handlung -->
      <router-link v-if="liveButNoTesla" to="/imports"
        class="flex items-center gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 transition-colors hover:bg-amber-100 dark:border-amber-800 dark:bg-amber-900/20 dark:hover:bg-amber-900/30">
        <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-amber-700 dark:text-amber-400" aria-hidden="true" />
        <span class="flex-1">
          <span class="block text-sm font-semibold text-amber-900 dark:text-amber-200">{{ t('settings.tier_live_disconnected_warning') }}</span>
          <span class="block text-[13px] text-amber-800 dark:text-amber-300">{{ t('settings.tier_live_repair_cta') }}</span>
        </span>
        <ChevronRightIcon class="h-4 w-4 shrink-0 text-amber-700 dark:text-amber-400" aria-hidden="true" />
      </router-link>

      <div v-if="subscriptionTier === 'AUTOSYNC_LIVE'" class="grid gap-2 pt-1">
        <button type="button" :class="BTN_SECONDARY" :disabled="tierActionLoading" @click="openDowngradeConfirm">
          {{ t('settings.downgrade_to_autosync_cta') }}
        </button>
        <button type="button" class="min-h-11 text-[13px] font-semibold text-gray-500 underline underline-offset-2 hover:text-gray-800 disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-200"
          :disabled="tierActionLoading" @click="openCancelConfirm">
          {{ t('settings.cancel_live_cta') }}
        </button>
      </div>
      <p v-if="tierActionError" class="text-center text-[13px] font-semibold text-gray-900 dark:text-gray-100" role="alert">{{ tierActionError }}</p>
    </div>
    <div v-else class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="min-w-0 sm:flex-1">
        <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.tier_autosync') }}</p>
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('upgrade.autosync_offer_benefit') }}</p>
        <p class="text-[13px] text-gray-500 tabular-nums dark:text-gray-400">{{ t('upgrade.autosync_offer_price', { priceMonthly: t('upgrade.price_monthly') }) }}</p>
      </div>
      <router-link to="/upgrade" :class="BTN_SECONDARY">{{ t('upgrade.autosync_offer_cta') }}</router-link>
    </div>
  </ModelBox>

  <!-- Land -->
  <ModelBox>
    <div class="grid gap-1">
      <h2 :class="H2">{{ t('settings.country_title') }}</h2>
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.country_desc') }}</p>
    </div>
    <div role="group" :aria-label="t('settings.country_title')" class="scrollbar-hide -mx-4 flex gap-2 overflow-x-auto px-4 pt-0.5 pb-1.5 md:mx-0 md:flex-wrap md:px-0">
      <ChoiceChip v-for="c in COUNTRY_OPTIONS" :key="c.code" size="lg" :active="countryStore.country === c.code" @click="countryStore.setCountry(c.code)">
        <span aria-hidden="true">{{ c.flag }}</span>{{ c.name[locale] || c.name.en }}
      </ChoiceChip>
    </div>
  </ModelBox>

  <!-- Anzeige -->
  <ModelBox>
    <h2 :class="H2">{{ t('settings.display_title') }}</h2>
    <div>
      <div :class="ROW">
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.implausible_banner_label') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('settings.implausible_banner_hint') }}</p>
        </div>
        <ToggleSwitch :model-value="implausibleBannerEnabled" :label="t('settings.implausible_banner_label')" @update:model-value="toggleImplausibleBanner()" />
      </div>
      <div :class="ROW">
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.savings_card_label') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('settings.savings_card_hint') }}</p>
        </div>
        <ToggleSwitch :model-value="savingsCardVisible" :label="t('settings.savings_card_label')" @update:model-value="toggleSavingsCard()" />
      </div>
      <div v-if="storyEnabled" :class="ROW">
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.story_always_month_label') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('settings.story_always_month_hint') }}</p>
        </div>
        <ToggleSwitch data-testid="settings-story-always-month" :model-value="alwaysMonth" :label="t('settings.story_always_month_label')" @update:model-value="setAlwaysMonth($event)" />
      </div>
    </div>
  </ModelBox>

  <!-- Meine Ladekarten: verwaltet unter /charging-providers, die ganze Box ist der Link -->
  <router-link to="/charging-providers"
    class="flex items-center gap-3 bg-white px-4 py-5 transition-colors hover:bg-gray-50 md:rounded-3xl md:px-8 md:edge-box dark:bg-gray-900 dark:hover:bg-gray-800">
    <CreditCardIcon class="h-6 w-6 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
    <span class="min-w-0 flex-1">
      <span class="block font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.tariff_title') }}</span>
      <span class="block text-sm text-gray-500 dark:text-gray-400">{{ t('settings.tariff_moved_hint') }}</span>
    </span>
    <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
  </router-link>

  <!-- Watt -->
  <ModelBox>
    <div class="flex items-baseline justify-between gap-3">
      <h2 :class="H2">{{ t('settings.watt_account') }}</h2>
      <p class="flex items-baseline gap-1 leading-none">
        <b class="text-[26px] font-extrabold tracking-tight text-gray-900 tabular-nums dark:text-gray-50">{{ coinBalance }}</b>
        <span class="text-[13px] font-semibold text-gray-500 dark:text-gray-400">{{ t('coins.unit') }}</span>
      </p>
    </div>
    <div class="grid gap-3 sm:grid-cols-2">
      <router-link to="/coins/history" :class="BTN_SECONDARY">
        <ClockIcon class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="truncate">{{ t('settings.watt_history') }}</span>
      </router-link>
      <router-link to="/leaderboard" :class="BTN_PRIMARY">
        <TrophyIcon class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="truncate">{{ t('coins.to_leaderboard') }}</span>
      </router-link>
    </div>
    <div>
      <div :class="ROW">
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.leaderboard_visible_label') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('settings.leaderboard_visible_hint') }}</p>
        </div>
        <ToggleSwitch :model-value="leaderboardVisible" :label="t('settings.leaderboard_visible_label')" @update:model-value="toggleLeaderboardVisible()" />
      </div>
      <div :class="ROW">
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.ticker_share_label') }}</p>
          <p class="mt-0.5 text-[13px] text-gray-500 dark:text-gray-400">{{ t('settings.ticker_share_hint') }}</p>
        </div>
        <ToggleSwitch :model-value="tickerShareCharges" :label="t('settings.ticker_share_label')" @update:model-value="toggleTickerShareCharges()" />
      </div>
    </div>
  </ModelBox>

  <!-- Einladen -->
  <ModelBox v-if="referralCode">
    <div class="grid gap-1">
      <h2 :class="H2">{{ t('settings.referral_title') }}</h2>
      <p class="text-sm text-gray-600 dark:text-gray-400" v-html="t('settings.referral_desc')" />
    </div>
    <div class="flex gap-2">
      <input :value="referralLink()" readonly :aria-label="t('settings.referral_title')" :class="INPUT" class="min-w-0 flex-1 cursor-default select-all text-sm">
      <button type="button" :class="BTN_SECONDARY" class="shrink-0" @click="copyReferralLink">
        <CheckIcon v-if="referralCopied" class="h-4 w-4" aria-hidden="true" />
        <ClipboardDocumentIcon v-else class="h-4 w-4" aria-hidden="true" />
        {{ referralCopied ? t('settings.copied') : t('settings.copy') }}
      </button>
    </div>
  </ModelBox>

  <!-- Hilfe -->
  <ModelBox>
    <div class="grid gap-1">
      <h2 :class="H2">{{ t('settings.help_title') }}</h2>
      <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.tutorial_title') }}</p>
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.tutorial_desc') }}</p>
    </div>
    <button type="button" :class="BTN_SECONDARY" class="w-full" @click="restartOnboarding">
      <AcademicCapIcon class="h-5 w-5" aria-hidden="true" />
      {{ t('settings.tutorial_btn') }}
    </button>
  </ModelBox>

  <!-- Unterstützen: Spenden-Aufruf inkl. Links nativ verboten (Apple-Reject, siehe iapPolicy) -->
  <ModelBox v-if="donationsAvailable()">
    <div class="grid gap-1">
      <h2 :class="H2">{{ t('settings.support_title') }}</h2>
      <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.support_desc') }}</p>
    </div>
    <SupportPopover variant="block" />
  </ModelBox>

  <!-- Daten und Datenschutz -->
  <ModelBox>
    <h2 :class="H2">{{ t('settings.privacy_title') }}</h2>
    <div class="grid gap-1">
      <p :class="LABEL">{{ t('settings.registered_since') }}</p>
      <p class="font-semibold text-gray-900 tabular-nums dark:text-gray-100">{{ registeredSince }}</p>
      <p class="text-sm text-gray-600 tabular-nums dark:text-gray-400">
        <b class="text-gray-900 dark:text-gray-100">{{ totalLogs }}</b> {{ t('settings.stats_charges') }} ·
        <b class="text-gray-900 dark:text-gray-100">{{ Math.round(totalKwh) }}</b> kWh ·
        <b class="text-gray-900 dark:text-gray-100">{{ formatCurrency(totalCostEur ?? 0) }}</b>
      </p>
    </div>
    <div class="grid gap-3">
      <button type="button" :class="BTN_SECONDARY" class="w-full" :disabled="loading" @click="exportData">
        <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />
        {{ t('settings.export_btn') }}
      </button>
      <button type="button" :class="BTN_SECONDARY" class="w-full" @click="showDeleteConfirm = true">
        <TrashIcon class="h-5 w-5" aria-hidden="true" />
        {{ t('settings.delete_btn') }}
      </button>
    </div>
  </ModelBox>

  <!-- Konto löschen: einziger rote Knopf der Seite -->
  <BottomSheet v-if="showDeleteConfirm" :label="t('settings.delete_modal_title')" edge @close="resetDeleteConfirm">
    <template #default="{ close }">
      <div class="grid gap-4 overflow-y-auto px-4 pt-3 pb-4 sm:px-6 sm:pt-6">
        <div class="mx-auto h-1 w-10 rounded-full bg-gray-300 sm:hidden dark:bg-gray-600" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ t('settings.delete_modal_title') }}</h3>
        <p class="text-sm text-gray-700 dark:text-gray-300" v-html="t('settings.delete_modal_desc')" />

        <input v-if="hasPassword" v-model="deletePassword" type="password" :placeholder="t('settings.delete_password_placeholder')" :aria-label="t('settings.delete_password_placeholder')" :class="INPUT">
        <template v-else>
          <button v-if="!deleteCodeSent" type="button" data-testid="delete-send-code" :class="BTN_SECONDARY" class="w-full" :disabled="loading" @click="sendDeleteCode">
            {{ t('settings.delete_code_send') }}
          </button>
          <template v-else>
            <p class="text-sm text-gray-600 dark:text-gray-400" aria-live="polite">{{ t('settings.delete_code_sent') }}</p>
            <input v-model="deleteCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
              :aria-label="t('settings.delete_code_placeholder')" :placeholder="t('settings.delete_code_placeholder')" :class="INPUT" class="tabular-nums">
          </template>
        </template>

        <div class="grid grid-cols-2 gap-3">
          <button type="button" :class="BTN_SECONDARY" @click="close">{{ t('settings.cancel') }}</button>
          <button type="button" :class="BTN_DANGER" :disabled="loading || (hasPassword ? !deletePassword : deleteCode.trim().length !== 6)" @click="deleteAccount">
            {{ t('settings.delete_confirm_btn') }}
          </button>
        </div>
      </div>
    </template>
  </BottomSheet>

  <!-- Abo downgraden oder kündigen -->
  <BottomSheet v-if="tierConfirmAction" :label="tierConfirmTitle" edge @close="closeTierConfirm">
    <template #default="{ close }">
      <div class="grid gap-4 overflow-y-auto px-4 pt-3 pb-4 sm:px-6 sm:pt-6">
        <div class="mx-auto h-1 w-10 rounded-full bg-gray-300 sm:hidden dark:bg-gray-600" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ tierConfirmTitle }}</h3>
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ tierConfirmBody }}</p>
        <div class="grid grid-cols-2 gap-3">
          <button type="button" :class="BTN_SECONDARY" @click="close">{{ t('settings.cancel_confirm_keep') }}</button>
          <button type="button" :class="BTN_SECONDARY" :disabled="tierActionLoading" @click="confirmTierAction(close)">{{ t('settings.cancel_confirm_proceed') }}</button>
        </div>
      </div>
    </template>
  </BottomSheet>

  <DemoSettingsModal />
</div>
</template>
