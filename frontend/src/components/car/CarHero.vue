<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CameraIcon, CheckBadgeIcon, ChevronRightIcon, EllipsisHorizontalIcon, PencilSquareIcon } from '@heroicons/vue/24/outline'
import LicensePlate from './LicensePlate.vue'
import SohPill from './SohPill.vue'
import ActionChip from '../shared/ActionChip.vue'
import type { Car } from '../../api/carService'
import type { TeslaConnectionStatus } from '../../api/teslaFleetService'
import { useLocaleFormat } from '../../composables/useLocaleFormat'

/**
 * Kopf der Fahrzeugseite. Oben "Foto" und das Mehr-Menue, ab md zusaetzlich "Bearbeiten".
 *
 * Mobil: Das Foto blendet nach unten per Maske in die Seite aus (unabhaengig von Bild und
 * Theme, keine Farbe muss passen). Name, Kennzeichen und eine leise Technik-Zeile stehen im
 * ausgeblendeten Bereich in Textfarbe, darunter die Kennzahlen-Leiste (Kilometerstand,
 * Ø Verbrauch, Akkuzustand; nur der Akkuzustand ist antippbar). "Bewertet" und "Geteilt"
 * haben mobil eigene Karten weiter unten. Der Tesla-Status sitzt als Punkt oben im Foto.
 *
 * Ab md: Foto 256 px hoch mit dunklem Verlauf, Name und alle Chips weiss im Bild, zuerst die
 * Aktions-Chips (SoH, Bewertet, Geteilt), danach die flachen Info-Chips.
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
const { formatDistance, formatConsumption, distanceUnitLabel, consumptionUnitLabel } = useLocaleFormat()

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

/** Mobile Technik-Zeile: dieselben Infos wie die Chips, ohne Messwerte und Tesla-Status. */
const specLine = computed(() => infoChips.value
  .filter(c => !['odometer', 'consumption', 'tesla'].includes(c.key))
  .map(c => c.label)
  .join(' · '))

interface Stat { key: 'odometer' | 'consumption'; value: string; unit: string; label: string }
/** Mobile Kennzahlen ohne Akkuzustand (der ist ein Knopf und steht immer am Ende). */
const stats = computed<Stat[]>(() => {
  const list: Stat[] = []
  if (props.odometerKm != null) {
    list.push({ key: 'odometer', value: formatDistance(props.odometerKm, { showUnit: false }), unit: distanceUnitLabel(), label: t('dashboard.car_card_odometer') })
  }
  if (props.avgConsumptionKwhPer100km != null) {
    list.push({ key: 'consumption', value: formatConsumption(props.avgConsumptionKwhPer100km, { showUnit: false }), unit: consumptionUnitLabel(), label: t('dashboard.metric_avg_consumption') })
  }
  return list
})
const teslaDot = computed(() => infoChips.value.find(c => c.key === 'tesla') ?? null)

// Info-Chips: flach und neutral, ohne Rand - klickbar sind nur die Aktions-Chips. Ueber dem Foto
// schwarz-transparent wie die Foto-Knoepfe, damit sie hinter das Bild zuruecktreten. Keine Farbe:
// ein Messwert (km, kWh) bekommt kein Urteil.
const infoOnPhoto = 'bg-black/45 text-white backdrop-blur'
const chipBase = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 min-h-11 md:min-h-8 text-sm md:text-xs font-medium'
// Maske statt Verlauf: das Foto selbst wird transparent, darunter liegt die Seitenflaeche.
const photoMask = '[mask-image:linear-gradient(to_bottom,black_50%,transparent_92%)] md:[mask-image:none]'
// Kennzahl in drei Zeilen (Zahl, Einheit, Label): "kWh/100km" passt auf 390 px nicht neben die Zahl,
// gleicher Aufbau in jeder Spalte haelt Zahlen, Einheiten und Labels auf einer Linie.
const statUnit = 'mt-1 text-[13px] font-semibold leading-tight text-gray-500 dark:text-gray-400'
const statLabel = 'text-[11.5px] font-semibold uppercase leading-tight tracking-wide text-gray-500 dark:text-gray-400'
const overlayBtn = 'inline-flex items-center justify-center gap-1.5 min-h-[40px] min-w-[40px] rounded-full bg-black/45 text-white backdrop-blur px-3 text-sm font-medium hover:bg-black/60 transition'
</script>

