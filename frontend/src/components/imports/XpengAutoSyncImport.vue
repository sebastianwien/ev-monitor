<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronDownIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline'
import XpengConsentStep from './XpengConsentStep.vue'
import XpengConnectionsList from './XpengConnectionsList.vue'
import { useXpengJobs } from '../../composables/useXpengJobs'
import xpengService from '../../api/xpengService'
import type { Car } from '../../api/carService'
import { carDisplayName } from '../../utils/enumLabel'

const { t } = useI18n()
const props = defineProps<{ cars: Car[] }>()

const {
  connections, jobs, loading, error,
  refresh,
} = useXpengJobs()

const localError = ref('')
const topError = computed(() => error.value)
const expanded = ref(false)

const xpengCars = computed(() => props.cars.filter(c => c.brand === 'XPENG'))
const connectedCarIds = computed(() => new Set(connections.value.map(c => c.carId)))

const primaryCar = computed(() => xpengCars.value[0] ?? null)
const primaryCarLabel = computed(() =>
  primaryCar.value ? carDisplayName(primaryCar.value.brand, primaryCar.value.model) : 'XPeng',
)
const primaryCarDetails = computed(() => {
  const car = primaryCar.value
  if (!car) return ''
  const parts: string[] = []
  if (car.year) parts.push(String(car.year))
  if (car.licensePlate) parts.push(car.licensePlate)
  return parts.join(' · ')
})
const extraCarsCount = computed(() => xpengCars.value.length - 1)
const hasAutoSyncActive = computed(() => connections.value.some(c => c.autoSyncEnabled))

const carsNeedingConsent = computed(() =>
  xpengCars.value.filter(c => !connectedCarIds.value.has(c.id)),
)

const upgradableConnections = computed(() =>
  connections.value.filter(c => !c.autoSyncEnabled),
)

// Auto-expand when there's something actionable
watch([carsNeedingConsent, upgradableConnections], ([needConsent, upgradable]) => {
  if (needConsent.length > 0 || upgradable.length > 0) expanded.value = true
}, { immediate: false })

const upgradingId = ref<string | null>(null)
const upgradeEmail = ref('')
const upgradeAccepted = ref(false)
const upgradeBusy = ref(false)

function openUpgrade(connectionId: string) {
  upgradingId.value = connectionId
  upgradeEmail.value = ''
  upgradeAccepted.value = false
  localError.value = ''
}

function closeUpgrade() {
  upgradingId.value = null
}

async function submitUpgrade(connectionId: string) {
  if (!upgradeAccepted.value) return
  if (!upgradeEmail.value.trim()) {
    localError.value = t('xpeng.autosync_email_required')
    return
  }
  upgradeBusy.value = true
  localError.value = ''
  try {
    await xpengService.activateAutoSync(connectionId, upgradeEmail.value.trim())
    closeUpgrade()
    await refresh()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    localError.value = err.response?.data?.error ?? err.message ?? 'Unknown error'
  } finally {
    upgradeBusy.value = false
  }
}

async function onConsentGranted() {
  localError.value = ''
  await refresh()
}

onMounted(refresh)
</script>

