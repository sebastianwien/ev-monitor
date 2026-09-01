<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, CurrencyEuroIcon, CheckCircleIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import api from '@/api/axios'
import PriceAmendModal from './PriceAmendModal.vue'
import type { EvLogResponse } from './EditLogModal.vue'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

const { t } = useI18n()
const { formatDecimal } = useLocaleFormat()
const props = defineProps<{ carId: string | null; open: boolean }>()
const emit = defineEmits<{ close: []; updated: [] }>()

const logs = ref<EvLogResponse[]>([])
const loading = ref(false)
const amendingLog = ref<EvLogResponse | null>(null)

async function loadLogs() {
  if (!props.carId) return
  loading.value = true
  try {
    const res = await api.get(`/logs/priceless?carId=${props.carId}`)
    logs.value = res.data
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (open) => {
  if (open) loadLogs()
})

function onAmended() {
  amendingLog.value = null
  loadLogs()
  emit('updated')
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div class="absolute inset-0 bg-black/50" @click="emit('close')" />

      <div class="relative w-full sm:max-w-2xl bg-white dark:bg-gray-800 sm:rounded-sm shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] ring-1 ring-black/10 dark:ring-white/10 overflow-hidden flex flex-col max-h-[90dvh]">
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100 dark:border-gray-700">
          <div class="flex items-center gap-2">
            <CurrencyEuroIcon class="h-5 w-5 text-amber-500" />
            <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">{{ t('priceless.title') }}</h2>
            <span v-if="logs.length > 0"
              class="text-xs bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 px-2 py-0.5 rounded-full font-medium">
              {{ logs.length }}
            </span>
          </div>
          <button @click="emit('close')" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 p-1 rounded-sm">
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Info -->
        <div class="px-5 py-3 bg-amber-50 dark:bg-amber-900/20 border-b border-amber-100 dark:border-amber-800/40 flex items-start gap-2">
          <InformationCircleIcon class="h-4 w-4 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
          <p class="text-xs text-amber-700 dark:text-amber-300 leading-relaxed">{{ t('priceless.info') }}</p>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto flex-1">
          <div v-if="loading" class="flex items-center justify-center py-12">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-amber-500" />
          </div>

          <template v-else>
            <div v-if="logs.length === 0" class="flex flex-col items-center justify-center py-12 gap-2">
              <CheckCircleIcon class="h-10 w-10 text-green-400" />
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('priceless.all_clear') }}</p>
            </div>

            <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
              <li v-for="log in logs" :key="log.id" class="flex items-center gap-3 px-5 py-3.5">
                <div class="min-w-0 flex-1">
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ formatDate(log.loggedAt) }}</span>
                  <span v-if="log.kwhCharged != null" class="ml-2 text-xs text-gray-500 dark:text-gray-400">
                    {{ formatDecimal(log.kwhCharged, 1) }} kWh
                  </span>
                </div>
                <button @click="amendingLog = log"
                  class="shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-amber-700 dark:text-amber-300 bg-amber-100 dark:bg-amber-900/40 hover:bg-amber-200 dark:hover:bg-amber-900/60 rounded-sm transition">
                  <CurrencyEuroIcon class="h-4 w-4" />
                  {{ t('priceless.add_price') }}
                </button>
              </li>
            </ul>
          </template>
        </div>

        <!-- Footer -->
        <div class="px-5 py-3 border-t border-gray-100 dark:border-gray-700 flex justify-end">
          <button @click="emit('close')"
            class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-sm transition">
            {{ t('priceless.close') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <PriceAmendModal
    v-if="amendingLog"
    :log="amendingLog"
    @close="amendingLog = null"
    @saved="onAmended"
  />
</template>
