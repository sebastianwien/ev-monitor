<template>
<div class="min-h-screen bg-gray-50 dark:bg-gray-950">
  <PublicNav />

  <main class="mx-auto grid max-w-7xl gap-[18px] pb-10 lg:px-6">
    <nav class="px-4 pt-6 text-[13px] text-gray-500 lg:px-0 dark:text-gray-400">
      <RouterLink :to="modelsBaseUrl" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('compare.breadcrumb_models') }}</RouterLink>
      <span class="mx-2" aria-hidden="true">›</span>
      <span class="text-gray-900 dark:text-gray-100">{{ t('compare.breadcrumb_current') }}</span>
    </nav>

    <div v-if="loading" class="flex items-center justify-center py-20" data-page-loading>
      <div class="h-10 w-10 animate-spin rounded-full border-b-2 border-green-600"></div>
    </div>

    <div v-else-if="error || items.length < 2" class="grid justify-items-center gap-4 px-4 py-20 text-center">
      <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ t('compare.error_title') }}</h1>
      <p class="text-gray-600 dark:text-gray-400">{{ error || t('compare.error_min_models') }}</p>
      <RouterLink :to="modelsBaseUrl" class="inline-flex min-h-11 items-center rounded-xl bg-green-600 px-4 font-semibold text-white hover:bg-green-700">{{ t('compare.breadcrumb_models') }}</RouterLink>
    </div>

    <template v-else>
      <!-- 1. Head: the question, one result sentence, the models as chips -->
      <header class="grid gap-4 px-4 lg:px-0">
        <h1 class="text-balance text-[clamp(30px,7.4vw,52px)] font-bold leading-[1.04] tracking-tight text-gray-900 dark:text-gray-100" data-testid="compare-title">{{ heading }}</h1>
        <p v-if="summaryText" class="max-w-[60ch] text-[16px] leading-relaxed text-gray-700 md:text-[17px] dark:text-gray-300" data-testid="compare-summary">{{ summaryText }}</p>
        <div class="flex flex-wrap gap-2">
          <ChoiceChip v-for="item in items" :key="item.key" size="sm" :aria-label="t('compare.remove', { name: shortName(item) })" data-testid="compare-chip" @click="remove(item)">
            {{ shortName(item) }}<XMarkIcon class="h-4 w-4" stroke-width="2.25" aria-hidden="true" />
          </ChoiceChip>
          <ChoiceChip v-if="items.length < MAX_COMPARE" size="sm" :href="listHref(items.map(i => i.key))" data-testid="compare-add">
            <PlusIcon class="h-4 w-4" stroke-width="2.25" aria-hidden="true" />{{ t('compare.add') }}
          </ChoiceChip>
        </div>
      </header>

      <!-- Phone: the names stay in view once the column heads scroll away, right under the public nav (70 px) -->
      <div ref="stickysentinel" class="h-0 md:hidden"></div>
      <Transition name="slide-down">
        <div v-if="floatingHeaderVisible" class="fixed inset-x-0 top-[calc(70px+env(safe-area-inset-top))] z-40 border-b border-gray-200 bg-white px-4 py-2 md:hidden dark:border-gray-800 dark:bg-gray-900">
          <div class="grid gap-3" :class="COLS[items.length]">
            <div v-for="item in items" :key="item.key" class="truncate text-center text-[13px] font-bold text-gray-900 dark:text-gray-100">{{ shortName(item) }}</div>
          </div>
        </div>
      </Transition>

      <!-- 2. For you: the open row's answers side by side; guests and an empty profile get one way to /modelle -->
      <section v-if="forYouRows.length" class="grid gap-3 bg-white px-4 py-6 md:rounded-3xl md:px-7 md:edge-box dark:bg-gray-900" data-testid="compare-for-you">
        <h2 class="text-center text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('models_ranking.detail.for_you') }}</h2>
        <CompareRows :rows="forYouRows" :names="items.map(shortName)" />
      </section>
      <RouterLink v-else :to="`${modelsBaseUrl}#passt-zu-dir`" class="edge-card mx-4 flex min-h-16 items-center gap-3 rounded-2xl bg-white px-4 py-3 hover:bg-green-50 lg:mx-0 dark:bg-gray-900 dark:hover:bg-green-950/40" data-testid="compare-profile-link">
        <UserIcon class="h-6 w-6 flex-none text-green-700 dark:text-green-400" aria-hidden="true" />
        <span class="grid min-w-0 flex-1">
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('compare.profile_title') }}</span>
          <span class="text-[13px] leading-snug text-gray-600 dark:text-gray-400">{{ t('compare.profile_sub') }}</span>
        </span>
        <ChevronRightIcon class="h-5 w-5 flex-none text-gray-400" aria-hidden="true" />
      </RouterLink>

      <!-- 3. What the drivers measure: column heads with photo, variant and model page, then the ranking row's cells and the fact list -->
      <section class="grid gap-5 bg-white px-4 py-6 md:rounded-3xl md:px-7 md:edge-box dark:bg-gray-900" data-testid="compare-measured">
        <h2 class="text-center text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ t('models_ranking.detail.measured') }}</h2>
        <div class="grid gap-x-4 gap-y-3" :class="GRID[items.length]">
          <span class="hidden md:block" aria-hidden="true"></span>
          <div v-for="(item, i) in items" :key="item.key" class="grid min-w-0 content-start justify-items-center gap-2 text-center" data-testid="compare-head">
            <img v-if="thumb(item)" :src="thumb(item)!" alt="" loading="lazy" class="h-16 w-16 rounded-xl bg-gray-200 object-cover md:h-20 md:w-20 dark:bg-gray-800" />
            <span v-else class="grid h-16 w-16 place-items-center rounded-xl bg-gray-100 text-[15px] font-bold text-gray-600 md:h-20 md:w-20 dark:bg-gray-800 dark:text-gray-300" aria-hidden="true">{{ item.model.brandDisplayName.slice(0, 2).toLocaleUpperCase() }}</span>
            <span class="text-[15px] font-semibold leading-snug text-gray-900 dark:text-gray-100"><span class="md:hidden">{{ shortName(item) }}</span><span class="hidden md:inline">{{ fullName(item) }}</span></span>
            <VariantPicker v-if="variantChoices[i].length >= 2" compact class="w-full max-w-[260px] [&>button]:w-full" :options="variantOptions[i]"
                           :align="i === 0 ? 'left' : i === items.length - 1 ? 'right' : 'center'"
                           :model-value="variantIndex[item.key] ?? 0" data-testid="compare-variant"
                           @update:model-value="v => variantIndex[item.key] = v" />
            <ModelPageButton :href="modelHref(item)" :label="t('compare.model_page')" :aria-label="t('models_ranking.detail.model_page', { name: fullName(item) })"
                             compact class="w-full max-w-[260px]" />
          </div>
        </div>
        <CompareRows :rows="measuredRows" :names="items.map(shortName)" />
        <p class="flex items-center gap-1.5 text-[12.5px] text-gray-500 dark:text-gray-400">
          <span class="inline-block h-2 w-2 rounded-full bg-green-600 dark:bg-green-400" aria-hidden="true"></span>{{ t('compare.legend_better') }}
        </p>
      </section>

      <!-- 6. Guests only, as on /modelle -->
      <section v-if="!isAuthenticated" class="mx-4 grid gap-3 rounded-2xl bg-gray-900 p-6 text-white lg:mx-0 dark:bg-gray-800">
        <h2 class="text-[22px] font-bold tracking-tight">{{ t('compare.cta_title') }}</h2>
        <p class="max-w-[52ch] text-gray-300">{{ t('compare.cta_desc') }}</p>
        <a :href="registerPath" class="inline-flex min-h-11 items-center justify-self-start rounded-xl bg-green-600 px-4 font-semibold text-white hover:bg-green-500">{{ t('compare.cta_register') }}</a>
      </section>
    </template>
  </main>

  <footer class="mx-auto max-w-7xl border-t border-gray-200 px-4 py-8 text-center text-sm text-gray-500 dark:border-gray-800 dark:text-gray-400">
    © {{ currentYear }} EV Monitor ·
    <RouterLink :to="modelsBaseUrl" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('compare.all_models') }}</RouterLink>
  </footer>
