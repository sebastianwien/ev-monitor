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
}>()

const emit = defineEmits<{
  (e: 'granted'): void
  (e: 'error', msg: string): void
}>()

const carId = ref<string | null>(null)
const vin = ref('')
const accepted = ref(false)
const busy = ref(false)

watch(() => props.carsNeedingConsent, (cars) => {
  if (cars.length === 1 && !carId.value) carId.value = cars[0].id
}, { immediate: true })

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
    await xpengService.grantConsent(carId.value, normalized)
    vin.value = ''
    accepted.value = false
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

    <details class="text-sm text-gray-600 dark:text-gray-400 cursor-pointer">
      <summary class="font-medium">{{ t('xpeng.consent_show_full') }}</summary>
      <p class="mt-2 text-xs leading-relaxed whitespace-pre-line border-l-2 border-gray-300 dark:border-gray-600 pl-3">
        {{ t('xpeng.consent_full_text') }}
      </p>
    </details>

    <label class="flex items-start gap-2 cursor-pointer">
      <input v-model="accepted" type="checkbox" class="mt-1" />
      <span class="text-sm text-gray-700 dark:text-gray-300">{{ t('xpeng.consent_accept_label') }}</span>
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
