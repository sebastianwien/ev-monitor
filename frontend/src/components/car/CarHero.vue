<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon, CheckBadgeIcon, EllipsisHorizontalIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import LicensePlate from './LicensePlate.vue'
import SohPill from './SohPill.vue'
import ActionChip from '../shared/ActionChip.vue'
import type { Car } from '../../api/carService'
import type { TeslaConnectionStatus } from '../../api/teslaFleetService'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

/**
 * Kopf der Fahrzeugseite: Foto edge-to-edge (mobil 4:3, ab md 256 px hoch), darin Name, Variante,
 * Baujahr und Kennzeichen. Oben "Foto" und das Mehr-Menue, ab md zusaetzlich "Bearbeiten".
 * Die Chips stehen mobil als wischbare Zeile unter dem Foto, weil sie auf 390 px nicht ins
 * Bild passen und ueber einem Nutzerfoto keinen sicheren Kontrast haetten; ab md liegen sie
 * im Hero ueber dem dunklen Verlauf. Zuerst die Aktions-Chips (SoH, Bewertet, Geteilt),
 * danach die flachen Info-Chips: so sind die Aktionen mobil ohne Wischen sichtbar.
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
  /** Nutzer hat dieses Auto bewertet. */
  rated: boolean
  /** Aktueller Kilometerstand aus dem Backend; ohne Wert kein Chip. */
  odometerKm?: number | null
  /** Ø Verbrauch über die ganze Zeit aus /logs/statistics; ohne Wert kein Chip. */
  avgConsumptionKwhPer100km?: number | null
}>()
const emit = defineEmits<{ menu: []; edit: []; soh: []; rating: []; share: []; upload: [event: Event] }>()
const { t, n } = useI18n()
const { formatDistance, formatConsumption } = useLocaleFormat()

// Das Foto kommt nach dem Inhalt (Blob-Abruf): weich einblenden statt aufploppen.
const photoReady = ref(false)
watch(() => props.imageUrl, () => { photoReady.value = false })

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

interface Chip { key: string; label: string; title?: string; pulse?: boolean }

const infoChips = computed<Chip[]>(() => {
  const list: Chip[] = []
  if (props.odometerKm != null) list.push({ key: 'odometer', label: formatDistance(props.odometerKm), title: t('dashboard.car_card_odometer') })
  if (props.avgConsumptionKwhPer100km != null) {
    list.push({ key: 'consumption', label: `Ø ${formatConsumption(props.avgConsumptionKwhPer100km)}`, title: t('dashboard.metric_avg_consumption') })
  }
  list.push({ key: 'capacity', label: `${capacity.value ?? '?'} kWh` })
  if (props.car.powerKw) list.push({ key: 'power', label: `${props.car.powerKw} kW · ${Math.round(props.car.powerKw * 1.35962)} PS` })
  if (props.showActive) list.push({ key: 'active', label: t('cars.active') })
  if (teslaState.value === 'charging') list.push({ key: 'tesla', label: t('cars.tesla_charging'), pulse: true })
  else if (teslaState.value === 'online') list.push({ key: 'tesla', label: t('cars.tesla_online') })
  else if (teslaState.value === 'asleep') list.push({ key: 'tesla', label: t('cars.tesla_sleeping') })
  if (props.car.hasHeatPump) list.push({ key: 'heat', label: t('cars.heat_pump_badge') })
  if (props.car.isBusinessCar) list.push({ key: 'business', label: t('cars.business_car_label') })
  return list
})

// Info-Chips: solid, flach, dunkleres Indigo als die Aktions-Chips. Ueber dem Foto immer dunkel.
const infoOnPhoto = 'bg-indigo-900 text-indigo-100'
const infoOnPage = 'bg-indigo-50 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-100'
const chipBase = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 min-h-11 md:min-h-8 text-sm md:text-xs font-medium'
const overlayBtn = 'inline-flex items-center justify-center gap-1.5 min-h-[40px] min-w-[40px] rounded-full bg-black/45 text-white backdrop-blur px-3 text-sm font-medium hover:bg-black/60 transition'
</script>

<template>
  <section :data-testid="`car-hero-${car.id}`">
    <div
      class="relative overflow-hidden bg-gray-800 aspect-[4/3] md:aspect-auto md:h-64"
      :class="imageUrl ? '' : 'bg-gradient-to-br from-indigo-950 via-indigo-800 to-gray-900'">
      <img v-if="imageUrl" :key="imageUrl" :src="imageUrl" :alt="modelLabel"
        class="absolute inset-0 h-full w-full object-cover transition-opacity duration-300"
        :class="photoReady ? 'opacity-100' : 'opacity-0'" @load="photoReady = true" />
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
          <SohPill :soh-percent="sohPercent" size="lg" data-chip="soh" @click="emit('soh')" />
          <ActionChip v-if="rated" size="lg" data-chip="rated" :aria-label="t('cars.hero_rated_aria')" @click="emit('rating')">
            <CheckBadgeIcon class="h-4 w-4" aria-hidden="true" />{{ t('rating.badge_rated') }}
          </ActionChip>
          <ActionChip v-if="shared" size="lg" data-chip="shared" :aria-label="t('cars.hero_shared_aria')" @click="emit('share')">
            {{ t('cars.shared_badge') }}
          </ActionChip>
          <span v-for="chip in infoChips" :key="chip.key" :data-chip="chip.key" :title="chip.title" :class="[chipBase, infoOnPhoto]">
            <span v-if="chip.pulse" class="h-1.5 w-1.5 rounded-full bg-green-400 animate-pulse" aria-hidden="true" />{{ chip.label }}
          </span>
        </div>
      </div>
    </div>

    <!-- Chips mobil: eine wischbare Zeile unter dem Foto -->
    <div class="md:hidden flex gap-2 overflow-x-auto px-4 py-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden" data-testid="car-hero-chips-mobile">
      <SohPill :soh-percent="sohPercent" size="lg" data-chip="soh" @click="emit('soh')" />
      <ActionChip v-if="rated" size="lg" data-chip="rated" :aria-label="t('cars.hero_rated_aria')" @click="emit('rating')">
        <CheckBadgeIcon class="h-4 w-4" aria-hidden="true" />{{ t('rating.badge_rated') }}
      </ActionChip>
      <ActionChip v-if="shared" size="lg" data-chip="shared" :aria-label="t('cars.hero_shared_aria')" @click="emit('share')">
        {{ t('cars.shared_badge') }}
      </ActionChip>
      <span v-for="chip in infoChips" :key="chip.key" :data-chip="chip.key" :title="chip.title" :class="[chipBase, infoOnPage]">
        <span v-if="chip.pulse" class="h-1.5 w-1.5 rounded-full bg-green-500 animate-pulse" aria-hidden="true" />{{ chip.label }}
      </span>
    </div>
  </section>
</template>