</div>
</template>

<script setup lang="ts">
import { ref, onUnmounted, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { ChevronRightIcon, PlusIcon, UserIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { getTopModels, getChargingReferencePrices, getModelStats, type TopModelPreview, type WltpVariant } from '../api/publicModelService'
import PublicNav from '../components/shared/PublicNav.vue'
import ChoiceChip from '../components/shared/ChoiceChip.vue'
import VariantPicker, { type VariantOption } from '../components/models/VariantPicker.vue'
import ModelPageButton from '../components/models/ModelPageButton.vue'
import CompareRows, { GRID, COLS, type CompareRow } from '../components/models/CompareRows.vue'
import { useAuthStore } from '../stores/auth'
import { useMarketRoute, getMarketBasePath } from '../composables/useMarketRoute'
import { useModelRanking, MAX_COMPARE, type RankedModel } from '../composables/useModelRanking'
import { useRankedModelText } from '../composables/useRankedModelText'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { officialModelThumbUrl } from '../config/modelImages'
import { combustionAllowed } from '../utils/costMix'
import { buildLadderAxis, barLength } from '../utils/ladderScale'
import { rangeKm, prospectKm } from '../utils/modelStory'
import { applyVariant, betterMarks, compareSummary, variantsWithTrips } from '../utils/compareModels'

const { t, locale } = useI18n()
const { distanceUnitLabel, formatDistance, formatNumber, unitSystem } = useLocaleFormat()
const authStore = useAuthStore()
const isAuthenticated = computed(() => authStore.isAuthenticated())
const { currentMarket, isDE, robots } = useMarketRoute()
const route = useRoute()
const router = useRouter()
const registerPath = computed(() => locale.value === 'en' ? '/en/register' : '/register')
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))
const currentYear = new Date().getFullYear()

