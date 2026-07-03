<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { TruckIcon } from '@heroicons/vue/24/outline'
import LicensePlate from '../car/LicensePlate.vue'
import CarCardDetails from '../dashboard/CarCardDetails.vue'
import { carDisplayName } from '../../utils/enumLabel'
import { isVwGroupBrand } from '../../api/vwGroupService'
import { useVehicleCharging } from '../../composables/useVehicleCharging'
import type { SmartcarConnectionStatus } from '../../api/smartcarService'
import type { VwGroupConnectionStatus } from '../../api/vwGroupService'

/**
 * Mobile Auto-Card-Selektor (<768px). Geteilte Quelle fuer Dashboard + Log-Feed.
 * Bewusst KEIN Collapse-on-Scroll (bleibt immer voll) und extra Top-Abstand,
 * damit der fixe Ticker samt Lasche darueber Platz hat. Desktop-Layout liegt
 * weiterhin pro View (dieser Selektor wird per md:hidden nur mobil gezeigt).
 */
const props = withDefaults(defineProps<{
  /** Ausgewaehltes Auto (v-model). */
  modelValue: string | null
  cars: any[]
  carImageUrls: Record<string, string>
  wltp: any
  currentOdometerKm: number | null
  teslaStatus: any
  smartcarStatus: SmartcarConnectionStatus | null
  vwGroupStatus: VwGroupConnectionStatus | null
  /**
   * Single-Car-Details direkt in der Card zeigen (Log-Feed). Das Dashboard hat
   * einen eigenen "Mehr Details"-Toggle darunter und setzt dies auf false.
   */
  showInlineDetails?: boolean
}>(), {
  showInlineDetails: false,
})

const emit = defineEmits<{ 'update:modelValue': [carId: string] }>()

const { t } = useI18n()

const { isVehicleCharging, isSmartcarCharging, isWallboxCharging } = useVehicleCharging(
  computed(() => props.cars),
  computed(() => props.smartcarStatus),
  computed(() => props.vwGroupStatus),
)
</script>

<template>
  <div
    v-if="cars.length > 0"
    :class="cars.length > 1
      ? 'sticky top-[calc(env(safe-area-inset-top)+3.5rem)] z-10 bg-white dark:bg-gray-800 -mx-4 px-4 py-1.5 mt-2 mb-4 border-b border-gray-100 dark:border-gray-700 shadow-sm'
      : 'mt-2 mb-4'"
  >
    <div class="flex gap-3 overflow-x-auto car-scroll-hide pb-1">
      <button
        v-for="car in cars"
        :key="car.id"
        @click="emit('update:modelValue', car.id)"
        :class="[
          cars.length === 1
            ? 'flex items-start rounded-sm border-2 text-left transition w-full overflow-hidden'
            : 'flex items-center rounded-sm border-2 text-left transition flex-shrink-0 min-w-[180px] max-w-[240px] overflow-hidden',
          modelValue === car.id
            ? isVehicleCharging(car)
              ? 'border-2 border-green-500 bg-green-50 dark:bg-green-900/20 shadow-[2px_2px_0_0_#16a34a] dark:shadow-[2px_2px_0_0_#14532d]'
              : 'border-2 border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-[2px_2px_0_0_#4338ca] dark:shadow-[2px_2px_0_0_#312e81]'
            : isVehicleCharging(car)
              ? 'border-2 border-green-300 dark:border-green-700 bg-white dark:bg-gray-700 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]'
              : 'border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151] hover:border-indigo-300',
          cars.length > 1 && isVehicleCharging(car) ? 'ring-2 ring-green-400 dark:ring-green-500' : '',
        ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
        <div
          :class="[
            'flex-shrink-0 bg-gray-100 dark:bg-gray-600 flex items-center justify-center overflow-hidden',
            cars.length === 1 ? 'w-16 self-stretch' : 'h-12 aspect-[4/3]'
          ]">
          <img
            v-if="carImageUrls[car.id]"
            :src="carImageUrls[car.id]"
            :alt="car.model"
            class="w-full h-full object-cover" />
          <TruckIcon v-else class="w-6 h-6 text-gray-400" />
        </div>
        <div class="min-w-0 flex-1 px-3 py-1.5 flex flex-col justify-center">
          <!-- Kompakt-Zeile: Name, Trim, Kennzeichen (single), Charging-Badges -->
          <div class="flex items-center gap-1.5 flex-wrap">
            <span class="font-semibold text-sm text-gray-800 dark:text-gray-200 whitespace-nowrap">{{ carDisplayName(car.brand, car.model) }}</span>
            <span v-if="car.trim" class="text-xs text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
            <LicensePlate v-if="cars.length === 1 && car.licensePlate" :plate="car.licensePlate" size="sm" class="flex-shrink-0" />
            <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
              <span v-if="teslaStatus.vehicleState === 'charging'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 text-green-700 text-xs rounded-full font-medium border border-green-200">
                <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
              </span>
              <span v-else-if="teslaStatus.vehicleState === 'online'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>{{ t('dashboard.tesla_online') }}
              </span>
              <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 text-gray-500 text-xs rounded-full font-medium border border-gray-300">
                <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>{{ t('dashboard.tesla_sleeping') }}
              </span>
            </template>
            <span v-if="isSmartcarCharging(car)"
              class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
            </span>
            <template v-if="isVwGroupBrand(car.brand) && vwGroupStatus?.connected">
              <span v-if="vwGroupStatus.vehicleState === 'charging'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
                <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>
                {{ t('dashboard.vwgroup_charging') }}
                <span v-if="vwGroupStatus.lastSoc != null" class="opacity-75">· {{ vwGroupStatus.lastSoc }}%</span>
              </span>
              <span v-else
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs rounded-full font-medium border border-blue-200 dark:border-blue-700">
                <span class="w-1.5 h-1.5 rounded-full bg-blue-400"></span>
                {{ vwGroupStatus.lastSoc != null ? vwGroupStatus.lastSoc + '%' : t('dashboard.vwgroup_connected') }}
              </span>
            </template>
            <span v-if="isWallboxCharging()"
              class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 text-xs rounded-full font-medium border border-green-200 dark:border-green-700">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
            </span>
          </div>
          <!-- Zusaetzliche Auto-Daten + Kennzeichen (single-car, ausgewaehlt) -->
          <div
            v-if="showInlineDetails && cars.length === 1 && car.id === modelValue"
            class="mt-2 pt-2 border-t border-gray-200 dark:border-gray-600">
            <CarCardDetails :car="car" :wltp="wltp" :current-odometer-km="currentOdometerKm" orientation="compact" />
          </div>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped>
/* Horizontalen Scrollbalken am Auto-Streifen ausblenden (Peek + Touch reichen). */
.car-scroll-hide { scrollbar-width: none; }
.car-scroll-hide::-webkit-scrollbar { display: none; }
</style>
