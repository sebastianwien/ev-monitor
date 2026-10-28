<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { TruckIcon, ShareIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'
import { carDisplayName } from '../../utils/enumLabel'
import { useVehicleCharging } from '../../composables/useVehicleCharging'
import type { SmartcarConnectionStatus } from '../../api/smartcarService'

/**
 * Mobile Auto-Selektor (<768px) ueber dem Dashboard. Bewusst nur eine Zeile (Foto,
 * Name, Lade-Abzeichen): Kennzeichen, Akku und Kilometer stehen in der Fahrzeugverwaltung,
 * der erste Bildschirm gehoert dem Geschehen. Bei einem Auto gibt es nichts zu waehlen -
 * dann ohne Sticky-Leiste. Desktop-Layout liegt weiterhin pro View (md:hidden).
 */
const props = defineProps<{
  /** Ausgewaehltes Auto (v-model). */
  modelValue: string | null
  cars: any[]
  carImageUrls: Record<string, string>
  teslaStatus: any
  smartcarStatus: SmartcarConnectionStatus | null
}>()

const emit = defineEmits<{ 'update:modelValue': [carId: string]; share: [car: Car] }>()

const { t } = useI18n()

const { isSmartcarCharging, isWallboxCharging } = useVehicleCharging(
  computed(() => props.cars),
  computed(() => props.smartcarStatus),
)
</script>

<template>
  <div
    v-if="cars.length > 0"
    :class="cars.length > 1
      ? 'sticky top-[calc(env(safe-area-inset-top)+3.5rem)] z-10 bg-white dark:bg-gray-800 -mx-4 px-4 py-1.5 mt-2 mb-4 border-b border-gray-100 dark:border-gray-700 shadow-sm'
      : 'mt-2 mb-3'"
  >
    <div class="flex gap-3 overflow-x-auto car-scroll-hide pb-1">
      <!-- Karte plus Teilen-Knopf als Geschwister: ein Button im Button waere ungueltig. -->
      <div
        v-for="car in cars"
        :key="car.id"
        :class="['relative', cars.length === 1 ? 'w-full' : 'flex-shrink-0']">
      <button
        v-if="car.id === modelValue"
        type="button"
        @click.stop="emit('share', car)"
        :aria-label="t('share_car.share_named', { name: [carDisplayName(car), car.trim].filter(Boolean).join(' ') })"
        class="absolute top-0 right-0 z-[1] min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-700 dark:text-gray-200">
        <ShareIcon class="w-5 h-5" />
      </button>
      <button
        @click="emit('update:modelValue', car.id)"
        :class="[
          cars.length === 1
            ? 'flex items-center rounded-2xl text-left transition w-full overflow-hidden'
            : 'flex items-center rounded-2xl text-left transition w-full min-w-[180px] max-w-[240px] overflow-hidden',
          // App-CI: das aktive Auto traegt die Kante, die anderen nur eine graue Linie.
          // Ob es gerade laedt, sagt das gruene Abzeichen in der Zeile, nicht der Rahmen.
          modelValue === car.id
            ? 'edge-card bg-white dark:bg-gray-800'
            : 'border-2 border-gray-200 bg-white hover:border-gray-400 dark:border-gray-700 dark:bg-gray-800/60 dark:hover:border-gray-500',
        ]" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
        <div class="flex-shrink-0 h-11 aspect-[4/3] bg-gray-100 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
          <img
            v-if="carImageUrls[car.id]"
            :src="carImageUrls[car.id]"
            :alt="car.model"
            class="w-full h-full object-cover" />
          <TruckIcon v-else class="w-6 h-6 text-gray-400" />
        </div>
        <div :class="['min-w-0 flex-1 px-3 py-1.5 flex flex-col justify-center', car.id === modelValue ? 'pr-11' : '']">
          <!-- Kompakt-Zeile: Name, Trim, Charging-Badges -->
          <div class="flex items-center gap-1.5 flex-wrap">
            <span class="font-semibold text-sm text-gray-800 dark:text-gray-200 whitespace-nowrap">{{ carDisplayName(car) }}</span>
            <span v-if="car.trim" class="text-xs text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
            <template v-if="car.brand?.toLowerCase() === 'tesla' && teslaStatus?.connected && (teslaStatus.carId === car.id || teslaStatus.carId === null)">
              <span v-if="teslaStatus.vehicleState === 'charging'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-50 dark:bg-green-400/10 text-green-800 dark:text-green-300 text-xs rounded-full font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>{{ t('dashboard.tesla_charging') }}
              </span>
              <span v-else-if="teslaStatus.vehicleState === 'online'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 dark:bg-white/10 text-gray-700 dark:text-gray-300 text-xs rounded-full font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-gray-900 dark:bg-gray-100"></span>{{ t('dashboard.tesla_online') }}
              </span>
              <span v-else-if="teslaStatus.vehicleState === 'asleep'"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400 text-xs rounded-full font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-gray-400"></span>{{ t('dashboard.tesla_sleeping') }}
              </span>
            </template>
            <span v-if="isSmartcarCharging(car)"
              class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-50 dark:bg-green-400/10 text-green-800 dark:text-green-300 text-xs rounded-full font-medium">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.smartcar_charging') }}
            </span>
            <span v-if="isWallboxCharging()"
              class="inline-flex items-center gap-1 px-1.5 py-0.5 bg-green-50 dark:bg-green-400/10 text-green-800 dark:text-green-300 text-xs rounded-full font-medium">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500 dark:bg-green-400 animate-pulse"></span>{{ t('dashboard.wallbox_charging') }}
            </span>
          </div>
        </div>
      </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Horizontalen Scrollbalken am Auto-Streifen ausblenden (Peek + Touch reichen). */
.car-scroll-hide { scrollbar-width: none; }
.car-scroll-hide::-webkit-scrollbar { display: none; }
</style>