// ── Floating names on the phone (IntersectionObserver) ────────────────────────
const stickysentinel = ref<HTMLElement | null>(null)
const floatingHeaderVisible = ref(false)
let observer: IntersectionObserver | null = null
watch(stickysentinel, el => {
  observer?.disconnect()
  if (!el) return
  observer = new IntersectionObserver(([entry]) => { floatingHeaderVisible.value = !entry.isIntersecting && entry.boundingClientRect.top < 0 })
  observer.observe(el)
})
onUnmounted(() => observer?.disconnect())

// ── Data: the same ranking as /modelle, so every figure matches the list ──────
const loading = ref(true)
const error = ref<string | null>(null)
const models = ref<TopModelPreview[]>([])
const { ranked, cost, needs, combustionMarket, applyReferencePrices } = useModelRanking(models)
watch(currentMarket, m => { combustionMarket.value = combustionAllowed(m) }, { immediate: true })

const modelKeys = computed((): string[] => {
  const raw = route.query.models as string | undefined
  return raw ? raw.split(',').slice(0, MAX_COMPARE).map(s => s.trim()).filter(Boolean) : []
})

onMounted(async () => {
  if (modelKeys.value.length < 2) {
    error.value = t('compare.error_need_two')
    loading.value = false
    return
  }
  try {
    const [top, prices] = await Promise.all([getTopModels(200), getChargingReferencePrices().catch(() => null)])
    models.value = top
    applyReferencePrices(prices, isDE.value)
  } catch {
    error.value = t('compare.error_loading')
  } finally {
    loading.value = false
  }
})

/** The compared models in the link's order; keys match regardless of case (older shared links) */
const rankedItems = computed<RankedModel[]>(() => modelKeys.value
  .map(k => ranked.value.find(r => r.key.toLowerCase() === k.toLowerCase()))
  .filter((r): r is RankedModel => r != null))

// ── Variants: only those with trips; "Alle Varianten" shows the list's figures ─
const variantsByKey = ref<Record<string, WltpVariant[]>>({})
const variantIndex = ref<Record<string, number>>({})
watch(() => rankedItems.value.map(r => r.key), keys => {
  for (const key of keys) {
    if (variantsByKey.value[key]) continue
    variantsByKey.value[key] = []
    const [brand, model] = key.split('/')
    getModelStats(brand, model)
      .then(s => { variantsByKey.value[key] = variantsWithTrips(s?.wltpVariants ?? []) })
      .catch(() => { /* no picker without the variants */ })
  }
}, { immediate: true })
const variantChoices = computed(() => rankedItems.value.map(r => variantsByKey.value[r.key] ?? []))
/** What the page shows: each model on its chosen battery */
const items = computed<RankedModel[]>(() => rankedItems.value.map((r, i) => {
  const v = variantChoices.value[i][(variantIndex.value[r.key] ?? 0) - 1]
  return v ? applyVariant(r, v) : r
}))
/** The model page's picker: "Alle Varianten" first (the list's figures), then each battery by the model page's rule */
const variantOptions = computed<VariantOption[][]>(() => rankedItems.value.map((r, i) => {
  const lo = r.model.minNetCapacityKwh
  const hi = r.model.maxNetCapacityKwh
  const all: VariantOption = {
    key: 'all',
    label: t('compare.all_variants'),
    short: t('compare.all_variants_short'),
    battery: lo != null && hi != null
      ? (Math.round(lo) === Math.round(hi) ? `${formatNumber(Math.round(hi))} kWh` : `${t('models_ranking.row.span', { min: formatNumber(Math.round(lo)), max: formatNumber(Math.round(hi)) })} kWh`)
      : '',
    range: `${listTexts[i].rangeValue.value} ${distanceUnitLabel()}`,
    prospect: '',
  }
  return [all, ...variantChoices.value[i].map((v, vi) => {
    const real = rangeKm(v.batteryCapacityKwh, r.model.avgConsumptionKwhPer100km)
    const wltp = real != null ? prospectKm(v.wltpRangeKm)! : v.wltpRangeKm
    return {
      key: `${vi}-${v.displayLabel ?? v.batteryCapacityKwh}`,
      label: v.displayLabel || v.variantName || `${formatNumber(v.batteryCapacityKwh)} kWh`,
      battery: `${formatNumber(v.batteryCapacityKwh)} kWh`,
      range: real != null ? formatDistance(real) : null,
      prospect: t('model.page.variant_prospect', { range: formatDistance(wltp) }),
    }
  })]
}))

