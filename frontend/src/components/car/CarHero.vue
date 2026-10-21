<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon, EllipsisHorizontalIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import LicensePlate from './LicensePlate.vue'
import SohPill from './SohPill.vue'
import type { Car } from '../../api/carService'
import type { TeslaConnectionStatus } from '../../api/teslaFleetService'

/**
 * Kopf der Fahrzeugseite: Foto edge-to-edge (mobil 4:3, ab md 21:9), darin Name, Variante,
 * Baujahr und Kennzeichen. Oben "Foto" und das Mehr-Menue, ab md zusaetzlich "Bearbeiten".
 * Die Chips (Akku, Leistung, Status) stehen mobil als wischbare Zeile unter dem Foto,
 * weil sechs Chips auf 390 px nicht ins Bild passen und ueber einem Nutzerfoto keinen
 * sicheren Kontrast haetten; ab md liegen sie im Hero ueber dem dunklen Verlauf.
 */
const props = defineProps<{
  car: Car
  modelLabel: string
  imageUrl: string | null
  uploading: boolean
  teslaStatus: TeslaConnectionStatus | null
  /** Primaerauto, nur relevant bei mehreren Autos. */
  showActive: boolean
  shared: boolean
}>()
const emit = defineEmits<{ menu: []; edit: []; soh: []; upload: [event: Event] }>()
const { t, n } = useI18n()

const sohPercent = computed(() =>
  props.car.batteryDegradationPercent ? Math.round(100 - Number(props.car.batteryDegradationPercent)) : null)

const capacityRaw = computed(() => props.car.effectiveBatteryCapacityKwh ?? props.car.customNetCapacityKwh ?? null)
const capacity = computed(() => capacityRaw.value === null ? null : n(capacityRaw.value, { maximumFractionDigits: 1 }))

const teslaState = computed(() => {
  const s = props.teslaStatus
  if (!s?.connected || props.car.brand?.toLowerCase() !== 'tesla') return null
  if (s.carId !== null && s.carId !== props.car.id) return null
  return s.vehicleState
})

type Tone = 'neutral' | 'green' | 'blue' | 'violet' | 'amber'
interface Chip { key: string; label: string; tone: Tone; pulse?: boolean }

const chips = computed<Chip[]>(() => {
  const list: Chip[] = []
  if (props.car.powerKw) list.push({ key: 'power', label: `${props.car.powerKw} kW · ${Math.round(props.car.powerKw * 1.35962)} PS`, tone: 'neutral' })
  if (props.showActive) list.push({ key: 'active', label: t('cars.active'), tone: 'green' })
  if (teslaState.value === 'charging') list.push({ key: 'tesla', label: t('cars.tesla_charging'), tone: 'green', pulse: true })
  else if (teslaState.value === 'online') list.push({ key: 'tesla', label: t('cars.tesla_online'), tone: 'blue' })
  else if (teslaState.value === 'asleep') list.push({ key: 'tesla', label: t('cars.tesla_sleeping'), tone: 'neutral' })
  if (props.car.hasHeatPump) list.push({ key: 'heat', label: t('cars.heat_pump_badge'), tone: 'blue' })
  if (props.car.isBusinessCar) list.push({ key: 'business', label: t('cars.business_car_label'), tone: 'violet' })
  if (props.shared) list.push({ key: 'shared', label: t('cars.shared_badge'), tone: 'neutral' })
  return list
})

const toneClass: Record<Tone, string> = {
  neutral: 'bg-gray-100 text-gray-700 dark:bg-white/10 dark:text-gray-200',
  green: 'bg-green-100 text-green-800 dark:bg-green-500/20 dark:text-green-300',
  blue: 'bg-blue-100 text-blue-800 dark:bg-blue-500/20 dark:text-blue-300',
  violet: 'bg-violet-100 text-violet-800 dark:bg-violet-500/20 dark:text-violet-300',
  amber: 'bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-300',
}
const chipBase = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-medium'
const overlayBtn = 'inline-flex items-center justify-center gap-1.5 min-h-[40px] min-w-[40px] rounded-full bg-black/45 text-white backdrop-blur px-3 text-sm font-medium hover:bg-black/60 transition'
</script>

