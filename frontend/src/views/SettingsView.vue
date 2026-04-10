<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'
import type { CountryCode } from '../config/unitSystems'
import { UserIcon, KeyIcon, TrashIcon, ArrowDownTrayIcon, AcademicCapIcon, ShareIcon, ClipboardDocumentIcon, CheckIcon, HeartIcon, ArrowRightOnRectangleIcon, BoltIcon, CreditCardIcon, PlusIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import SupportPopover from '../components/SupportPopover.vue'
import DemoSettingsModal from '../components/DemoSettingsModal.vue'
import api from '../api/axios'
import { subscriptionService } from '../api/subscriptionService'
import { useLocaleFormat } from '../composables/useLocaleFormat'

const { t, locale } = useI18n()
const { formatCostPerKwh, formatCurrency } = useLocaleFormat()
const router = useRouter()
const authStore = useAuthStore()
const countryStore = useCountryStore()

const countries: { code: CountryCode; flag: string; name: Record<string, string> }[] = [
  { code: 'DE', flag: '🇩🇪', name: { de: 'Deutschland', en: 'Germany' } },
  { code: 'AT', flag: '🇦🇹', name: { de: 'Österreich', en: 'Austria' } },
  { code: 'CH', flag: '🇨🇭', name: { de: 'Schweiz', en: 'Switzerland' } },
  { code: 'GB', flag: '🇬🇧', name: { de: 'Großbritannien', en: 'United Kingdom' } },
  { code: 'NL', flag: '🇳🇱', name: { de: 'Niederlande', en: 'Netherlands' } },
  { code: 'BE', flag: '🇧🇪', name: { de: 'Belgien', en: 'Belgium' } },
  { code: 'DK', flag: '🇩🇰', name: { de: 'Dänemark', en: 'Denmark' } },
  { code: 'NO', flag: '🇳🇴', name: { de: 'Norwegen', en: 'Norway' } },
  { code: 'SE', flag: '🇸🇪', name: { de: 'Schweden', en: 'Sweden' } },
]

// Account Data
const email = ref('')
const username = ref('')
const registeredSince = ref('')
const totalLogs = ref(0)
const totalKwh = ref(0)
const totalCostEur = ref(0)

// Coin Balance
const coinBalance = ref(0)

// Referral
const referralCode = ref('')
const referralCopied = ref(false)

// Community
const leaderboardVisible = ref(true)

const subscriptionPeriodEnd = ref<string | null>(null)

const portalLoading = ref(false)
const openPortal = async () => {
  portalLoading.value = true
  try {
    const { portalUrl } = await subscriptionService.createPortalSession()
    window.location.href = portalUrl
  } catch {
    // ignore - portal link not available (no subscription yet)
  } finally {
    portalLoading.value = false
  }
}

const referralLink = () => `${window.location.origin}/register?ref=${referralCode.value}`

const copyReferralLink = async () => {
  try {
    await navigator.clipboard.writeText(referralLink())
    referralCopied.value = true
    setTimeout(() => { referralCopied.value = false }, 2000)
  } catch {
    // fallback: select the input text
  }
}

// Forms
const showEmailForm = ref(false)
const showUsernameForm = ref(false)
const showPasswordForm = ref(false)

const newEmail = ref('')
const emailCurrentPassword = ref('')
const newUsername = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

// Delete Account
const showDeleteConfirm = ref(false)
const deletePassword = ref('')

// Loading & Messages
const loading = ref(false)
const message = ref<{ type: 'success' | 'error', text: string } | null>(null)

// Fetch user data
const fetchUserData = async () => {
  try {
    // Get user from JWT token (already decoded in authStore)
    const user = authStore.user
    if (user) {
      email.value = user.email || user.sub || ''
      username.value = user.username || user.email?.split('@')[0] || ''
    }

    // Fetch stats and coins from API
    const [statsRes, coinsRes] = await Promise.all([
      api.get('/users/me/stats'),
      api.get('/coins/balance')
    ])

    const stats = statsRes.data
    registeredSince.value = new Date(stats.registeredSince).toLocaleDateString()
    totalLogs.value = stats.totalLogs
    totalKwh.value = stats.totalKwh
    totalCostEur.value = stats.totalCostEur ?? 0
    referralCode.value = stats.referralCode || ''
    leaderboardVisible.value = stats.leaderboardVisible ?? true

    coinBalance.value = coinsRes.data.totalCoins || 0
  } catch (error: any) {
    console.error('Failed to fetch user data:', error)
  }
}

// Change Email
const changeEmail = async () => {
  if (!newEmail.value || !emailCurrentPassword.value) return

  loading.value = true
  message.value = null

  try {
    await api.put('/users/me/email', { newEmail: newEmail.value, currentPassword: emailCurrentPassword.value })
    // JWT is now invalid (email changed) — logout and redirect to login
    authStore.logout()
    router.push('/login?reason=email-changed')
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || t('settings.err_email_change') }
    loading.value = false
  }
}

