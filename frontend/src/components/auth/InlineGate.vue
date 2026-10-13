<template>
  <div data-testid="inline-gate" :class="plain ? '' : 'rounded-xl border-2 border-gray-800 dark:border-gray-200 bg-green-50 dark:bg-green-900/20 p-4 sm:p-5'">
    <p v-if="lead" class="flex items-start gap-2 font-semibold text-gray-900 dark:text-gray-100">
      <LockOpenIcon class="h-5 w-5 shrink-0 mt-0.5 text-green-700 dark:text-green-400" />
      <span>{{ lead }}</span>
    </p>
    <p v-if="detail" class="mt-2 text-sm text-gray-700 dark:text-gray-300">{{ detail }}</p>

    <!-- Google: One Tap und Button; ohne Script (Adblocker, ITP) der bisherige Redirect-Login -->
    <div v-if="googleEnabled || redirectGoogleEnabled" :class="lead ? 'mt-4' : ''">
      <div v-show="!googleUnavailable" ref="googleButton" class="min-h-[44px]" />
      <GoogleButton v-if="googleUnavailable && redirectGoogleEnabled" :label="t('gate.google_redirect')" @click="onRedirectGoogle" />
      <p class="mt-3 text-center text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('gate.or_email') }}</p>
    </div>

    <!-- Schritt 1: Adresse -->
    <form v-if="step === 'email'" class="mt-4" novalidate @submit.prevent="sendCode">
      <label for="gate-email" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">
        {{ t('gate.email_label') }}
      </label>
      <div class="flex flex-col sm:flex-row gap-2">
        <input id="gate-email" v-model.trim="email" type="email" autocomplete="email" inputmode="email" required
               :placeholder="t('gate.email_placeholder')"
               :class="inputClass" class="flex-1 min-w-0 px-3 py-2.5 text-base text-gray-900 dark:text-gray-100 focus:border-green-600 focus:outline-none" />
        <button type="submit" :disabled="busy"
                :class="buttonClass" class="shrink-0 min-h-11 px-4 py-2.5 font-bold text-white disabled:opacity-60">
          {{ busy ? t('gate.sending') : t('gate.send_code') }}
        </button>
      </div>
    </form>

    <!-- Schritt 2: Code -->
    <form v-else class="mt-4" novalidate @submit.prevent="verify">
      <p class="text-sm text-gray-700 dark:text-gray-300 mb-2">
        {{ t('gate.code_sent', { email }) }}
      </p>
      <label for="gate-code" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">
        {{ t('gate.code_label') }}
      </label>
      <div class="flex flex-col sm:flex-row gap-2">
        <input id="gate-code" v-model.trim="code" type="text" autocomplete="one-time-code" inputmode="numeric"
               pattern="[0-9]{6}" maxlength="6" required
               :class="inputClass" class="flex-1 min-w-0 px-3 py-2.5 text-center text-xl font-bold tracking-[0.4em] tabular-nums text-gray-900 dark:text-gray-100 focus:border-green-600 focus:outline-none" />
        <button type="submit" :disabled="busy"
                :class="buttonClass" class="shrink-0 min-h-11 px-4 py-2.5 font-bold text-white disabled:opacity-60">
          {{ busy ? t('gate.verifying') : t('gate.confirm') }}
        </button>
      </div>
      <button type="button" class="mt-2 text-sm font-semibold text-green-700 dark:text-green-400 underline" @click="restart">
        {{ t('gate.change_email') }}
      </button>
    </form>

    <p v-if="error" role="alert" class="mt-3 text-sm font-semibold text-red-700 dark:text-red-400">{{ error }}</p>

    <p class="mt-4 text-xs text-gray-600 dark:text-gray-400">
      {{ consent ?? t('gate.consent') }}
      <a href="/datenschutz" class="underline">{{ t('gate.privacy_link') }}</a>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useI18n } from 'vue-i18n'
import { LockOpenIcon } from '@heroicons/vue/24/outline'
import GoogleButton from './GoogleButton.vue'
import { useAuthStore } from '../../stores/auth'
import { analytics } from '../../services/analytics'
import { currentUtmParams } from '../../utils/reddit-pixel'
import { useGoogleIdentity } from '../../composables/useGoogleIdentity'
import { rememberReturnPath } from '../../utils/postLoginRedirect'