// ── Names and links ───────────────────────────────────────────────────────────
/** "Model 3", "ID.3": the head's question and the chips */
const shortName = (item: RankedModel) => item.model.modelUrlSlug.replace(/_/g, ' ')
/** "Tesla Model 3"; a model name that carries the brand ("Renault 5") shows it once */
function fullName(item: RankedModel) {
  const name = shortName(item)
  return name.toLowerCase().startsWith(item.model.brandDisplayName.toLowerCase()) ? name : `${item.model.brandDisplayName} ${name}`
}
const modelHref = (item: RankedModel) => `${modelsBaseUrl.value}/${item.model.brandSlug}/${item.model.modelUrlSlug}`
const thumb = (item: RankedModel) => officialModelThumbUrl(item.model.model)
/** Back to the list with these models picked */
const listHref = (keys: string[]) => `${modelsBaseUrl.value}?vergleich=${keys.join(',')}#fahrer-messen`
function remove(item: RankedModel) {
  const rest = items.value.filter(i => i.key !== item.key).map(i => i.key)
  if (rest.length >= 2) router.replace({ query: { ...route.query, models: rest.join(',') } })
  else router.push(listHref(rest))
}

// ── Head: the question and one result sentence ────────────────────────────────
const heading = computed(() => {
  const n = items.value.map(shortName)
  return n.length === 3 ? t('compare.title_three', { a: n[0], b: n[1], c: n[2] }) : t('compare.title_two', { a: n[0], b: n[1] })
})
const summaryText = computed(() => {
  const s = compareSummary(items.value.map(i => ({
    name: shortName(i), consumption: i.model.avgConsumptionKwhPer100km, winterKm: i.model.winterRangeMaxKm ?? null,
  })))
  // with two models the other one is obvious, with three it is named
  const than = items.value.length > 2
  const lean = s.lean ? { a: s.lean.winner, b: s.lean.other, pct: formatNumber(s.lean.pct) } : null
  const winter = s.winter ? { a: s.winter.winner, b: s.winter.other, km: formatDistance(s.winter.km, { step: 10 }) } : null
  if (lean && winter && lean.a === winter.a && (!than || lean.b === winter.b)) {
    return t(than ? 'compare.summary_both_than' : 'compare.summary_both', { ...lean, km: winter.km }) + '.'
  }
  const parts = [
    lean ? t(than ? 'compare.summary_lean_than' : 'compare.summary_lean', lean) : null,
    winter ? t(than ? 'compare.summary_winter_than' : 'compare.summary_winter', winter) : null,
  ].filter(Boolean)
  return parts.length ? parts.join(', ') + '.' : ''
})

// ── Rows ──────────────────────────────────────────────────────────────────────
// One text set per compare slot (at most MAX_COMPARE); a slot is only read while it holds a model.
const texts = Array.from({ length: MAX_COMPARE }, (_, i) =>
  useRankedModelText(() => items.value[i], () => needs.value.longestTripKm, () => cost.value.fuel))
const slots = computed(() => items.value.map((_, i) => texts[i]))
/** The same models without a chosen variant: "Alle Varianten" in the picker names the list's range */
const listTexts = Array.from({ length: MAX_COMPARE }, (_, i) =>
  useRankedModelText(() => rankedItems.value[i], () => needs.value.longestTripKm, () => cost.value.fuel))

/** For you: signed in only (guests have the one gate on /modelle), each answer once any model has it */
const ANSWER_ORDER = ['charge', 'trip', 'cost']
const forYouRows = computed<CompareRow[]>(() => {
  if (!isAuthenticated.value) return []
  return ANSWER_ORDER.flatMap(key => {
    const answers = slots.value.map(x => x.answers.value.find(a => a.key === key) ?? null)
    const first = answers.find(a => a != null)
    if (!first) return []
    return [{ key, label: first.label, cells: answers.map(a => ({ value: a?.value ?? '–', sub: a?.sub ?? [], better: false })) }]
  })
})