// Change Username
const changeUsername = async () => {
  if (!newUsername.value) return

  loading.value = true
  message.value = null

  try {
    const response = await api.put('/users/me/username', { newUsername: newUsername.value })
    authStore.setToken(response.data.token)
    username.value = authStore.user?.username || newUsername.value
    message.value = { type: 'success', text: t('settings.ok_username') }
    showUsernameForm.value = false
    newUsername.value = ''
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || t('settings.err_username_change') }
  } finally {
    loading.value = false
  }
}

// Change Password
const changePassword = async () => {
  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    message.value = { type: 'error', text: t('settings.err_fill_all') }
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    message.value = { type: 'error', text: t('settings.err_passwords_mismatch') }
    return
  }

  if (newPassword.value.length < 8) {
    message.value = { type: 'error', text: t('settings.err_password_short') }
    return
  }

  loading.value = true
  message.value = null

  try {
    await api.put('/users/me/password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value
    })
    message.value = { type: 'success', text: t('settings.ok_password') }
    showPasswordForm.value = false
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || t('settings.err_password_change') }
  } finally {
    loading.value = false
  }
}

// Export Data
const exportData = async () => {
  loading.value = true
  message.value = null

  try {
    const response = await api.get('/users/me/export', { responseType: 'blob' })

    // Create download link
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `ev-monitor-export-${Date.now()}.json`)
    document.body.appendChild(link)
    link.click()
    link.remove()

    message.value = { type: 'success', text: t('settings.ok_export') }
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || t('settings.err_export') }
  } finally {
    loading.value = false
  }
}

// Delete Account
const deleteAccount = async () => {
  if (!deletePassword.value) {
    message.value = { type: 'error', text: t('settings.err_password_enter') }
    return
  }

  loading.value = true
  message.value = null

  try {
    await api.delete('/users/me', {
      data: { password: deletePassword.value }
    })

    // Logout and redirect
    authStore.logout()
    router.push('/login')
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || t('settings.err_delete') }
    loading.value = false
  }
}

// Charging Providers
interface ChargingProvider {
  id: string
  providerName: string
  acPricePerKwh: number | null
  dcPricePerKwh: number | null
  monthlyFeeEur: number
  sessionFeeEur: number
  activeFrom: string
  activeUntil: string | null
}

const KNOWN_EMPS = [
  // Deutschland
  'ADAC e-Charge',
  'Aral Pulse',
  'bp pulse',
  'Charge Now (BMW)',
  'EnBW mobility+',
  'Elli (VW)',
  'E.ON Drive',
  'EWE Go',
  'Fastned Gold',
  'IONITY Passport',
  'Lichtblick',
  'Maingau Energie',
  'Mercedes me Charge',
  'NewMotion',
  'Plugsurfing',
  'Shell Recharge',
  'Stadtwerke',
  'Tesla',
  // Österreich
  'Ella (AT)',
  'SMATRICS EnBW (AT)',
  // Schweiz
  'Move (CH)',
  'Anderer Anbieter',
]