<template>
  <div class="rounded-sm overflow-hidden border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]">
    <!-- Header: identisch zur Tesla-Tile, klickbar -->
    <button
      type="button"
      class="w-full flex items-stretch text-left hover:bg-gray-50 dark:hover:bg-white/5 transition-colors"
      @click="expanded = !expanded"
    >
      <div class="px-3 py-4 flex flex-col justify-center items-center min-w-[80px] md:min-w-[88px] shrink-0 border-r-2 border-gray-300 dark:border-gray-700 bg-gray-950 dark:bg-gray-700 text-white">
        <p class="text-xl md:text-2xl font-extrabold tracking-tight">XP</p>
        <p class="text-[9px] font-bold uppercase tracking-wider mt-0.5 opacity-75">XPENG</p>
      </div>
      <div class="flex-1 flex items-center justify-between px-4 py-3.5 gap-3 min-w-0">
        <div class="flex-1 min-w-0">
          <h3 class="text-base font-bold text-gray-900 dark:text-white truncate">{{ primaryCarLabel }}</h3>
          <p v-if="primaryCarDetails || extraCarsCount > 0" class="text-xs text-gray-500 dark:text-gray-400 font-mono mt-0.5 truncate">
            <span v-if="primaryCarDetails">{{ primaryCarDetails }}</span>
            <span v-if="primaryCarDetails && extraCarsCount > 0"> · </span>
            <span v-if="extraCarsCount > 0">+{{ extraCarsCount }} weitere</span>
          </p>
        </div>
        <span
          v-if="hasAutoSyncActive"
          class="shrink-0 text-[10px] font-bold uppercase tracking-wider bg-green-600 text-white px-2 py-1 rounded-sm whitespace-nowrap"
        >● AutoSync</span>
        <ChevronDownIcon
          class="h-4 w-4 text-gray-500 dark:text-gray-400 shrink-0 transition-transform duration-200"
          :class="expanded ? 'rotate-180' : ''"
        />
      </div>
    </button>

    <!-- Expandable content -->
    <Transition name="accordion">
      <div v-if="expanded" class="border-t-2 border-gray-200 dark:border-gray-700">
        <div class="p-4 md:p-5 space-y-5">
          <div v-if="topError || localError"
               class="rounded-sm border-2 border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-950/40 p-3 text-sm text-red-900 dark:text-red-200 flex gap-2 shadow-[2px_2px_0_0_#fca5a5] dark:shadow-[2px_2px_0_0_#b91c1c]">
            <ExclamationCircleIcon class="w-5 h-5 flex-shrink-0" />
            <span>{{ topError || localError }}</span>
          </div>

          <div v-if="loading" class="text-center text-gray-500 dark:text-gray-400 py-8">{{ t('common.loading') }}</div>

          <template v-else>
            <XpengConsentStep
              v-if="carsNeedingConsent.length > 0"
              :cars-needing-consent="carsNeedingConsent"
              mode="autosync"
              :card="false"
              @granted="onConsentGranted"
              @error="localError = $event"
            />

            <section v-if="upgradableConnections.length > 0"
                     class="rounded-sm border-2 border-amber-400 dark:border-amber-600 bg-white dark:bg-gray-800 p-5 md:p-6 shadow-[2px_2px_0_0_#f59e0b] dark:shadow-[2px_2px_0_0_#d97706]">
              <p class="text-amber-600 dark:text-amber-500 text-[11px] font-bold uppercase tracking-[0.14em] mb-3">
                {{ t('xpeng.autosync_upgrade_title') }}
              </p>
              <div class="divide-y divide-amber-100 dark:divide-amber-900/40">
                <div v-for="c in upgradableConnections" :key="c.id" class="py-3 first:pt-0">
                  <div class="flex items-center justify-between gap-3">
                    <span class="font-mono text-sm font-semibold text-gray-900 dark:text-white">{{ c.vinMasked }}</span>
                    <button
                      @click="upgradingId === c.id ? closeUpgrade() : openUpgrade(c.id)"
                      class="inline-flex items-center gap-1.5 border-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 hover:bg-amber-100 dark:hover:bg-amber-950/50 text-amber-800 dark:text-amber-200 text-[11px] font-bold uppercase tracking-wide px-3 py-1.5 rounded-sm transition-colors">
                      <BoltIcon class="w-3.5 h-3.5" />
                      {{ t('xpeng.autosync_upgrade_btn') }}
                    </button>
                  </div>
                  <div v-if="upgradingId === c.id" class="mt-3 space-y-3 border-t border-amber-200 dark:border-amber-900/60 pt-3">
                    <p class="text-xs text-gray-600 dark:text-gray-400 leading-relaxed">
                      {{ t('xpeng.autosync_upgrade_body') }}
                    </p>
                    <div>
                      <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">
                        {{ t('xpeng.autosync_email_label') }}
                      </label>
                      <input
                        v-model="upgradeEmail"
                        type="email"
                        maxlength="255"
                        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-800 text-sm dark:text-white"
                        :placeholder="t('xpeng.autosync_email_placeholder')"
                      />
                      <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('xpeng.autosync_email_hint') }}</p>
                    </div>
                    <label class="flex items-start gap-2 cursor-pointer">
                      <input v-model="upgradeAccepted" type="checkbox" class="mt-0.5" />
                      <span class="text-xs text-gray-700 dark:text-gray-300">{{ t('xpeng.autosync_upgrade_accept') }}</span>
                    </label>
                    <div class="flex gap-2">
                      <button
                        @click="submitUpgrade(c.id)"
                        :disabled="!upgradeAccepted || !upgradeEmail.trim() || upgradeBusy"
                        class="btn-3d inline-flex items-center gap-1.5 bg-amber-500 hover:bg-amber-400 disabled:opacity-50 disabled:cursor-not-allowed text-gray-950 font-bold uppercase tracking-wide text-xs px-4 py-2 rounded-sm">
                        <BoltIcon class="w-3.5 h-3.5" />
                        {{ upgradeBusy ? t('common.loading') : t('xpeng.autosync_upgrade_btn') }}
                      </button>
                      <button
                        @click="closeUpgrade"
                        class="text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 px-3 py-2">
                        {{ t('common.cancel') }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <XpengConnectionsList
              :connections="connections"
              :jobs="jobs"
              filter="autosync"
              @refresh="refresh"
              @error="localError = $event"
            />

            <div v-if="carsNeedingConsent.length === 0 && upgradableConnections.length === 0 && connections.filter(c => c.autoSyncEnabled).length === 0"
                 class="text-center py-10 text-gray-500 dark:text-gray-400 text-sm">
              {{ t('xpeng.no_xpeng_cars') }}
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.accordion-enter-active,
.accordion-leave-active {
  transition: opacity 0.18s ease, max-height 0.22s ease;
  max-height: 1200px;
  overflow: hidden;
}
.accordion-enter-from,
.accordion-leave-to {
  opacity: 0;
  max-height: 0;
}
</style>