// The list's bar scale over all models, so a bar here is as long as in the list
const axis = computed(() => buildLadderAxis(
  models.value.flatMap(m => [
    m.avgConsumptionKwhPer100km, m.avgWltpConsumptionKwhPer100km, m.minWltpConsumptionKwhPer100km,
    m.maxWltpConsumptionKwhPer100km, m.minRealConsumptionKwhPer100km, m.maxRealConsumptionKwhPer100km,
  ]),
  unitSystem.value.consumptionUnit,
))

const round = (v: number | null | undefined, step: number) => v != null ? Math.round(v / step) * step : null
/** Marked figures, compared as shown (rounded): lower consumption and cost, more range */
const marks = computed(() => ({
  consumption: betterMarks(items.value.map(r => round(r.model.avgConsumptionKwhPer100km, 0.1)), true),
  cost: betterMarks(items.value.map(r => round(r.costPer100kmEur, 0.01)), true),
  range: betterMarks(items.value.map(r => round(r.model.typicalRangeMaxKm ?? r.model.realRangeKm, 10)), false),
}))

const FACT_ORDER = ['consumption', 'winter', 'highway', 'wltp', 'battery', 'data']
const measuredRows = computed<CompareRow[]>(() => {
  const x = slots.value
  const rows: CompareRow[] = [
    {
      key: 'consumption',
      label: t('models_ranking.columns.consumption'),
      cells: items.value.map((item, i) => ({
        value: `${x[i].realText.value} ${x[i].consumptionUnitLabel()}`,
        sub: [`${t('models_ranking.columns.wltp')} ${x[i].wltpText.value}`],
        better: marks.value.consumption[i],
        bar: {
          real: barLength(item.model.avgConsumptionKwhPer100km, axis.value),
          wltp: barLength(item.wltpKwhPer100km, axis.value),
          // in kWh space: above WLTP orange, below green, as in the list
          tone: (item.wltpDeviationPct ?? 0) > 0 ? 'bg-orange-500 dark:bg-orange-400' : 'bg-green-600 dark:bg-green-500',
          label: `${t('models_ranking.row.bar_drivers')} ${x[i].realText.value}, ${t('models_ranking.columns.wltp')} ${x[i].wltpText.value} ${x[i].consumptionUnitLabel()}`,
        },
      })),
    },
    {
      key: 'cost',
      label: t('models_ranking.columns.cost_per', { unit: distanceUnitLabel() }),
      cells: x.map((s, i) => ({
        value: `${t('models_ranking.row.electricity')} ${s.electricityCost.value}`,
        sub: [s.combustionCaption.value, s.driversPrice.value ?? ''].filter(Boolean),
        better: marks.value.cost[i],
      })),
    },
    {
      key: 'range',
      label: t('models_ranking.columns.range'),
      cells: x.map((s, i) => ({
        value: `${s.rangeValue.value} ${distanceUnitLabel()}`,
        sub: s.ladderLine.value ? [s.ladderLine.value] : [],
        better: marks.value.range[i],
      })),
    },
  ]
  for (const key of FACT_ORDER) {
    const values = x.map(s => s.facts.value.find(f => f.key === key)?.value ?? null)
    if (values.every(v => v == null)) continue
    rows.push({
      key,
      label: key === 'consumption' ? t('compare.row_seasons') : t(`models_ranking.detail.fact_${key}`),
      cells: values.map(v => ({ value: v ?? '–', sub: [], better: false })),
    })
  }
  return rows
})

// ── SEO: index, follow; the title keeps "A vs. B" for search ──────────────────
const BASE_URL = 'https://ev-monitor.net'
useHead(computed(() => {
  const names = items.value.map(fullName).join(' vs. ')
  const title = names ? `${names} – ${t('compare.breadcrumb_current')} | EV Monitor` : t('compare.head_title_fallback')
  const description = t('compare.head_desc', { names })
  const canonical = `${BASE_URL}${route.fullPath}`
  return {
    title,
    meta: [
      { name: 'description', content: description },
      { name: 'robots', content: robots.value },
      { property: 'og:title', content: title },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonical },
      { property: 'og:image', content: `${BASE_URL}/og-image.png` },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: title },
      { name: 'twitter:description', content: description },
    ],
    link: [{ rel: 'canonical', href: canonical }],
  }
}))
</script>

<style scoped>
.slide-down-enter-active, .slide-down-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-down-enter-from, .slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