const chargingProviders = ref<ChargingProvider[]>([])
const showProviderForm = ref(false)
const showProviderHistory = ref(false)
const providerForm = ref({
  providerName: '',
  customProviderName: '',
  acPricePerKwh: '' as string | number,
  dcPricePerKwh: '' as string | number,
  monthlyFeeEur: 0,
  sessionFeeEur: 0,
  activeFrom: new Date().toISOString().split('T')[0],
})

const activeProvider = computed(() =>
  chargingProviders.value.find(p => p.activeUntil === null) ?? null
)
const pastProviders = computed(() =>
  chargingProviders.value.filter(p => p.activeUntil !== null)
)
const isCustomProvider = computed(() => providerForm.value.providerName === 'Anderer Anbieter')

const fetchChargingProviders = async () => {
  try {
    const res = await api.get('/users/me/charging-providers')
    chargingProviders.value = res.data
  } catch {
    // not critical - section just stays empty
  }
}

const saveChargingProvider = async () => {
  const name = isCustomProvider.value
    ? providerForm.value.customProviderName.trim()
    : providerForm.value.providerName

  if (!name || !providerForm.value.activeFrom) return

  loading.value = true
  message.value = null
  try {
    await api.post('/users/me/charging-providers', {
      providerName: name,
      acPricePerKwh: providerForm.value.acPricePerKwh !== '' ? providerForm.value.acPricePerKwh : null,
      dcPricePerKwh: providerForm.value.dcPricePerKwh !== '' ? providerForm.value.dcPricePerKwh : null,
      monthlyFeeEur: providerForm.value.monthlyFeeEur || 0,
      sessionFeeEur: providerForm.value.sessionFeeEur || 0,
      activeFrom: providerForm.value.activeFrom,
    })
    await fetchChargingProviders()
    showProviderForm.value = false
    providerForm.value = {
      providerName: '',
      customProviderName: '',
      acPricePerKwh: '',
      dcPricePerKwh: '',
      monthlyFeeEur: 0,
      sessionFeeEur: 0,
      activeFrom: new Date().toISOString().split('T')[0],
    }
    message.value = { type: 'success', text: t('settings.tariff_ok') }
  } catch (error: any) {
    message.value = { type: 'error', text: error.response?.data?.message || t('settings.tariff_err_save') }
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

const formatPrice = (val: number | null) =>
  val != null ? formatCostPerKwh(val) : '-'

const formatDate = (dateStr: string) =>
  new Date(dateStr).toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })

// Leaderboard visibility
const toggleLeaderboardVisible = async () => {
  const newVal = !leaderboardVisible.value
  try {
    await api.put(`/users/me/leaderboard-visible?visible=${newVal}`)
    leaderboardVisible.value = newVal
  } catch {
    message.value = { type: 'error', text: t('settings.err_leaderboard') }
  }
}

// Restart Onboarding Tutorial
const restartOnboarding = () => {
  localStorage.removeItem('onboarding-completed')
  localStorage.setItem('onboarding-force', 'true')
  message.value = { type: 'success', text: t('settings.tutorial_restarting') }
  setTimeout(() => {
    window.location.reload()
  }, 1000)
}


onMounted(async () => {
  fetchUserData()
  fetchChargingProviders()
  authStore.refreshPremiumStatus()
  try {
    const status = await subscriptionService.getStatus()
    subscriptionPeriodEnd.value = status.subscriptionPeriodEnd
  } catch {
    // non-critical
  }
})
</script>

