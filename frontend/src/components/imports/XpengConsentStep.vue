<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShieldCheckIcon } from '@heroicons/vue/24/outline'
import CarSelectDropdown from '../car/CarSelectDropdown.vue'
import xpengService from '../../api/xpengService'
import type { Car } from '../../api/carService'

const { t } = useI18n()

const props = defineProps<{
  carsNeedingConsent: Car[]
  mode: 'manual' | 'autosync'
  card?: boolean
}>()

const emit = defineEmits<{
  (e: 'granted'): void
  (e: 'error', msg: string): void
}>()

const carId = ref<string | null>(null)
const vin = ref('')
const accepted = ref(false)
const xpengEmail = ref('')
const busy = ref(false)

watch(() => props.carsNeedingConsent, (cars) => {
  if (cars.length === 1 && !carId.value) carId.value = cars[0].id
}, { immediate: true })

const isAutoSync = computed(() => props.mode === 'autosync')

const consentText = computed(() =>
  isAutoSync.value ? t('xpeng.consent_full_text_v2') : t('xpeng.consent_full_text'),
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
    const email = isAutoSync.value && xpengEmail.value.trim() ? xpengEmail.value.trim() : undefined
    await xpengService.grantConsent(carId.value, normalized, isAutoSync.value, email)
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
  <section :class="props.card !== false ? 'rounded-sm border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]' : ''" class="space-y-4">
    <h3 class="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 flex items-center gap-2">
      <ShieldCheckIcon class="w-4 h-4" />
      {{ t('xpeng.step_consent_title') }}
    </h3>

    <div>
      <label class="block text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wide mb-1.5">{{ t('xpeng.car_label') }}</label>
      <CarSelectDropdown v-model="carId" :cars="carsNeedingConsent" />
    </div>

    <div>
      <label class="block text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wide mb-1.5">{{ t('xpeng.vin_label') }}</label>
      <input
        v-model="vin"
        type="text"
        maxlength="17"
        class="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-sm bg-slate-50 dark:bg-slate-700 dark:text-white uppercase font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
        placeholder="L1NN...."
      />
      <p class="text-xs text-slate-400 dark:text-slate-500 mt-1">{{ t('xpeng.vin_hint') }}</p>
    </div>

    <div v-if="isAutoSync">
      <label class="block text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wide mb-1.5">
        {{ t('xpeng.autosync_email_label') }}
      </label>
      <input
        v-model="xpengEmail"
        type="email"
        maxlength="255"
        class="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-sm bg-slate-50 dark:bg-slate-700 text-sm dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400"
        :placeholder="t('xpeng.autosync_email_placeholder')"
      />
      <p class="text-xs text-slate-400 dark:text-slate-500 mt-1">{{ t('xpeng.autosync_email_hint') }}</p>
    </div>

    <details class="text-sm text-slate-500 dark:text-slate-400 cursor-pointer">
      <summary class="font-medium text-slate-600 dark:text-slate-300 select-none">{{ t('xpeng.consent_show_full') }}</summary>
      <p class="mt-2 text-xs leading-relaxed whitespace-pre-line border-l-2 border-slate-300 dark:border-slate-600 pl-3">
        {{ consentText }}
      </p>
    </details>

    <label class="flex items-start gap-2 cursor-pointer">
      <input v-model="accepted" type="checkbox" class="mt-1" />
      <span class="text-sm text-slate-600 dark:text-slate-300">
        {{ isAutoSync ? t('xpeng.consent_accept_label_v2') : t('xpeng.consent_accept_label') }}
      </span>
    </label>

    <button
      @click="submit"
      :disabled="!canSubmit"
      class="btn-3d w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 dark:disabled:bg-slate-600 disabled:cursor-not-allowed text-white px-5 py-2.5 rounded-sm font-medium text-sm transition-colors"
    >
      {{ busy ? t('common.loading') : t('xpeng.btn_grant_consent') }}
    </button>
  </section>
</template>
