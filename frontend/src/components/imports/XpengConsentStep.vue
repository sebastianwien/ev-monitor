<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShieldCheckIcon, BoltIcon } from '@heroicons/vue/24/outline'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import xpengService from '../../api/xpengService'
import { useAuthStore } from '../../stores/auth'
import type { Car } from '../../api/carService'

const { t } = useI18n()
const auth = useAuthStore()

const props = defineProps<{
  carsNeedingConsent: Car[]
}>()

const emit = defineEmits<{
  (e: 'granted'): void
  (e: 'error', msg: string): void
}>()

const carId = ref<string | null>(null)
const vin = ref('')
const accepted = ref(false)
const autoSync = ref(auth.canActivateTelemetry.value)
const xpengEmail = ref('')
const busy = ref(false)

watch(() => props.carsNeedingConsent, (cars) => {
  if (cars.length === 1 && !carId.value) carId.value = cars[0].id
}, { immediate: true })

watch(auth.canActivateTelemetry, (can) => {
  if (!can) autoSync.value = false
})

function selectMode(isAutoSync: boolean) {
  autoSync.value = isAutoSync
  accepted.value = false
}

const consentText = computed(() =>
  autoSync.value ? t('xpeng.consent_full_text_v2') : t('xpeng.consent_full_text'),
)

const canSubmit = computed(() =>
  !!carId.value && accepted.value && vin.value.trim().length === 17 && !busy.value,
)

async function submit() {
  if (!carId.value) return
  if (!accepted.value) {
    emit('error', t('xpeng.err_consent_required'))
    return
  }
  const normalized = vin.value.trim().toUpperCase()
  if (normalized.length !== 17) {
    emit('error', t('xpeng.err_vin_length'))
    return
  }
  busy.value = true
  try {
    const email = autoSync.value && xpengEmail.value.trim() ? xpengEmail.value.trim() : undefined
    await xpengService.grantConsent(carId.value, normalized, autoSync.value, email)
    vin.value = ''
    accepted.value = false
    xpengEmail.value = ''
    emit('granted')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    emit('error', err.response?.data?.error ?? err.message ?? 'Unknown error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="space-y-4 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
    <h3 class="text-base font-semibold flex items-center gap-2">
      <ShieldCheckIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
      {{ t('xpeng.step_consent_title') }}
    </h3>

    <!-- Mode selector: nur fuer berechtigte User -->
    <div v-if="auth.canActivateTelemetry" class="grid grid-cols-2 gap-2">
      <button
        type="button"
        @click="selectMode(true)"
        :class="autoSync
          ? 'border-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 text-amber-900 dark:text-amber-200'
          : 'border-2 border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600'"
        class="flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors">
        <BoltIcon class="w-4 h-4 shrink-0" :class="autoSync ? 'text-amber-500' : 'text-gray-400'" />
        {{ t('xpeng.mode_autosync') }}
      </button>
      <button
        type="button"
        @click="selectMode(false)"
        :class="!autoSync
          ? 'border-2 border-gray-400 dark:border-gray-500 bg-gray-50 dark:bg-gray-700/30 text-gray-900 dark:text-gray-100'
          : 'border-2 border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600'"
        class="flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors">
        {{ t('xpeng.mode_manual') }}
      </button>
    </div>

    <div>
      <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">{{ t('xpeng.car_label') }}</label>
      <CarSelectDropdown v-model="carId" :cars="carsNeedingConsent" />
    </div>

    <div>
      <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">{{ t('xpeng.vin_label') }}</label>
      <input
        v-model="vin"
        type="text"
        maxlength="17"
        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 dark:text-white uppercase font-mono text-sm"
        placeholder="L1NN...."
      />
      <p class="text-xs text-gray-500 mt-1">{{ t('xpeng.vin_hint') }}</p>
    </div>

    <!-- XPeng E-Mail nur im AutoSync-Modus -->
    <div v-if="autoSync">
      <label class="block text-sm text-gray-700 dark:text-gray-300 mb-1">
        {{ t('xpeng.autosync_email_label') }}
      </label>
      <input
        v-model="xpengEmail"
        type="email"
        maxlength="255"
        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-sm dark:text-white"
        :placeholder="t('xpeng.autosync_email_placeholder')"
      />
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('xpeng.autosync_email_hint') }}</p>
    </div>

    <details class="text-sm text-gray-600 dark:text-gray-400 cursor-pointer">
      <summary class="font-medium">{{ t('xpeng.consent_show_full') }}</summary>
      <p class="mt-2 text-xs leading-relaxed whitespace-pre-line border-l-2 border-gray-300 dark:border-gray-600 pl-3">
        {{ consentText }}
      </p>
    </details>

    <label class="flex items-start gap-2 cursor-pointer">
      <input v-model="accepted" type="checkbox" class="mt-1" />
      <span class="text-sm text-gray-700 dark:text-gray-300">
        {{ autoSync ? t('xpeng.consent_accept_label_v2') : t('xpeng.consent_accept_label') }}
      </span>
    </label>

    <button
      @click="submit"
      :disabled="!canSubmit"
      class="btn-3d w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white px-5 py-2.5 rounded-lg font-medium text-sm transition-colors"
    >
      {{ busy ? t('common.loading') : t('xpeng.btn_grant_consent') }}
    </button>
  </section>
</template>