/**
 * Login without leaving the page: address, then a 6-digit mail code. A new address gets a
 * free account on the first valid code. `lead` names what the visitor gets before we ask.
 * `placement` marks the account as won through this gate (utm_medium "gate", utm_campaign =
 * placement) so gate sign-ups can be counted; a campaign the visitor arrived with stays.
 */
const props = defineProps<{
  lead?: string
  detail?: string
  placement?: string
  /** inside a box that already draws the frame and the heading: no own border, background or padding */
  plain?: boolean
  /** replaces the default consent line above the privacy link */
  consent?: string
}>()
const emit = defineEmits<{ authenticated: [] }>()

const { t, locale } = useI18n()
const authStore = useAuthStore()

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
const step = ref<'email' | 'code'>('email')
const email = ref('')
const code = ref('')
const busy = ref(false)
const error = ref('')
const googleButton = ref<HTMLElement | null>(null)
const { enabled: googleEnabled, unavailable: googleUnavailable, mount: mountGoogle } = useGoogleIdentity()
/** Inside a page box: flat like the page; standalone: the app's 3D style */
const inputClass = computed(() => props.plain
  ? 'rounded-xl border border-gray-300 bg-white dark:border-gray-700 dark:bg-gray-900'
  : 'rounded-lg border-2 border-gray-300 bg-white dark:border-gray-600 dark:bg-gray-800')
const buttonClass = computed(() => props.plain
  ? 'rounded-xl bg-green-600 hover:bg-green-700'
  : 'btn-3d rounded-lg border-2 border-gray-800 bg-green-600 dark:border-gray-200')
const redirectGoogleEnabled = import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true' && !Capacitor.isNativePlatform()

onMounted(() => {
  if (googleButton.value) void mountGoogle(googleButton.value, loginWithGoogle)
})

async function loginWithGoogle(idToken: string) {
  error.value = ''
  busy.value = true
  try {
    await authStore.loginWithGoogle({ idToken, locale: locale.value, ...currentUtm() })
    emit('authenticated')
  } catch {
    error.value = t('gate.error_google')
  } finally {
    busy.value = false
  }
}

/** Redirect login leaves the page: come back here, the calculator inputs wait in sessionStorage. */
function onRedirectGoogle() {
  rememberReturnPath(window.location.pathname + window.location.search)
}

async function sendCode() {
  error.value = ''
  if (!EMAIL_REGEX.test(email.value)) {
    error.value = t('gate.error_email')
    return
  }
  busy.value = true
  try {
    await authStore.requestLoginCode(email.value, locale.value)
    analytics.track('gate_code_requested')
    step.value = 'code'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

async function verify() {
  error.value = ''
  if (!/^\d{6}$/.test(code.value)) {
    error.value = t('gate.error_code_format')
    return
  }
  busy.value = true
  try {
    const utm = currentUtm()
    await authStore.loginWithCode({ email: email.value, code: code.value, locale: locale.value, ...utm })
    if (props.placement) analytics.track('gate_code_verified', { placement: props.placement })
    else analytics.track('gate_code_verified')
    emit('authenticated')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

function restart() {
  step.value = 'email'
  code.value = ''
  error.value = ''
}

/**
 * Attribution for a new account: URL first, then the UTM stored on the first page view. With a
 * placement, empty medium and campaign name the gate; the source is never invented.
 */
function currentUtm(): { utmSource?: string; utmMedium?: string; utmCampaign?: string } {
  const utm = currentUtmParams()
  const fromVisit = {
    utmSource: utm?.source || undefined,
    utmMedium: utm?.medium || undefined,
    utmCampaign: utm?.campaign || undefined,
  }
  if (!props.placement) return fromVisit
  return {
    utmSource: fromVisit.utmSource,
    utmMedium: fromVisit.utmMedium ?? 'gate',
    utmCampaign: fromVisit.utmCampaign ?? props.placement.slice(0, 100),
  }
}

function errorMessage(e: unknown): string {
  const response = (e as { response?: { status?: number; data?: { code?: string } } }).response
  if (response?.status === 429) return t('gate.error_rate_limited')
  if (response?.data?.code === 'INVALID_CODE') return t('gate.error_code_invalid')
  if (response?.status === 400) return t('gate.error_email')
  return t('gate.error_generic')
}
</script>
