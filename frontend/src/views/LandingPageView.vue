<script setup lang="ts">
import { ref, onMounted, computed, type Component } from 'vue'
import { useHead } from '@unhead/vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { analytics } from '../services/analytics'
import SupportPopover from '../components/settings/SupportPopover.vue'
import PublicNav from '../components/shared/PublicNav.vue'
import GridRippleBackground from '../components/shared/GridRippleBackground.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { getTopModels, getMostEfficientModels, getLongestRangeModels, getPlatformStats, type TopModelPreview } from '../api/publicModelService'
import { selectSuperlatives } from '../composables/superlatives'
import { formatKm } from '../utils/formatKm'
import {
  LockClosedIcon,
  UsersIcon,
  ArrowRightIcon,
  BoltIcon,
  ArrowDownTrayIcon,
  ShieldCheckIcon,
  ServerStackIcon,
  MapPinIcon,
  DocumentCheckIcon,
  ArrowTopRightOnSquareIcon,
  FireIcon,
  MoonIcon,
  ArrowLongRightIcon,
  CodeBracketIcon,
  ArrowPathIcon,
  InformationCircleIcon,
} from '@heroicons/vue/24/outline'
import CommunityPulseSection from '../components/shared/CommunityPulseSection.vue'
import ImageLightbox from '../components/shared/ImageLightbox.vue'
import { useLightbox } from '../composables/useLightbox'
import CommunityLeaderboard from '../components/shared/CommunityLeaderboard.vue'
import PublicModelCard from '../components/shared/PublicModelCard.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { formatConsumption, formatNumber, consumptionUnitLabel, formatDistance, distanceUnitLabel } = useLocaleFormat()
const isEn = computed(() => route.path.startsWith('/en'))
const modelsUrl = computed(() => isEn.value ? '/en/models' : '/modelle')
const pricingUrl = computed(() => isEn.value ? '/pricing' : '/preise')

// Klickbare Screenshots: pro Sektion ein Bild-Set, das in der Lightbox durchblaettert wird.
const { open: openLightbox } = useLightbox()
const dashboardShot = () => [
  { src: '/screenshots/dashboard-light.jpg', alt: t('landing.app_preview.dashboard_title') },
]
const productShots = () => [
  { src: '/screenshots/dashboard-light.jpg', alt: t('landing.app_preview.dashboard_title') },
  { src: '/screenshots/logfeed-light.jpg', alt: t('landing.app_preview.logfeed_title') },
]
const galleryShots = () => [
  { src: '/screenshots/map-light.jpg', alt: t('landing.app_preview.map_title') },
  { src: '/screenshots/charts-light.jpg', alt: t('landing.app_preview.charts_title') },
]
const darkShots = () => [
  { src: '/screenshots/dashboard-dark.jpg', alt: t('landing.app_preview.dashboard_title') },
  { src: '/screenshots/charts-dark.jpg', alt: t('landing.app_preview.charts_title') },
  { src: '/screenshots/map-dark.jpg', alt: t('landing.app_preview.map_title') },
]

const topModels = ref<TopModelPreview[]>([])
const nextModels = ref<TopModelPreview[]>([])
const efficientModels = ref<TopModelPreview[]>([])
const rangeModels = ref<TopModelPreview[]>([])
const loading = ref(true)
const displayModels = ref(0)
const displayUsers = ref(0)
const displayTrips = ref(0)
const displayTotalKm = ref(0)
// Stabile (nicht animierte) Fahrer-Zahl fuer den Fliesstext im Community-Intro -
// eine hochzaehlende Zahl mitten im Satz waere unruhig.
const communityDrivers = ref(0)

// Round trips to nearest 10 for cleaner display with "+"
const displayTripsRounded = computed(() => Math.floor(displayTrips.value / 10) * 10)
const displayTotalKmRounded = computed(() => formatKm(displayTotalKm.value))

// Trust panel: community stats (with explanatory labels) + trust signals
const trustStats = computed(() => [
  { value: `${displayTotalKmRounded.value} km`, label: t('landing.hero.total_km_label') },
  { value: `${displayModels.value} ${t('landing.hero.models_unit')}`, label: t('landing.hero.models_label') },
  { value: `${displayUsers.value} ${t('landing.hero.drivers_unit')}`, label: t('landing.hero.drivers_label') },
])
const trustSignals = [
  { icon: CodeBracketIcon, key: 'landing.features.open_source_title', href: 'https://github.com/sebastianwien/ev-monitor', external: true, action: '' },
  { icon: ArrowDownTrayIcon, key: 'landing.features.auto_import_badge', href: '', external: false, action: 'import' },
  { icon: ShieldCheckIcon, key: 'landing.features.privacy_badge', href: '', external: false, action: 'privacy' },
  { icon: UsersIcon, key: 'landing.features.community_badge', href: '', external: false, action: '' },
]

interface SuperlativeItem {
  rank: number
  name: string
  href: string
  value: string
  unit: string
  meta: string
}
interface SuperlativeCategory {
  key: string
  label: string
  sub: string
  icon: Component
  items: SuperlativeItem[]
}

// The three labelled superlatives that replace the (unexplained) hero model cards.
// Each card now answers "why is this model here?": most efficient, longest real
// range, most tracked. Formatting/units done here so the template stays declarative.
const superlativeCategories = computed<SuperlativeCategory[]>(() => {
  const board = selectSuperlatives(efficientModels.value, rangeModels.value, topModels.value)
  const href = (m: TopModelPreview) => `${modelsUrl.value}/${m.brandDisplayName}/${m.modelUrlSlug}`
  const dataMeta = (m: TopModelPreview) => t('landing.superlatives.meta_datapoints', { n: formatNumber(m.logCount) })

  return [
    {
      key: 'efficiency',
      label: t('landing.superlatives.efficiency_label'),
      sub: t('landing.superlatives.efficiency_sub'),
      icon: BoltIcon,
      items: board.efficiency.map((m, i) => ({
        rank: i + 1,
        name: m.modelDisplayName,
        href: href(m),
        value: formatConsumption(m.avgConsumptionKwhPer100km, { showUnit: false }),
        unit: consumptionUnitLabel(),
        meta: dataMeta(m),
      })),
    },
    {
      key: 'range',
      label: t('landing.superlatives.range_label'),
      sub: t('landing.superlatives.range_sub'),
      icon: ArrowLongRightIcon,
      items: board.range.map((m, i) => ({
        rank: i + 1,
        name: m.modelDisplayName,
        href: href(m),
        value: m.realRangeKm != null ? formatDistance(m.realRangeKm, { showUnit: false, round: true }) : '–',
        unit: distanceUnitLabel(),
        meta: dataMeta(m),
      })),
    },
    {
      key: 'popular',
      label: t('landing.superlatives.popular_label'),
      sub: t('landing.superlatives.popular_sub'),
      icon: FireIcon,
      items: board.popular.map((m, i) => ({
        rank: i + 1,
        name: m.modelDisplayName,
        href: href(m),
        value: formatNumber(m.logCount),
        unit: t('landing.superlatives.unit_charges'),
        meta: m.avgConsumptionKwhPer100km != null
          ? formatConsumption(m.avgConsumptionKwhPer100km, { showUnit: true })
          : dataMeta(m),
      })),
    },
  ].filter(cat => cat.items.length > 0)
})

const hasSuperlatives = computed(() => superlativeCategories.value.length > 0)