<template>
  <section class="relative" :data-testid="`car-hero-${car.id}`">
    <div class="relative aspect-[4/3] md:aspect-auto md:h-64 md:overflow-hidden">
      <div class="absolute inset-0 bg-gray-200 dark:bg-gray-800 md:bg-gray-800" :class="photoMask" aria-hidden="true">
        <img v-if="imageUrl" :key="imageUrl" :src="imageUrl" :alt="modelLabel"
          class="absolute inset-0 h-full w-full object-cover transition-opacity duration-300"
          :class="photoReady ? 'opacity-100' : 'opacity-0'" @load="photoReady = true" />
      </div>
      <div class="absolute inset-0 hidden md:block bg-gradient-to-t from-gray-900/95 via-gray-900/30 to-transparent" aria-hidden="true" />

      <!-- Aktionen oben -->
      <div class="absolute left-3 right-3 top-3 flex items-center justify-between gap-2">
        <label :class="[overlayBtn, 'cursor-pointer focus-within:ring-2 focus-within:ring-white/80', uploading ? 'opacity-60 cursor-not-allowed' : '']">
          <CameraIcon class="h-5 w-5" aria-hidden="true" />
          <span>{{ uploading ? t('cars.uploading') : (imageUrl ? t('cars.hero_photo') : t('cars.upload_photo')) }}</span>
          <input type="file" accept="image/jpeg,image/png" class="sr-only" :disabled="uploading" @change="emit('upload', $event)" />
        </label>
        <div class="flex items-center gap-2">
          <span v-if="teslaDot" :class="[overlayBtn, 'md:hidden pointer-events-none']" data-testid="car-hero-tesla-mobile">
            <span class="h-1.5 w-1.5 rounded-full" :class="teslaDot.pulse ? 'bg-green-400 animate-pulse' : 'bg-white/70'" aria-hidden="true" />{{ teslaDot.label }}
          </span>
          <button type="button" :class="[overlayBtn, 'hidden md:inline-flex']" @click="emit('edit')">
            <PencilSquareIcon class="h-5 w-5" aria-hidden="true" />{{ t('cars.edit_btn') }}
          </button>
          <button type="button" :class="overlayBtn" :aria-label="t('cars.hero_menu_aria')" data-testid="car-menu-btn" @click="emit('menu')">
            <EllipsisHorizontalIcon class="h-6 w-6" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>

    <!-- Name und Kennzeichen: mobil im ausgeblendeten Fotobereich in Textfarbe, ab md weiss im Bild -->
    <div class="relative -mt-12 px-4 md:absolute md:inset-x-6 md:bottom-5 md:mt-0 md:px-0">
      <div class="flex items-end justify-between gap-3">
        <h2 class="min-w-0 text-gray-900 dark:text-gray-100 md:text-white md:dark:text-white">
          <span class="block text-[26px] md:text-[34px] font-extrabold leading-none tracking-tight">{{ modelLabel }}</span>
          <span class="mt-1 block text-sm md:text-base font-medium text-gray-600 dark:text-gray-300 md:text-gray-200 md:dark:text-gray-200">
            <template v-if="car.trim">{{ car.trim }} · </template>{{ car.year }}
          </span>
        </h2>
        <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" class="shrink-0" />
      </div>
      <p v-if="specLine" class="mt-1.5 text-sm text-gray-500 dark:text-gray-400 md:hidden" data-testid="car-hero-specs-mobile">{{ specLine }}</p>
      <!-- Ab md: Chips im Bild, zuerst die Aktionen -->
      <div class="mt-3 hidden md:flex flex-wrap gap-2" data-testid="car-hero-chips">
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

      <!-- Kennzahlen-Leiste: Messwerte gleichrangig, nur der Akkuzustand oeffnet etwas -->
      <div class="mt-4 grid auto-cols-fr grid-flow-col md:hidden divide-x divide-gray-200 border-y border-gray-200 dark:divide-white/10 dark:border-white/10" data-testid="car-hero-stats-mobile">
        <dl v-for="s in stats" :key="s.key" :data-stat="s.key" class="flex min-w-0 flex-col-reverse justify-between gap-1 px-3 py-3 first:pl-0">
          <dt :class="statLabel">{{ s.label }}</dt>
          <dd class="flex flex-col text-gray-900 dark:text-gray-100">
            <span class="text-[24px] font-extrabold leading-none tracking-tight tabular-nums">{{ s.value }}</span>
            <span :class="statUnit">{{ s.unit }}</span>
          </dd>
        </dl>
        <button type="button" data-stat="soh" class="flex min-h-11 min-w-0 items-center gap-1 px-3 py-3 text-left first:pl-0 active:bg-gray-50 dark:active:bg-white/5"
          :aria-label="sohPercent != null ? t('soh.open_aria', { soh: Math.round(sohPercent) }) : t('soh.open_aria_unknown')" @click="emit('soh')">
          <span class="flex min-w-0 flex-1 flex-col justify-between gap-1 self-stretch">
            <span v-if="sohPercent != null" class="flex flex-col text-gray-900 dark:text-gray-100">
              <span class="text-[24px] font-extrabold leading-none tracking-tight tabular-nums">~{{ Math.round(sohPercent) }}</span>
              <span :class="statUnit">%</span>
            </span>
            <span v-else class="text-[15px] font-bold leading-6 text-gray-900 dark:text-gray-100">{{ t('soh.badge_none') }}</span>
            <span :class="statLabel">{{ t('cars.hero_stat_soh') }}</span>
          </span>
          <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
        </button>
      </div>
    </div>
  </section>
</template>