<template>
  <div class="md:max-w-4xl md:mx-auto md:p-6">
    <div class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-6">
      <!-- Header -->
      <div class="flex items-center gap-3 mb-6">
        <UserIcon class="h-8 w-8 text-gray-700 dark:text-gray-300" />
        <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-200">{{ t('settings.title') }}</h1>
      </div>

      <!-- Message Banner -->
      <div v-if="message" :class="[
        'mb-6 p-4 rounded-lg',
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
            class="ml-auto flex items-center gap-2 px-3 py-1.5 rounded-lg border border-red-300 dark:border-red-700 text-red-600 dark:text-red-400 text-sm font-medium hover:bg-red-50 dark:hover:bg-red-900/30 transition shadow-[0_4px_0_0_#fca5a5] dark:shadow-[0_4px_0_0_#7f1d1d] active:shadow-none active:translate-y-1 cursor-pointer" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
            <ArrowRightOnRectangleIcon class="h-4 w-4" />
            Logout
          </button>
        </h2>

        <!-- Email -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.email_label') }}</p>
              <p class="font-medium">{{ email }}</p>
            </div>
            <button
              v-if="(authStore.user as any)?.authProvider === 'LOCAL'"
              @click="showEmailForm = !showEmailForm"
              class="btn-3d px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              {{ t('settings.email_change') }}
            </button>
            <span v-else class="text-xs text-gray-400">{{ t('settings.via_provider', { provider: (authStore.user as any)?.authProvider }) }}</span>
          </div>

          <div v-if="showEmailForm" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600 space-y-3">
            <input
              v-model="newEmail"
              type="email"
              :placeholder="t('settings.email_new_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="emailCurrentPassword"
              type="password"
              :placeholder="t('settings.email_password_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('settings.email_logout_hint') }}</p>
            <div class="flex gap-2">
              <button
                @click="changeEmail"
                :disabled="loading || !newEmail || !emailCurrentPassword"
                class="btn-3d px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition">
                {{ t('settings.save') }}
              </button>
              <button
                @click="showEmailForm = false; newEmail = ''; emailCurrentPassword = ''"
                class="btn-3d px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-400 dark:hover:bg-gray-500 transition">
                {{ t('settings.cancel') }}
              </button>
            </div>
          </div>
        </div>

        <!-- Username -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.username_label') }}</p>
              <p class="font-medium">{{ username }}</p>
            </div>
            <button
              @click="showUsernameForm = !showUsernameForm"
              class="btn-3d px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              {{ t('settings.email_change') }}
            </button>
          </div>

          <div v-if="showUsernameForm" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600">
            <input
              v-model="newUsername"
              type="text"
              :placeholder="t('settings.username_new_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-indigo-500 focus:border-indigo-500 mb-3">
            <div class="flex gap-2">
              <button
                @click="changeUsername"
                :disabled="loading"
                class="btn-3d px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showUsernameForm = false; newUsername = ''"
                class="btn-3d px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-400 dark:hover:bg-gray-500 transition">
                Abbrechen
              </button>
            </div>
          </div>
        </div>

        <!-- Password -->
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
          <div class="flex justify-between items-center">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('settings.password_label') }}</p>
              <p class="font-medium">••••••••••</p>
            </div>
            <button
              @click="showPasswordForm = !showPasswordForm"
              class="btn-3d px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              {{ t('settings.email_change') }}
            </button>
          </div>

          <div v-if="showPasswordForm" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600 space-y-3">
            <input
              v-model="currentPassword"
              type="password"
              :placeholder="t('settings.password_current_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="newPassword"
              type="password"
              :placeholder="t('settings.password_new_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <input
              v-model="confirmPassword"
              type="password"
              :placeholder="t('settings.password_confirm_placeholder')"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-indigo-500 focus:border-indigo-500">
            <div class="flex gap-2">
              <button
                @click="changePassword"
                :disabled="loading"
                class="btn-3d px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition">
                Speichern
              </button>
              <button
                @click="showPasswordForm = false; currentPassword = ''; newPassword = ''; confirmPassword = ''"
                class="btn-3d px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-400 dark:hover:bg-gray-500 transition">
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
        <div v-if="authStore.isPremium" class="flex items-center justify-between p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-800 rounded-xl">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 bg-indigo-100 dark:bg-indigo-900/40 rounded-full flex items-center justify-center shrink-0">
              <svg class="w-4 h-4 text-indigo-600 dark:text-indigo-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
              </svg>
            </div>
            <div>
              <span class="text-sm font-medium text-indigo-800 dark:text-indigo-200">{{ t('upgrade.pro_active') }}</span>
              <p v-if="subscriptionPeriodEnd" class="text-xs text-indigo-600/70 dark:text-indigo-400/70 mt-0.5">
                {{ t('upgrade.pro_active_until', { date: new Date(subscriptionPeriodEnd).toLocaleDateString(locale, { day: '2-digit', month: '2-digit', year: 'numeric' }) }) }}
              </p>
            </div>
          </div>
          <button
            @click="openPortal"
            :disabled="portalLoading"
            class="text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-800 dark:hover:text-indigo-200 disabled:opacity-50 transition-colors"
          >
            {{ portalLoading ? t('upgrade.pro_manage_loading') : t('upgrade.pro_manage_btn') }}
          </button>
        </div>
        <div v-else class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700/50 border border-gray-200 dark:border-gray-600 rounded-xl">
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
            v-for="c in countries"
            :key="c.code"
            @click="countryStore.setCountry(c.code)"
            class="flex flex-col items-center gap-1 min-w-[72px] px-3 py-2.5 rounded-xl border-2 transition-all shrink-0 cursor-pointer"
            :class="countryStore.country === c.code
              ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
              : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'">
            <span class="text-2xl">{{ c.flag }}</span>
            <span class="text-xs font-medium" :class="countryStore.country === c.code ? 'text-green-700 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'">{{ c.name[locale] || c.name.en }}</span>
          </button>
        </div>
      </div>

      <!-- Ladetarif Section -->
      <div class="mb-8">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-4 flex items-center gap-2">
          <CreditCardIcon class="h-6 w-6" />
          {{ t('settings.tariff_title') }}
        </h2>

        <!-- Aktueller Tarif -->
        <div class="mb-3 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
          <div v-if="activeProvider" class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <p class="font-semibold text-gray-800 dark:text-gray-100 truncate">{{ activeProvider.providerName }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ t('settings.tariff_active_since') }} {{ formatDate(activeProvider.activeFrom) }}</p>
              <div class="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-sm text-gray-600 dark:text-gray-300">
                <span v-if="activeProvider.acPricePerKwh != null">AC: {{ formatPrice(activeProvider.acPricePerKwh) }}</span>
                <span v-if="activeProvider.dcPricePerKwh != null">DC: {{ formatPrice(activeProvider.dcPricePerKwh) }}</span>
                <span v-if="activeProvider.monthlyFeeEur > 0">{{ formatCurrency(activeProvider.monthlyFeeEur!) }}/{{ t('settings.month_short') }}</span>
              </div>
            </div>
            <button
              @click="showProviderForm = !showProviderForm"
              class="btn-3d flex-shrink-0 px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              {{ t('settings.tariff_switch_btn') }}
            </button>
          </div>
          <div v-else class="flex items-center justify-between gap-4">
            <div>
              <p class="font-medium text-gray-700 dark:text-gray-300">{{ t('settings.tariff_none_title') }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ t('settings.tariff_none_desc') }}</p>
            </div>
            <button
              @click="showProviderForm = !showProviderForm"
              class="btn-3d flex-shrink-0 flex items-center gap-1 px-3 py-1 text-sm bg-indigo-600 text-white rounded-md hover:bg-indigo-700 transition">
              <PlusIcon class="h-4 w-4" />
              {{ t('settings.tariff_add_btn') }}
            </button>
          </div>
        </div>

        <!-- Formular -->
        <div v-if="showProviderForm" class="mb-3 p-4 bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-700 rounded-lg space-y-3">
          <p class="text-sm font-medium text-indigo-800 dark:text-indigo-300">
            {{ activeProvider ? t('settings.tariff_form_title_switch') : t('settings.tariff_form_title_new') }}
          </p>

          <!-- Anbieter -->
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_provider_label') }}</label>
            <select
              v-model="providerForm.providerName"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500">
              <option value="" disabled>{{ t('settings.tariff_provider_placeholder') }}</option>
              <option v-for="emp in KNOWN_EMPS" :key="emp" :value="emp">{{ emp }}</option>
            </select>
          </div>
          <div v-if="isCustomProvider">
            <input
              v-model="providerForm.customProviderName"
              type="text"
              :placeholder="t('settings.tariff_provider_custom_placeholder')"
              maxlength="100"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>

          <!-- Preise -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_ac_label') }}</label>
              <input
                v-model="providerForm.acPricePerKwh"
                type="number" step="0.0001" min="0" max="5"
                placeholder="z.B. 0.39"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_dc_label') }}</label>
              <input
                v-model="providerForm.dcPricePerKwh"
                type="number" step="0.0001" min="0" max="5"
                placeholder="z.B. 0.49"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_monthly_label') }}</label>
              <input
                v-model="providerForm.monthlyFeeEur"
                type="number" step="0.01" min="0"
                placeholder="0.00"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_session_label') }}</label>
              <input
                v-model="providerForm.sessionFeeEur"
                type="number" step="0.0001" min="0"
                placeholder="0.00"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500" />
            </div>
          </div>

          <!-- Aktiv seit -->
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('settings.tariff_active_from_label') }}</label>
            <input
              v-model="providerForm.activeFrom"
              type="date"
              :max="new Date().toISOString().split('T')[0]"
              class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md text-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>

          <div class="flex gap-2 pt-1">
            <button
              @click="saveChargingProvider"
              :disabled="loading || !providerForm.providerName || (isCustomProvider && !providerForm.customProviderName)"
              class="btn-3d flex-1 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 disabled:opacity-50 transition text-sm">
              {{ t('settings.tariff_save') }}
            </button>
            <button
              @click="showProviderForm = false"
              class="btn-3d flex-1 px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-400 transition text-sm">
              {{ t('settings.tariff_cancel') }}
            </button>
          </div>
        </div>

        <!-- History -->
        <div v-if="pastProviders.length > 0">
          <button
            @click="showProviderHistory = !showProviderHistory"
            class="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition">
            <ChevronDownIcon v-if="!showProviderHistory" class="h-4 w-4" />
            <ChevronUpIcon v-else class="h-4 w-4" />
            {{ pastProviders.length === 1 ? t('settings.tariff_history_one') : t('settings.tariff_history_many', { n: pastProviders.length }) }}
          </button>
          <div v-if="showProviderHistory" class="mt-2 space-y-2">
            <div
              v-for="provider in pastProviders"
              :key="provider.id"
              class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg text-sm">
              <div class="min-w-0">
                <p class="font-medium text-gray-700 dark:text-gray-300 truncate">{{ provider.providerName }}</p>
                <p class="text-xs text-gray-400 mt-0.5">
                  {{ formatDate(provider.activeFrom) }} - {{ provider.activeUntil ? formatDate(provider.activeUntil) : '' }}
                </p>
              </div>
              <button
                @click="deleteChargingProvider(provider.id)"
                class="flex-shrink-0 ml-3 p-1.5 text-gray-400 hover:text-red-500 transition rounded">
                <TrashIcon class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Watt Balance -->
      <div class="mb-8 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-6">
        <div class="flex items-center gap-3 mb-3">
          <BoltIcon class="h-6 w-6 text-gray-500 dark:text-gray-400" />
          <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">{{ t('settings.watt_account') }}</h2>
          <span class="ml-auto text-3xl font-bold text-amber-600">{{ coinBalance }}</span>
        </div>
        <div class="flex items-center justify-between mb-4">
          <router-link to="/coins/history" class="btn-3d px-3 py-1.5 rounded-lg bg-amber-100 hover:bg-amber-200 text-amber-800 dark:bg-amber-900/30 dark:hover:bg-amber-900/50 dark:text-amber-300 text-xs font-semibold border border-amber-300 dark:border-amber-700 transition">
            {{ t('settings.watt_history') }}
          </router-link>
          <router-link to="/leaderboard" class="btn-3d px-3 py-1.5 rounded-lg bg-amber-100 hover:bg-amber-200 text-amber-800 dark:bg-amber-900/30 dark:hover:bg-amber-900/50 dark:text-amber-300 text-xs font-semibold border border-amber-300 dark:border-amber-700 transition">
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
      <div v-if="referralCode" class="mb-8 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-6">
        <div class="flex items-center gap-3 mb-2">
          <ShareIcon class="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
          <h2 class="text-xl font-bold text-gray-800 dark:text-gray-200">{{ t('settings.referral_title') }}</h2>
        </div>
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-4" v-html="t('settings.referral_desc')" />
        <div class="flex gap-2">
          <input
            :value="referralLink()"
            readonly
            class="flex-1 min-w-0 px-3 py-2 text-sm bg-white dark:bg-gray-700 border border-indigo-200 dark:border-indigo-700 rounded-lg text-gray-700 dark:text-gray-300 focus:outline-none cursor-default select-all" />
          <button
            @click="copyReferralLink"
            class="btn-3d flex-shrink-0 flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-lg transition"
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
        <div class="p-4 bg-gradient-to-r from-indigo-50 to-purple-50 dark:from-indigo-900/30 dark:to-purple-900/30 border border-indigo-200 dark:border-indigo-700 rounded-lg">
          <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-2 flex items-center gap-2">
            <span class="text-xl">👋</span>
            {{ t('settings.tutorial_title') }}
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
            {{ t('settings.tutorial_desc') }}
          </p>
          <button
            @click="restartOnboarding"
            class="btn-3d w-full flex items-center justify-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition">
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
        <div class="p-4 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
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
        <div class="mb-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
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
          class="btn-3d w-full mb-4 flex items-center justify-center gap-2 px-4 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition">
          <ArrowDownTrayIcon class="h-5 w-5" />
          <span>{{ t('settings.export_btn') }}</span>
        </button>

        <!-- Delete Account -->
        <button
          @click="showDeleteConfirm = true"
          class="btn-3d w-full flex items-center justify-center gap-2 px-4 py-3 bg-red-600 text-white rounded-lg hover:bg-red-700 transition">
          <TrashIcon class="h-5 w-5" />
          <span>{{ t('settings.delete_btn') }}</span>
        </button>
      </div>

      <!-- Delete Confirmation Modal -->
      <div
        v-if="showDeleteConfirm"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showDeleteConfirm = false">
        <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl max-w-md w-full p-6">
          <h3 class="text-2xl font-bold text-red-600 mb-4">{{ t('settings.delete_modal_title') }}</h3>
          <p class="text-gray-700 dark:text-gray-300 mb-4" v-html="t('settings.delete_modal_desc')" />

          <input
            v-model="deletePassword"
            type="password"
            :placeholder="t('settings.delete_password_placeholder')"
            class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-md focus:ring-red-500 focus:border-red-500 mb-4">

          <div class="flex gap-3">
            <button
              @click="deleteAccount"
              :disabled="loading || !deletePassword"
              class="btn-3d flex-1 px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 disabled:opacity-50 transition">
              {{ t('settings.delete_confirm_btn') }}
            </button>
            <button
              @click="showDeleteConfirm = false; deletePassword = ''"
              class="btn-3d flex-1 px-4 py-2 bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-400 dark:hover:bg-gray-500 transition">
              {{ t('settings.cancel') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
  <DemoSettingsModal />
</template>