<template>
  <section :data-testid="`car-hero-${car.id}`">
    <div
      class="relative overflow-hidden bg-gray-800 aspect-[4/3] md:aspect-[21/9]"
      :class="imageUrl ? '' : 'bg-gradient-to-br from-indigo-950 via-indigo-800 to-gray-900'">
      <img v-if="imageUrl" :src="imageUrl" :alt="modelLabel" class="absolute inset-0 h-full w-full object-cover" />
      <div class="absolute inset-0 bg-gradient-to-t from-gray-900/95 via-gray-900/30 to-transparent" aria-hidden="true" />

      <!-- Aktionen oben -->
      <div class="absolute left-3 right-3 top-3 flex items-center justify-between gap-2">
        <label :class="[overlayBtn, 'cursor-pointer focus-within:ring-2 focus-within:ring-white/80', uploading ? 'opacity-60 cursor-not-allowed' : '']">
          <CameraIcon class="h-5 w-5" aria-hidden="true" />
          <span>{{ uploading ? t('cars.uploading') : (imageUrl ? t('cars.hero_photo') : t('cars.upload_photo')) }}</span>
          <input type="file" accept="image/jpeg,image/png" class="sr-only" :disabled="uploading" @change="emit('upload', $event)" />
        </label>
        <div class="flex items-center gap-2">
          <button type="button" :class="[overlayBtn, 'hidden md:inline-flex']" @click="emit('edit')">
            <PencilSquareIcon class="h-5 w-5" aria-hidden="true" />{{ t('cars.edit_btn') }}
          </button>
          <button type="button" :class="overlayBtn" :aria-label="t('cars.hero_menu_aria')" data-testid="car-menu-btn" @click="emit('menu')">
            <EllipsisHorizontalIcon class="h-6 w-6" aria-hidden="true" />
          </button>
        </div>
      </div>

      <!-- Name und Kennzeichen unten -->
      <div class="absolute inset-x-4 bottom-4 md:inset-x-6 md:bottom-5">
        <div class="flex items-end justify-between gap-3">
          <h2 class="min-w-0 text-white">
            <span class="block text-[26px] md:text-[34px] font-extrabold leading-none tracking-tight">{{ modelLabel }}</span>
            <span class="mt-1 block text-sm md:text-base font-medium text-gray-200">
              <template v-if="car.trim">{{ car.trim }} · </template>{{ car.year }}
            </span>
          </h2>
          <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" class="shrink-0" />
        </div>
        <!-- Chips ab md im Bild -->
        <div class="mt-3 hidden md:flex flex-wrap gap-2">
          <span class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-medium bg-white/10 text-gray-100">
            {{ capacity ?? '?' }} kWh
            <SohPill :soh-percent="sohPercent" @click="emit('soh')" />
          </span>
          <span v-for="chip in chips" :key="chip.key" :class="[chipBase, toneClass[chip.tone]]">
            <span v-if="chip.pulse" class="h-1.5 w-1.5 rounded-full bg-green-500 animate-pulse" aria-hidden="true" />{{ chip.label }}
          </span>
        </div>
      </div>
    </div>

    <!-- Chips mobil: eine wischbare Zeile unter dem Foto -->
    <div class="md:hidden flex gap-2 overflow-x-auto px-4 py-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      <span :class="[chipBase, toneClass.neutral]">
        {{ capacity ?? '?' }} kWh
        <SohPill :soh-percent="sohPercent" @click="emit('soh')" />
      </span>
      <span v-for="chip in chips" :key="chip.key" :class="[chipBase, toneClass[chip.tone]]">
        <span v-if="chip.pulse" class="h-1.5 w-1.5 rounded-full bg-green-500 animate-pulse" aria-hidden="true" />{{ chip.label }}
      </span>
    </div>
  </section>
</template>