// Leaderboard-Sektion erst zeigen, wenn das Live-Widget echte Daten geliefert hat.
// Bei API-Fehler oder leerem Monat bleibt sie aus - kein Fehler-/Leer-State auf der Landing.
const leaderboardReady = ref(false)
// In den ersten 7 Tagen ist der laufende Monat noch zu duenn - dann den Vormonats-Endstand zeigen.
const leaderboardMonth = new Date().getDate() <= 7 ? 'previous' : 'current'

// desc nach dem ersten " - " umbrechen (Trenner ist in allen Locales identisch); single source of truth bleibt der eine i18n-Key.
const leaderboardDescParts = computed(() => {
  const text = t('landing.leaderboard.desc')
  const idx = text.indexOf(' - ')
  return idx === -1 ? [text] : [text.slice(0, idx + 2), text.slice(idx + 3)]
})

function animateCount(target: number | undefined | null, setter: (v: number) => void, duration = 1400) {
  if (target == null || isNaN(target)) return
  const start = Date.now()
  const tick = () => {
    const progress = Math.min((Date.now() - start) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic
    setter(Math.round(eased * target))
    if (progress < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

useHead(computed(() => ({
  title: isEn.value
    ? 'EV Monitor – Real Electric Car Consumption & Charging Costs'
    : 'EV Monitor – Echter Stromverbrauch & Ladekosten von Elektroautos',
  meta: [
    {
      name: 'description',
      content: isEn.value
        ? 'Track real EV charging costs and consumption. Compare WLTP vs. real-world data for Tesla, VW ID, Hyundai Ioniq, BMW i4 and more. Community-driven, free and private.'
        : 'Echte Ladekosten und Verbrauch von Elektroautos tracken. WLTP vs. Realität für Tesla, VW ID, Hyundai Ioniq, BMW i4 und viele mehr. Kostenlos, anonym, Open Source.'
    },
    { name: 'robots', content: 'index, follow' },
    { property: 'og:type', content: 'website' },
    { property: 'og:url', content: isEn.value ? 'https://ev-monitor.net/en' : 'https://ev-monitor.net/' },
    {
      property: 'og:title',
      content: isEn.value
        ? 'EV Monitor – Real Electric Car Consumption & Charging Costs'
        : 'EV Monitor – Echter Stromverbrauch & Ladekosten von Elektroautos'
    },
    {
      property: 'og:description',
      content: isEn.value
        ? 'Track real EV charging costs and consumption. Compare WLTP vs. real-world data for Tesla, VW ID, Hyundai Ioniq and more.'
        : 'Echte Ladekosten und Verbrauch tracken. WLTP vs. Realität für Tesla, VW ID, Hyundai Ioniq und mehr.'
    },
    { property: 'og:locale', content: isEn.value ? 'en_GB' : 'de_DE' },
    { name: 'twitter:card', content: 'summary_large_image' },
    {
      name: 'twitter:title',
      content: isEn.value
        ? 'EV Monitor – Real Electric Car Consumption & Charging Costs'
        : 'EV Monitor – Echter Stromverbrauch & Ladekosten von Elektroautos'
    },
    {
      name: 'twitter:description',
      content: isEn.value
        ? 'Track real EV charging costs and consumption. Compare WLTP vs. real-world data for Tesla, VW ID, Hyundai Ioniq and more.'
        : 'Echte Ladekosten und Verbrauch tracken. WLTP vs. Realität für Tesla, VW ID, Hyundai Ioniq und mehr.'
    },
  ],
  link: [
    { rel: 'canonical', href: isEn.value ? 'https://ev-monitor.net/en' : 'https://ev-monitor.net/' },
    { rel: 'alternate', hreflang: 'de', href: 'https://ev-monitor.net/' },
    { rel: 'alternate', hreflang: 'en', href: 'https://ev-monitor.net/en' },
    { rel: 'alternate', hreflang: 'x-default', href: 'https://ev-monitor.net/' },
  ]
})))

onMounted(async () => {
  analytics.track('landing_page_viewed')

  // Load platform stats and animate counters
  try {
    const stats = await getPlatformStats()
    communityDrivers.value = stats.userCount
    animateCount(stats.modelCount, v => displayModels.value = v)
    animateCount(stats.userCount, v => displayUsers.value = v)
    animateCount(stats.tripCount, v => displayTrips.value = v)
    animateCount(stats.totalKm, v => displayTotalKm.value = v)
  } catch {
    // fallback: leave at 0
  }

  // Load top models with community data for SEO — single request instead of 12
  try {
    const [models, efficient, range] = await Promise.all([
      getTopModels(8),
      getMostEfficientModels(10),
      getLongestRangeModels(3),
    ])
    topModels.value = models.slice(0, 4)
    nextModels.value = models.slice(4, 8)
    efficientModels.value = efficient
    rangeModels.value = range
  } catch (error) {
    console.error('Failed to load model previews:', error)
  } finally {
    loading.value = false
  }
})

const returnScrollY = ref<number | null>(null)

const scrollToImportHub = () => {
  returnScrollY.value = window.scrollY
  document.getElementById('import-hub')?.scrollIntoView({ behavior: 'smooth' })
}

const scrollToPrivacy = () => {
  document.getElementById('privacy-section')?.scrollIntoView({ behavior: 'smooth' })
}

const scrollBack = () => {
  if (returnScrollY.value === null) return
  window.scrollTo({ top: returnScrollY.value, behavior: 'smooth' })
  returnScrollY.value = null
}

const goToRegister = (source: string = 'unknown') => {
  analytics.track('cta_register_clicked', { source })
  router.push('/register')
}

const demoLoading = ref(false)
const demoLogin = async (source: 'hero' | 'models_section' | 'dashboard_preview' | 'logfeed_preview' | 'map_gallery' | 'charts_gallery' = 'hero') => {
  demoLoading.value = true
  analytics.trackDemoLoginClicked(source)
  try {
    const response = await import('../api/axios').then(m => m.default.post('/auth/demo-login'))
    authStore.setToken(response.data.token)
    sessionStorage.setItem('ev_demo_entry_url', window.location.pathname)
    router.push('/dashboard')
  } catch {
    router.push('/login')
  } finally {
    demoLoading.value = false
  }
}
</script>

<template>
  <div class="sl-grid isolate min-h-screen bg-white dark:bg-gray-950 overflow-x-clip">
    <!-- Maus-reaktives Gitter: zeichnet nur bewegte Punkte ueber das statische .sl-grid -->
    <GridRippleBackground />

    <!-- Navbar -->
    <PublicNav />

    <!-- Bild-Lightbox (Teleport nach body); reagiert auf openLightbox() aus den Sektionen -->
    <ImageLightbox />

    <!-- Hero Section -->
    <section class="pt-8 pb-6 sm:pt-12 sm:pb-8">
      <div class="max-w-4xl mx-auto text-center px-6 sm:px-8 lg:px-12">
        <h1 class="text-3xl sm:text-5xl lg:text-6xl font-bold text-gray-900 dark:text-gray-100 leading-tight mb-4">
          {{ t('landing.hero.title') }}
        </h1>
        <p class="text-lg sm:text-2xl text-gray-700 dark:text-gray-300 mb-8 max-w-2xl mx-auto break-words leading-relaxed">
          {{ t('landing.hero.subtitle') }}
        </p>

        <!-- Hero product visual: ein einzelnes, grosses Dashboard-Bild, das den Hero
             ankert (fuellt die Textspalte, damit es nicht allein rumfloatet). -->
        <div class="mt-8 sm:mt-10 mb-8 sm:mb-10 w-full max-w-3xl mx-auto relative">
          <div class="rounded-xl border-2 border-gray-900 dark:border-gray-100 shadow-[8px_8px_0_0_#111827] dark:shadow-[8px_8px_0_0_#e5e7eb] overflow-hidden relative transition duration-300 ease-out hover:scale-[1.06]">
            <img
              src="/screenshots/dashboard-light.jpg"
              :alt="t('landing.app_preview.dashboard_title')"
              class="w-full block h-auto cursor-zoom-in"
              width="885"
              height="950"
              fetchpriority="high"
              role="button"
              tabindex="0"
              @click="openLightbox(dashboardShot(), 0)"
              @keydown.enter.space.prevent="openLightbox(dashboardShot(), 0)"
            />
            <div class="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-b from-transparent to-white dark:to-gray-900 pointer-events-none"></div>
          </div>
          <div class="absolute -top-3 -right-3 bg-green-500 text-white text-sm font-bold px-4 py-2 rounded-full shadow-[4px_4px_0_0_#111827] border-2 border-gray-900 hidden sm:block">
            {{ t('landing.app_preview.chip_co2') }}
          </div>
          <div class="absolute -bottom-3 -left-3 bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 text-sm font-bold px-4 py-2 rounded-full shadow-[4px_4px_0_0_#111827] border-2 border-gray-900 hidden sm:block">
            {{ t('landing.app_preview.chip_costs') }}
          </div>
        </div>

        <!-- CTAs sind jetzt Teil des "Echte Daten"-Blocks unten (Abschluss-CTA),
             damit sie nicht losgeloest ueber der Sektion schweben. -->

        <!-- Community-USP: eigener Hintergrund-Band wie die anderen Sektionen; bricht per
             Full-Bleed aus dem zentrierten Hero-Container aus (Root hat overflow-x-clip).
             Fahrer-Zahl nur zeigen, wenn die Stats geladen sind. -->
        <div class="relative left-1/2 -translate-x-1/2 w-screen bg-gray-50 dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 py-10 sm:py-14 mb-10 sm:mb-12">
          <div class="max-w-3xl mx-auto px-6 sm:px-8">
            <template v-if="communityDrivers > 0">
              <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-3">{{ t('landing.community_intro.heading') }}</h2>
              <p class="text-lg text-gray-600 dark:text-gray-300 max-w-2xl mx-auto leading-relaxed">{{ t('landing.community_intro.text', { drivers: formatNumber(communityDrivers) }) }}</p>
            </template>

            <!-- Ein Block: Kennzahlen + Trust-Signale in EINER Karte mit einem gemeinsamen
                 gruenen Versatz-Schatten. Kennzahlen oben, Trennlinie, Signale als flache
                 klickbare Zeilen darunter. -->
            <div class="mt-8 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-xl p-6 sm:p-8 shadow-[5px_5px_0_0_#15803d] dark:shadow-[5px_5px_0_0_#22c55e]">
              <template v-if="communityDrivers > 0">
                <!-- Kennzahlen: Mobile gestapelt (divide-y), ab sm 3 Spalten -->
                <div class="grid grid-cols-1 sm:grid-cols-3 divide-y sm:divide-y-0 sm:divide-x divide-gray-900/10 dark:divide-white/10">
                  <div v-for="s in trustStats" :key="s.label" class="py-4 first:pt-0 sm:py-0 px-2 sm:px-4 text-center">
                    <p class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 tabular-nums">{{ s.value }}</p>
                    <p class="text-sm sm:text-base text-gray-600 dark:text-gray-300 mt-1.5 leading-snug">{{ s.label }}</p>
                  </div>
                </div>
                <div class="my-6 h-px bg-gray-900/10 dark:bg-white/10"></div>
              </template>

              <!-- Trust-Signale: flache Zeilen im selben Block; no-press haelt die globale
                   Button-3D-Schatten-Regel fern, damit der Block-Schatten allein wirkt. -->
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1">
                <component
                  :is="sig.href ? 'a' : sig.action ? 'button' : 'div'"
                  v-for="sig in trustSignals" :key="sig.key"
                  :href="sig.href || undefined"
                  :target="sig.external ? '_blank' : undefined"
                  :rel="sig.external ? 'noopener noreferrer' : undefined"
                  @click="sig.action === 'import' ? scrollToImportHub() : sig.action === 'privacy' ? scrollToPrivacy() : undefined"
                  class="no-press flex w-full items-center gap-3 text-left rounded-lg px-2 py-2.5 transition-colors"
                  :class="(sig.href || sig.action) ? 'cursor-pointer hover:bg-green-50 dark:hover:bg-green-900/20' : ''"
                >
                  <span class="shrink-0 flex items-center justify-center h-10 w-10 rounded-lg bg-green-100 dark:bg-green-900/40">
                    <component :is="sig.icon" class="h-6 w-6 text-green-700 dark:text-green-400" />
                  </span>
                  <span class="flex-1 text-base font-bold leading-snug text-gray-900 dark:text-gray-100">{{ t(sig.key) }}</span>
                  <ArrowTopRightOnSquareIcon v-if="sig.external" class="h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" />
                </component>
              </div>

              <!-- Abschluss-CTA im selben Block: Nudge + Haupt-Handlungsaufrufe, damit die
                   Buttons nicht mehr losgeloest ueber der Sektion schweben. -->
              <div class="mt-6 pt-6 border-t border-gray-900/10 dark:border-white/10">
                <p v-if="communityDrivers > 0" class="mb-4 text-center text-base font-medium text-gray-700 dark:text-gray-300">
                  {{ t('landing.hero.social_proof', { drivers: formatNumber(communityDrivers) }) }}
                </p>
                <div class="flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4">
                  <button
                    @click="goToRegister('hero_primary')"
                    class="btn-3d cta-shadow w-full sm:w-auto cursor-pointer bg-green-600 text-white border-2 border-gray-900 dark:border-gray-100 px-6 py-3 sm:px-8 sm:py-4 rounded-sm text-base sm:text-lg font-semibold hover:bg-green-700 inline-flex items-center justify-center gap-2 transition"
                  >
                    {{ t('landing.hero.register_button') }}
                  </button>
                  <button
                    @click="demoLogin('hero')"
                    :disabled="demoLoading"
                    class="btn-3d cta-shadow w-full sm:w-auto cursor-pointer bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 text-gray-800 dark:text-gray-100 px-6 py-3 sm:px-8 sm:py-4 rounded-sm text-base sm:text-lg font-semibold hover:text-green-700 dark:hover:text-green-400 disabled:opacity-50 inline-flex items-center justify-center transition"
                  >
                    {{ demoLoading ? t('landing.hero.loading_button') : t('landing.hero.demo_button') }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Community-Bestenliste: now social proof below the product, no longer the first thing a visitor sees -->
        <div v-if="hasSuperlatives" class="sl-board text-left px-5 py-7 mb-6">
          <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 text-center mb-2">{{ t('landing.superlatives.eyebrow') }}</h2>
          <i18n-t keypath="landing.superlatives.from_trips" tag="p" class="text-center text-base sm:text-lg text-gray-500 dark:text-gray-400 font-semibold mb-3 flex flex-wrap items-baseline justify-center gap-x-2">
            <template #n>
              <span class="sl-num text-3xl sm:text-4xl font-extrabold text-green-700 dark:text-green-400 tabular-nums">{{ formatNumber(displayTripsRounded) }}</span>
            </template>
          </i18n-t>
          <p class="mb-6 text-center text-base sm:text-lg text-gray-600 dark:text-gray-300 max-w-xl mx-auto leading-relaxed">{{ t('landing.superlatives.subline') }}</p>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
            <div
              v-for="cat in superlativeCategories"
              :key="cat.key"
              class="sl-panel bg-white dark:bg-gray-800 rounded-xl border-2 border-gray-900 dark:border-gray-100 p-5"
            >
              <div class="flex items-center gap-2.5 text-green-700 dark:text-green-400">
                <component :is="cat.icon" class="h-6 w-6" />
                <span class="font-extrabold uppercase tracking-wide text-base">{{ cat.label }}</span>
              </div>
              <div class="text-xs text-gray-400 font-semibold mb-3 ml-[2.1rem] -mt-0.5">{{ cat.sub }}</div>
              <div class="h-0.5 bg-green-600 rounded mb-1"></div>
              <a
                v-for="(item, i) in cat.items"
                :key="`${cat.key}-${item.name}-${i}`"
                :href="item.href"
                class="sl-rise sl-row mt-3 flex items-center gap-3.5 p-3.5 rounded-lg border-2 border-gray-900/10 dark:border-white/10 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700/40"
                :style="{ animationDelay: `${i * 60}ms` }"
              >
                <span
                  v-if="item.rank === 1"
                  class="sl-gold-chip flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center text-xl font-extrabold"
                >1</span>
                <span
                  v-else
                  class="flex-shrink-0 w-10 h-10 rounded-lg border-2 border-gray-900/10 dark:border-white/15 flex items-center justify-center text-lg font-extrabold text-gray-400"
                >{{ item.rank }}</span>
                <div class="flex-1 min-w-0">
                  <div
                    class="font-bold text-base leading-snug line-clamp-2"
                    :class="item.rank === 1 ? 'text-gray-900 dark:text-gray-100' : 'text-gray-700 dark:text-gray-300'"
                  >{{ item.name }}</div>
                  <div class="sl-num text-xs text-gray-400 font-semibold mt-0.5">{{ item.meta }}</div>
                </div>
                <div class="text-right leading-none flex-shrink-0">
                  <span
                    class="sl-num text-2xl font-extrabold whitespace-nowrap"
                    :class="item.rank === 1 ? 'sl-gold' : 'text-gray-900 dark:text-gray-100'"
                  >{{ item.value }}</span>
                  <div class="text-[11px] text-gray-400 font-semibold mt-1">{{ item.unit }}</div>
                </div>
              </a>
            </div>
          </div>
        </div>

        <!-- Standalone CTA directly under the superlative cards -->
        <div v-if="hasSuperlatives" class="mb-10 sm:mb-12 flex justify-center">
          <router-link
            :to="modelsUrl"
            @click="analytics.trackCtaModelsClicked('hero')"
            class="btn-3d cta-shadow w-full sm:w-auto bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 text-gray-800 dark:text-gray-100 px-6 py-3 sm:px-8 sm:py-4 rounded-sm text-base sm:text-lg font-semibold hover:text-green-700 dark:hover:text-green-400 transition inline-flex items-center justify-center space-x-2"
          >
            <span>{{ t('landing.hero.models_button') }}</span>
            <ArrowRightIcon class="h-5 w-5" />
          </router-link>
        </div>

      </div>
    </section>

    <!-- Model Preview Section: own accent band so the cost section stands out and
         breaks the seam between the two surrounding grid sections -->
    <section class="py-8 sm:py-16 px-4 sm:px-6 lg:px-8 bg-gray-50 dark:bg-gray-800/40 border-y border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto">
        <div class="text-center mb-12">
          <h2 class="text-3xl font-semibold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('landing.models_section.title') }}
          </h2>
          <i18n-t keypath="landing.models_section.subtitle" tag="p" class="max-w-2xl mx-auto text-xl sm:text-2xl text-gray-600 dark:text-gray-400">
            <template #emphasis>
              <span class="font-bold text-gray-900 dark:text-gray-100">{{ t('landing.models_section.subtitle_emphasis') }}</span>
            </template>
          </i18n-t>
        </div>

        <div v-if="loading" class="text-center text-gray-500 dark:text-gray-400">
          {{ t('landing.models_section.loading') }}
        </div>

        <div v-else-if="topModels.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          <!-- Model Cards -->
          <PublicModelCard
            v-for="preview in topModels"
            :key="`${preview.brand}-${preview.model}`"
            :model="preview"
            :to="`${modelsUrl}/${preview.brandDisplayName}/${preview.modelUrlSlug}`"
          />

          <!-- Next 4 models teaser + CTAs - span full grid width -->
          <div class="col-span-full mt-2 space-y-4">
            <div v-if="nextModels.length > 0">
              <!-- Mobile: pills -->
              <div class="flex flex-wrap gap-2 justify-center sm:hidden">
                <a
                  v-for="m in nextModels"
                  :key="`${m.brand}-${m.model}`"
                  :href="`${modelsUrl}/${m.brandDisplayName}/${m.modelUrlSlug}`"
                  class="px-3 py-1.5 bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 text-gray-600 dark:text-gray-400 text-xs font-medium rounded-full transition"
                >
                  {{ m.modelDisplayName }}
                </a>
              </div>
              <!-- sm+: cards (identical layout to the top row) -->
              <div class="hidden sm:grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                <PublicModelCard
                  v-for="m in nextModels"
                  :key="`${m.brand}-${m.model}`"
                  :model="m"
                  :to="`${modelsUrl}/${m.brandDisplayName}/${m.modelUrlSlug}`"
                />
              </div>
            </div>
            <!-- Hinweis unter den Modellen, ueber den CTAs: Preise sind Community-Durchschnitte pro Modell. -->
            <div class="mt-8 sm:mt-10 max-w-3xl mx-auto flex items-start gap-3.5 rounded-xl border-2 border-green-600/70 dark:border-green-400/50 bg-green-50 dark:bg-green-900/20 px-5 py-4 sm:px-6 sm:py-5 text-left">
              <InformationCircleIcon class="h-7 w-7 shrink-0 text-green-600 dark:text-green-400 mt-0.5" />
              <p class="text-base sm:text-lg text-gray-700 dark:text-gray-200 leading-relaxed">{{ t('landing.models_section.price_disclaimer') }}</p>
            </div>

            <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-center gap-3 sm:gap-6 mt-6 sm:mt-8">
              <router-link
                :to="modelsUrl"
                class="btn-3d cta-shadow bg-green-600 text-white border-2 border-gray-900 dark:border-gray-100 px-6 py-3 rounded-sm font-semibold hover:bg-green-700 transition inline-flex items-center justify-center space-x-2"
              >
                <span>{{ t('landing.models_section.compare_button') }}</span>
                <ArrowRightIcon class="h-5 w-5" />
              </router-link>
              <button
                @click="goToRegister('models_section')"
                class="btn-3d cta-shadow bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 text-gray-800 dark:text-gray-100 px-6 py-3 rounded-sm font-semibold hover:text-green-700 dark:hover:text-green-400 transition inline-flex items-center justify-center space-x-2"
              >
                <span>{{ t('landing.hero.register_button') }}</span>
              </button>
            </div>
          </div>
        </div>

        <div v-else class="text-center text-gray-500 dark:text-gray-400">
          {{ t('landing.models_section.no_data') }}
        </div>
      </div>
    </section>

    <!-- Community Pulse: live ticker below the cost section -->
    <CommunityPulseSection />

    <!-- App Preview Section -->
    <section class="py-12 sm:py-20 border-t border-gray-100 dark:border-gray-800">

      <!-- Dashboard preview -->
      <div class="max-w-7xl mx-auto px-4 sm:px-8 lg:px-12">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-y-5 lg:gap-x-16 lg:items-center">

          <!-- Heading (mobile pos 1+2) -->
          <div class="order-1 lg:col-start-1 lg:row-start-1 lg:self-end text-center lg:text-left">
            <span class="text-xs sm:text-sm font-extrabold text-green-700 dark:text-green-400 uppercase tracking-[0.18em]">{{ t('landing.app_preview.dashboard_label') }}</span>
            <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2 mb-3">
              {{ t('landing.app_preview.dashboard_title') }}
            </h2>
            <p class="text-lg font-medium text-gray-700 dark:text-gray-200 max-w-md mx-auto lg:mx-0">
              {{ t('landing.app_preview.dashboard_desc') }}
            </p>
          </div>

          <!-- Dashboard screenshot (mobile pos 3) -->
          <div class="order-2 lg:order-none lg:col-start-2 lg:row-start-1 lg:row-span-2 lg:self-center w-full max-w-xl lg:max-w-none mx-auto relative">
            <div class="rounded-xl border-2 border-gray-900 dark:border-gray-100 shadow-[8px_8px_0_0_#111827] dark:shadow-[8px_8px_0_0_#e5e7eb] overflow-hidden relative transition duration-300 ease-out hover:scale-[1.06]">
              <img
                src="/screenshots/dashboard-light.jpg"
                :alt="t('landing.app_preview.dashboard_title')"
                class="w-full block h-auto cursor-zoom-in"
                loading="lazy"
                width="885"
                height="950"
                role="button"
                tabindex="0"
                @click="openLightbox(productShots(), 0)"
                @keydown.enter.space.prevent="openLightbox(productShots(), 0)"
              />
              <div class="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-b from-transparent to-white dark:to-gray-900 pointer-events-none"></div>
            </div>
            <!-- Floating chips -->
            <div class="absolute -top-3 -right-3 bg-green-500 text-white text-sm font-bold px-4 py-2 rounded-full shadow-[4px_4px_0_0_#111827] border-2 border-gray-900 hidden sm:block">
              {{ t('landing.app_preview.chip_co2') }}
            </div>
            <div class="absolute -bottom-3 -left-3 bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 text-sm font-bold px-4 py-2 rounded-full shadow-[4px_4px_0_0_#111827] border-2 border-gray-900 hidden sm:block">
              {{ t('landing.app_preview.chip_costs') }}
            </div>
          </div>

          <!-- Features + CTA (mobile pos 4) -->
          <div class="order-3 lg:col-start-1 lg:row-start-2 lg:self-start text-center lg:text-left">
            <ul class="text-base font-medium text-gray-700 dark:text-gray-200 space-y-2.5 mb-6 text-left inline-block">
              <li class="flex items-start gap-2"><span class="text-green-500 font-bold mt-0.5">-</span>{{ t('landing.app_preview.dashboard_bullet1') }}</li>
              <li class="flex items-start gap-2"><span class="text-green-500 font-bold mt-0.5">-</span>{{ t('landing.app_preview.dashboard_bullet2') }}</li>
              <li class="flex items-start gap-2"><span class="text-green-500 font-bold mt-0.5">-</span>{{ t('landing.app_preview.dashboard_bullet3') }}</li>
            </ul>
            <button
              @click="demoLogin('dashboard_preview')"
              :disabled="demoLoading"
              class="inline-flex items-center gap-2 text-green-600 hover:text-green-700 font-semibold text-base transition disabled:opacity-50"
            >
              {{ t('landing.app_preview.dashboard_cta') }}
              <ArrowRightIcon class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      <!-- Logfeed preview -->
      <div class="max-w-7xl mx-auto px-4 sm:px-8 lg:px-12 mt-16 sm:mt-24">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-y-5 lg:gap-x-16 lg:items-center">

          <!-- Heading (mobile pos 1+2) -->
          <div class="order-1 lg:col-start-2 lg:row-start-1 lg:self-end text-center lg:text-left">
            <span class="text-xs sm:text-sm font-extrabold text-green-700 dark:text-green-400 uppercase tracking-[0.18em]">{{ t('landing.app_preview.logfeed_label') }}</span>
            <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2 mb-3">
              {{ t('landing.app_preview.logfeed_title') }}
            </h2>
            <p class="text-lg font-medium text-gray-700 dark:text-gray-200 max-w-md mx-auto lg:mx-0">
              {{ t('landing.app_preview.logfeed_desc') }}
            </p>
          </div>

          <!-- Logfeed screenshot (mobile pos 3) -->
          <div class="order-2 lg:order-none lg:col-start-1 lg:row-start-1 lg:row-span-2 lg:self-center w-full max-w-xl lg:max-w-none mx-auto relative">
            <div class="rounded-xl border-2 border-gray-900 dark:border-gray-100 shadow-[8px_8px_0_0_#111827] dark:shadow-[8px_8px_0_0_#e5e7eb] overflow-hidden relative transition duration-300 ease-out hover:scale-[1.06]">
              <img
                src="/screenshots/logfeed-light.jpg"
                :alt="t('landing.app_preview.logfeed_title')"
                class="w-full block h-auto cursor-zoom-in"
                loading="lazy"
                width="885"
                height="799"
                role="button"
                tabindex="0"
                @click="openLightbox(productShots(), 1)"
                @keydown.enter.space.prevent="openLightbox(productShots(), 1)"
              />
              <div class="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-b from-transparent to-white dark:to-gray-900 pointer-events-none"></div>
            </div>
            <!-- Floating chips -->
            <div class="absolute -top-3 -right-3 bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 text-sm font-bold px-4 py-2 rounded-full shadow-[4px_4px_0_0_#111827] border-2 border-gray-900 hidden sm:block">
              {{ t('landing.app_preview.chip_efficiency') }}
            </div>
            <div class="absolute -bottom-3 -left-3 bg-green-500 text-white text-sm font-bold px-4 py-2 rounded-full shadow-[4px_4px_0_0_#111827] border-2 border-gray-900 hidden sm:block">
              {{ t('landing.app_preview.chip_range') }}
            </div>
          </div>

          <!-- Features + CTA (mobile pos 4) -->
          <div class="order-3 lg:col-start-2 lg:row-start-2 lg:self-start text-center lg:text-left">
            <ul class="text-base font-medium text-gray-700 dark:text-gray-200 space-y-2.5 mb-6 text-left inline-block">
              <li class="flex items-start gap-2"><span class="text-green-500 font-bold mt-0.5">-</span>{{ t('landing.app_preview.logfeed_bullet1') }}</li>
              <li class="flex items-start gap-2"><span class="text-green-500 font-bold mt-0.5">-</span>{{ t('landing.app_preview.logfeed_bullet2') }}</li>
              <li class="flex items-start gap-2"><span class="text-green-500 font-bold mt-0.5">-</span>{{ t('landing.app_preview.logfeed_bullet3') }}</li>
            </ul>
            <button
              @click="demoLogin('logfeed_preview')"
              :disabled="demoLoading"
              class="inline-flex items-center gap-2 text-green-600 hover:text-green-700 font-semibold text-base transition disabled:opacity-50"
            >
              {{ t('landing.app_preview.logfeed_cta') }}
              <ArrowRightIcon class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Gallery Section -->
    <section class="py-12 sm:py-20 bg-gray-50 dark:bg-gray-900 border-t border-gray-100 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-8 lg:px-12">
        <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 text-center mb-10 sm:mb-14">
          {{ t('landing.app_preview.gallery_title') }}
        </h2>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-12">

          <!-- Map -->
          <div class="flex flex-col">
            <!-- Heading (mobile pos 1) -->
            <div class="order-1 lg:order-2 lg:mt-5">
              <span class="text-xs sm:text-sm font-extrabold text-green-700 dark:text-green-400 uppercase tracking-[0.18em]">{{ t('landing.app_preview.map_label') }}</span>
              <h3 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1 mb-2">{{ t('landing.app_preview.map_title') }}</h3>
            </div>
            <!-- Screenshot (mobile pos 2) -->
            <div class="order-2 lg:order-1 mt-3 lg:mt-0 rounded-xl border-2 border-gray-900 dark:border-gray-100 shadow-[6px_6px_0_0_#111827] dark:shadow-[6px_6px_0_0_#e5e7eb] overflow-hidden transition duration-300 ease-out hover:scale-[1.06]">
              <img
                src="/screenshots/map-light.jpg"
                :alt="t('landing.app_preview.map_title')"
                class="w-full block cursor-zoom-in"
                loading="lazy"
                width="640"
                height="390"
                role="button"
                tabindex="0"
                @click="openLightbox(galleryShots(), 0)"
                @keydown.enter.space.prevent="openLightbox(galleryShots(), 0)"
              />
            </div>
            <!-- Features + CTA (mobile pos 3) -->
            <div class="order-3 mt-3 lg:mt-0">
              <ul class="text-base font-medium text-gray-700 dark:text-gray-200 space-y-2 mb-3">
                <li class="flex items-start gap-2"><span class="text-green-500 font-bold shrink-0 mt-0.5">-</span>{{ t('landing.app_preview.map_bullet1') }}</li>
                <li class="flex items-start gap-2"><span class="text-green-500 font-bold shrink-0 mt-0.5">-</span>{{ t('landing.app_preview.map_bullet2') }}</li>
              </ul>
              <button @click="demoLogin('map_gallery')" :disabled="demoLoading" class="inline-flex items-center gap-2 text-green-600 hover:text-green-700 font-semibold text-base transition disabled:opacity-50">
                {{ t('landing.app_preview.map_cta') }}<ArrowRightIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

          <!-- Charts -->
          <div class="flex flex-col">
            <!-- Heading (mobile pos 1) -->
            <div class="order-1 lg:order-2 lg:mt-5">
              <span class="text-xs sm:text-sm font-extrabold text-green-700 dark:text-green-400 uppercase tracking-[0.18em]">{{ t('landing.app_preview.charts_label') }}</span>
              <h3 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1 mb-2">{{ t('landing.app_preview.charts_title') }}</h3>
            </div>
            <!-- Screenshot (mobile pos 2) -->
            <div class="order-2 lg:order-1 mt-3 lg:mt-0 rounded-xl border-2 border-gray-900 dark:border-gray-100 shadow-[6px_6px_0_0_#111827] dark:shadow-[6px_6px_0_0_#e5e7eb] overflow-hidden transition duration-300 ease-out hover:scale-[1.06]">
              <img
                src="/screenshots/charts-light.jpg"
                :alt="t('landing.app_preview.charts_title')"
                class="w-full block h-auto cursor-zoom-in"
                loading="lazy"
                width="640"
                height="519"
                role="button"
                tabindex="0"
                @click="openLightbox(galleryShots(), 1)"
                @keydown.enter.space.prevent="openLightbox(galleryShots(), 1)"
              />
            </div>
            <!-- Features + CTA (mobile pos 3) -->
            <div class="order-3 mt-3 lg:mt-0">
              <ul class="text-base font-medium text-gray-700 dark:text-gray-200 space-y-2 mb-3">
                <li class="flex items-start gap-2"><span class="text-green-500 font-bold shrink-0 mt-0.5">-</span>{{ t('landing.app_preview.charts_bullet1') }}</li>
                <li class="flex items-start gap-2"><span class="text-green-500 font-bold shrink-0 mt-0.5">-</span>{{ t('landing.app_preview.charts_bullet2') }}</li>
              </ul>
              <button @click="demoLogin('charts_gallery')" :disabled="demoLoading" class="inline-flex items-center gap-2 text-green-600 hover:text-green-700 font-semibold text-base transition disabled:opacity-50">
                {{ t('landing.app_preview.charts_cta') }}<ArrowRightIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

        </div>
      </div>
    </section>

    <!-- Leaderboard Preview Section -->
    <section v-show="leaderboardReady" class="py-12 sm:py-20 border-t border-gray-100 dark:border-gray-800">
      <div class="max-w-2xl mx-auto px-4 sm:px-6">

        <!-- Marketing-Ueberschrift: fuehrt das Live-Widget ein -->
        <div class="text-center mb-8">
          <span class="text-xs sm:text-sm font-extrabold text-green-700 dark:text-green-400 uppercase tracking-[0.18em]">{{ t('landing.leaderboard.label') }}</span>
          <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2 mb-3">
            {{ t('landing.leaderboard.title') }}
          </h2>
          <p class="text-lg sm:text-xl font-medium text-gray-700 dark:text-gray-200 max-w-xl mx-auto">
            <template v-for="(part, i) in leaderboardDescParts" :key="i">{{ part }}<br v-if="i < leaderboardDescParts.length - 1" /></template>
          </p>
        </div>

        <!-- Echtes, interaktives Leaderboard-Widget im Brand-Rahmen (live aus /api/public/leaderboard) -->
        <!-- Mobile clippt der Rahmen sauber; ab sm overflow-visible, damit die Galerie-Pfeile ausserhalb sichtbar sind -->
        <div class="rounded-xl border-2 border-gray-900 dark:border-gray-100 shadow-[8px_8px_0_0_#15803d] dark:shadow-[8px_8px_0_0_#22c55e] overflow-hidden sm:overflow-visible">
          <CommunityLeaderboard :show-header="false" :month="leaderboardMonth" :max-entries="5" @available="leaderboardReady = leaderboardReady || $event">
            <!-- Footer (Login-Hinweise) auf der Landing unterdruecken; CTA liegt unter dem Widget -->
            <template #footer />
          </CommunityLeaderboard>
        </div>

        <!-- CTA unter dem Widget -->
        <button
          type="button"
          @click="goToRegister('leaderboard_cta')"
          class="w-full mt-6 inline-flex items-center justify-center gap-2 px-6 py-4 bg-green-600 hover:bg-green-700 text-white text-lg sm:text-xl font-bold rounded-lg border-2 border-gray-900 dark:border-gray-100 shadow-[4px_4px_0_0_#111827] dark:shadow-[4px_4px_0_0_#e5e7eb] transition active:translate-x-0.5 active:translate-y-0.5 active:shadow-none">
          {{ t('landing.leaderboard.cta') }}
          <ArrowRightIcon class="h-6 w-6 shrink-0" />
        </button>
      </div>
    </section>

    <!-- Dark Mode Teaser -->
    <div class="py-12 sm:py-16 border-t border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 text-center">
      <MoonIcon class="h-8 w-8 text-indigo-400 mx-auto mb-3" />
      <p class="text-xl sm:text-2xl font-semibold text-gray-800 dark:text-gray-100 mb-10">
        {{ t('landing.darkmode_teaser') }}
      </p>
      <!-- Dark mode gallery -->
      <div class="max-w-7xl mx-auto px-4 sm:px-8 lg:px-12">
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 sm:gap-6">
          <div class="rounded-xl overflow-hidden border-2 border-gray-900 dark:border-gray-100 shadow-[6px_6px_0_0_#111827] dark:shadow-[6px_6px_0_0_#e5e7eb] aspect-[900/1087]">
            <img src="/screenshots/dashboard-dark.jpg" :alt="t('landing.app_preview.dashboard_title')" class="w-full h-full object-cover object-top cursor-zoom-in" loading="lazy" width="900" height="1087" role="button" tabindex="0" @click="openLightbox(darkShots(), 0)" @keydown.enter.space.prevent="openLightbox(darkShots(), 0)" />
          </div>
          <div class="rounded-xl overflow-hidden border-2 border-gray-900 dark:border-gray-100 shadow-[6px_6px_0_0_#111827] dark:shadow-[6px_6px_0_0_#e5e7eb] aspect-[900/1087]">
            <img src="/screenshots/charts-dark.jpg" :alt="t('landing.app_preview.charts_title')" class="w-full h-full object-cover object-top cursor-zoom-in" loading="lazy" width="900" height="1165" role="button" tabindex="0" @click="openLightbox(darkShots(), 1)" @keydown.enter.space.prevent="openLightbox(darkShots(), 1)" />
          </div>
          <div class="rounded-xl overflow-hidden border-2 border-gray-900 dark:border-gray-100 shadow-[6px_6px_0_0_#111827] dark:shadow-[6px_6px_0_0_#e5e7eb] aspect-[900/1087]">
            <img src="/screenshots/map-dark.jpg" :alt="t('landing.app_preview.map_title')" class="w-full h-full object-cover object-top cursor-zoom-in" loading="lazy" width="900" height="1156" role="button" tabindex="0" @click="openLightbox(darkShots(), 2)" @keydown.enter.space.prevent="openLightbox(darkShots(), 2)" />
          </div>
        </div>
      </div>
    </div>

    <!-- Import Hub -->
    <section id="import-hub" class="py-8 sm:py-14 px-4 sm:px-6 lg:px-8 bg-gray-50 dark:bg-gray-900 border-t border-b border-gray-200 dark:border-gray-700">
      <div class="max-w-5xl mx-auto">
        <div class="text-center mb-8">
          <div class="inline-flex items-center gap-2 mb-3">
            <ArrowDownTrayIcon class="h-6 w-6 text-green-600" />
            <h2 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100">{{ t('landing.import.title') }}</h2>
          </div>
          <p class="text-lg sm:text-xl text-gray-600 dark:text-gray-300">{{ t('landing.import.subtitle') }}</p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">

          <div class="flex flex-col gap-4">
            <!-- Group 1: Telemetrie & App -->
            <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg p-4 cta-shadow">
              <p class="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">{{ t('landing.import.group_cars') }}</p>
              <div class="space-y-3">
                <div class="flex items-start gap-2.5">
                  <span class="mt-1.5 h-2 w-2 rounded-full bg-gray-900 dark:bg-gray-100 shrink-0"></span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-1 flex-wrap">
                      <span class="text-base font-semibold text-gray-900 dark:text-gray-100">Tesla Fleet API</span>
                      <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                    </div>
                    <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.tesla_desc') }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-2.5">
                  <span class="mt-1.5 h-2 w-2 rounded-full bg-blue-500 shrink-0"></span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-1 flex-wrap">
                      <span class="text-base font-semibold text-gray-900 dark:text-gray-100">Tessie</span>
                      <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                    </div>
                    <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.tessie_desc') }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-2.5">
                  <span class="mt-1.5 h-2 w-2 rounded-full bg-purple-500 shrink-0"></span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-1 flex-wrap">
                      <span class="text-base font-semibold text-gray-900 dark:text-gray-100">Tronity</span>
                      <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                    </div>
                    <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.tronity_desc') }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- Group 2: Wallboxen -->
            <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg p-4 cta-shadow">
              <p class="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">{{ t('landing.import.group_wallbox') }}</p>
              <div class="space-y-3">
                <div class="flex items-start gap-2.5">
                  <span class="mt-1.5 h-2 w-2 rounded-full bg-green-500 shrink-0"></span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-1 flex-wrap">
                      <span class="text-base font-semibold text-gray-900 dark:text-gray-100">go-eCharger Cloud</span>
                      <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                    </div>
                    <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.gocharger_desc') }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-2.5">
                  <span class="mt-1.5 h-2 w-2 rounded-full bg-gray-500 shrink-0"></span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-1 flex-wrap">
                      <span class="text-base font-semibold text-gray-900 dark:text-gray-100">OCPP Wallbox</span>
                      <div class="flex gap-1 shrink-0">
                        <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full">{{ t('landing.import.available') }}</span>
                        <span class="text-xs bg-blue-100 text-blue-800 font-medium px-1.5 py-0.5 rounded-full">{{ t('landing.import.beta') }}</span>
                      </div>
                    </div>
                    <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.ocpp_desc') }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Group 3: Import & API -->
          <div class="bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg p-4 cta-shadow">
            <p class="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">{{ t('landing.import.group_manual') }}</p>
            <div class="space-y-3">
              <div class="flex items-start gap-2.5">
                <span class="mt-1.5 h-2 w-2 rounded-full bg-indigo-500 shrink-0"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-1 flex-wrap">
                    <span class="text-base font-semibold text-gray-900 dark:text-gray-100">Sprit-Monitor</span>
                    <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                  </div>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.spritmonitor_desc') }}</p>
                </div>
              </div>
              <div class="flex items-start gap-2.5">
                <span class="mt-1.5 h-2 w-2 rounded-full bg-gray-400 shrink-0"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-1 flex-wrap">
                    <span class="text-base font-semibold text-gray-900 dark:text-gray-100">CSV / JSON</span>
                    <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                  </div>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.csv_desc') }}</p>
                </div>
              </div>
              <div class="flex items-start gap-2.5">
                <span class="mt-1.5 h-2 w-2 rounded-full bg-orange-400 shrink-0"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-1 flex-wrap">
                    <span class="text-base font-semibold text-gray-900 dark:text-gray-100">REST API</span>
                    <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full shrink-0">{{ t('landing.import.available') }}</span>
                  </div>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.api_desc') }}</p>
                </div>
              </div>
              <div class="flex items-start gap-2.5">
                <span class="mt-1.5 h-2 w-2 rounded-full bg-red-400 shrink-0"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-1 flex-wrap">
                    <span class="text-base font-semibold text-gray-900 dark:text-gray-100">XPeng</span>
                    <div class="flex gap-1 shrink-0">
                      <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full">{{ t('landing.import.available') }}</span>
                      <span class="text-xs bg-blue-100 text-blue-800 font-medium px-1.5 py-0.5 rounded-full">{{ t('landing.import.beta') }}</span>
                    </div>
                  </div>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.xpeng_desc') }}</p>
                </div>
              </div>
              <div class="flex items-start gap-2.5">
                <span class="mt-1.5 h-2 w-2 rounded-full bg-blue-600 shrink-0"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-1 flex-wrap">
                    <span class="text-base font-semibold text-gray-900 dark:text-gray-100">VW Group</span>
                    <div class="flex gap-1 shrink-0">
                      <span class="text-xs bg-green-100 text-green-700 font-medium px-1.5 py-0.5 rounded-full">{{ t('landing.import.available') }}</span>
                      <span class="text-xs bg-blue-100 text-blue-800 font-medium px-1.5 py-0.5 rounded-full">{{ t('landing.import.beta') }}</span>
                    </div>
                  </div>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 leading-snug">{{ t('landing.import.vweuda_desc') }}</p>
                </div>
              </div>
            </div>
          </div>

        </div>

        <!-- AutoSync unter den Quellen erklaert, direkt beim Preise-Link: das
             vollautomatische (Premium-)Verfahren + Verweis auf die Pakete. -->
        <div class="mt-8 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg p-5 sm:p-6 cta-shadow">
          <div class="flex flex-col items-center text-center">
            <ArrowPathIcon class="h-7 w-7 text-green-600 dark:text-green-400 shrink-0 mb-2" />
            <p class="text-lg font-bold text-gray-900 dark:text-gray-100">AutoSync</p>
            <p class="text-base text-gray-600 dark:text-gray-300 mt-1 leading-relaxed max-w-2xl">{{ t('landing.import.autosync_explainer') }}</p>
          </div>
          <div class="mt-4 text-center">
            <router-link
              :to="pricingUrl"
              class="inline-flex items-center gap-2 text-base font-semibold text-green-700 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 transition"
            >
              {{ t('landing.import.pricing_link') }}
              <ArrowRightIcon class="h-5 w-5 shrink-0" />
            </router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- Privacy Section -->
    <section id="privacy-section" class="py-10 sm:py-16 px-4 sm:px-6 lg:px-8 border-t border-gray-100 dark:border-gray-800">
      <div class="max-w-4xl mx-auto">
        <div class="text-center mb-8">
          <div class="inline-flex items-center gap-2 mb-3">
            <ShieldCheckIcon class="h-7 w-7 text-green-600" />
            <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 dark:text-gray-100">{{ t('landing.privacy.title') }}</h2>
          </div>
          <p class="text-lg sm:text-xl text-gray-600 dark:text-gray-300 max-w-xl mx-auto">{{ t('landing.privacy.subtitle') }}</p>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">

          <!-- Encryption -->
          <div class="flex flex-col items-center text-center gap-2.5 p-5 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg cta-shadow">
            <div class="h-10 w-10 rounded-full bg-green-50 dark:bg-green-900/20 flex items-center justify-center">
              <LockClosedIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
            <p class="text-base font-semibold text-gray-900 dark:text-gray-100 leading-snug">{{ t('landing.privacy.encryption_title') }}</p>
            <p class="text-sm text-gray-600 dark:text-gray-400 leading-snug">{{ t('landing.privacy.encryption_desc') }}</p>
          </div>

          <!-- DSGVO -->
          <div class="flex flex-col items-center text-center gap-2.5 p-5 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg cta-shadow">
            <div class="h-10 w-10 rounded-full bg-green-50 dark:bg-green-900/20 flex items-center justify-center">
              <DocumentCheckIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
            <p class="text-base font-semibold text-gray-900 dark:text-gray-100 leading-snug">{{ t('landing.privacy.gdpr_title') }}</p>
            <p class="text-sm text-gray-600 dark:text-gray-400 leading-snug">{{ t('landing.privacy.gdpr_desc') }}</p>
          </div>

          <!-- Geohashing -->
          <div class="flex flex-col items-center text-center gap-2.5 p-5 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg cta-shadow">
            <div class="h-10 w-10 rounded-full bg-green-50 dark:bg-green-900/20 flex items-center justify-center">
              <MapPinIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
            <p class="text-base font-semibold text-gray-900 dark:text-gray-100 leading-snug">{{ t('landing.privacy.geohash_title') }}</p>
            <p class="text-sm text-gray-600 dark:text-gray-400 leading-snug">{{ t('landing.privacy.geohash_desc') }}</p>
          </div>

          <!-- German Servers -->
          <div class="flex flex-col items-center text-center gap-2.5 p-5 bg-white dark:bg-gray-800 border-2 border-gray-900 dark:border-gray-100 rounded-lg cta-shadow">
            <div class="h-10 w-10 rounded-full bg-green-50 dark:bg-green-900/20 flex items-center justify-center">
              <ServerStackIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
            <p class="text-base font-semibold text-gray-900 dark:text-gray-100 leading-snug">{{ t('landing.privacy.servers_title') }}</p>
            <p class="text-sm text-gray-600 dark:text-gray-400 leading-snug">{{ t('landing.privacy.servers_desc') }}</p>
          </div>

        </div>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="py-10 sm:py-20 px-4 sm:px-6 lg:px-8 bg-gray-50 dark:bg-gray-900 border-t border-b border-gray-200 dark:border-gray-700">
      <div class="max-w-3xl mx-auto text-center">
        <h2 class="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-4">
          {{ t('landing.cta.title') }}
        </h2>
        <p class="text-lg text-gray-600 dark:text-gray-400 mb-8">
          {{ t('landing.cta.subtitle') }}<br />
          {{ t('landing.cta.subtitle2') }}
        </p>
        <button
          @click="goToRegister('footer_cta')"
          class="btn-3d [--btn-shadow-color:#111827] dark:[--btn-shadow-color:#000000] bg-green-600 text-white border-2 border-gray-900 dark:border-gray-100 px-8 py-4 rounded-sm text-lg font-semibold hover:bg-green-700 transition"
        >
          {{ t('landing.cta.button') }}
        </button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="border-t-2 border-gray-900 dark:border-gray-800 py-12 px-4 sm:px-6 lg:px-8 bg-gray-100 dark:bg-gray-950">
      <div class="max-w-7xl mx-auto">
        <div class="flex flex-col md:flex-row justify-between items-center space-y-4 md:space-y-0">
          <div class="flex items-center space-x-2">
            <BoltIcon class="h-6 w-6 text-green-600" />
            <span class="text-xl font-semibold text-gray-900 dark:text-gray-100">EV Monitor</span>
          </div>
          <div class="flex flex-wrap justify-center gap-x-6 gap-y-3 text-lg font-medium text-gray-700 dark:text-gray-200">
            <router-link :to="modelsUrl" class="hover:text-gray-900 dark:hover:text-gray-100 font-medium">{{ t('landing.footer.models') }}</router-link>
            <router-link to="/preise" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.pricing') }}</router-link>
            <router-link to="/blog" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.blog') }}</router-link>
            <router-link to="/datenschutz" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.privacy') }}</router-link>
            <router-link to="/impressum" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.imprint') }}</router-link>
            <router-link to="/agb" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.terms') }}</router-link>
            <a href="https://github.com/sebastianwien/ev-monitor" target="_blank" rel="noopener noreferrer" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.github') }}</a>
            <a href="https://tally.so/r/vGB8XA" target="_blank" rel="noopener noreferrer" class="hover:text-gray-900 dark:hover:text-gray-100">{{ t('landing.footer.feedback') }}</a>
          </div>
        </div>
        <div class="mt-8 text-center text-lg text-gray-600 dark:text-gray-300">
          {{ t('landing.footer.made_with') }}
        </div>
        <div class="mt-3 text-center text-base text-gray-500 dark:text-gray-400">
          <SupportPopover variant="footer" />
        </div>
      </div>
    </footer>
    <!-- Green accent line, mirroring the navbar's top accent (here at the bottom) -->
    <div class="h-1 bg-gradient-to-r from-green-400 via-emerald-500 to-green-600"></div>

    <Transition name="back-btn">
      <button
        v-if="returnScrollY !== null"
        @click="scrollBack"
        class="fixed bottom-6 right-6 z-50 flex items-center gap-1.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 text-gray-700 dark:text-gray-300 text-xs font-medium px-3 py-2 rounded-full shadow-lg hover:border-green-500 hover:text-green-700 transition"
      >
        <ArrowRightIcon class="h-3.5 w-3.5 rotate-[-90deg]" />
        {{ t('common.back') }}
      </button>
    </Transition>
  </div>
</template>


<style scoped>

/* 3D press effect for buttons in sections (not nav) */
section a[class*="rounded"]:not(.no-press), section button[class*="rounded"]:not(.no-press) {
  box-shadow: 0 4px 0 0 rgba(0,0,0,0.30);
  transform: translateY(0);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}

section a[class*="rounded"]:not(.no-press):active, section button[class*="rounded"]:not(.no-press):active {
  box-shadow: 0 1px 0 0 rgba(0,0,0,0.30);
  transform: translateY(3px);
  transition: transform 0.05s ease, box-shadow 0.05s ease;
}
</style>
