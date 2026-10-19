<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getSeoModels, type SeoModelFilter, type SeoModelLevel, type SeoModelRow, type SeoPeriod } from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoFindingBadge from './SeoFindingBadge.vue'
import SeoFindingNote from './SeoFindingNote.vue'
import { FINDINGS } from './seoHelp'
import { GLOSSARY } from './seoGlossary'
import { num, pct, pos } from './seoFormat'
import type { SeoColumn } from './seoTable'

const props = defineProps<{ days: SeoPeriod }>()
const filter = defineModel<SeoModelFilter>('filter', { required: true })
const level = defineModel<SeoModelLevel>('level', { required: true })

const FILTERS: SeoModelFilter[] = ['ALL', 'CONTENT_GAP', 'INVISIBLE']
const rows = ref<SeoModelRow[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await getSeoModels(props.days, level.value, level.value === 'BRAND' ? 'ALL' : filter.value)
  } catch {
    error.value = 'Die Modellseiten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

watch(() => [props.days, level.value, filter.value], load)
onMounted(load)

const g = (k: string) => `${GLOSSARY[k].term}: ${GLOSSARY[k].text}`
const columns = computed<SeoColumn<SeoModelRow>[]>(() => [
  { key: 'brand', label: 'Marke', value: r => r.brand },
  ...(level.value === 'MODEL' ? [{ key: 'model', label: 'Modell', value: (r: SeoModelRow) => r.model }] : []),
  { key: 'impressions', label: 'Impressionen', hint: g('impressions'), numeric: true, value: r => r.impressions, format: r => num(r.impressions) },
  { key: 'clicks', label: 'Klicks', hint: g('clicks'), numeric: true, value: r => r.clicks, format: r => num(r.clicks) },
  { key: 'ctr', label: 'Klickrate', hint: g('ctr'), numeric: true, value: r => (r.impressions ? r.ctr : null), format: r => (r.impressions ? pct(r.ctr) : '-'), csv: r => (r.impressions ? r.ctr * 100 : null) },
  { key: 'position', label: 'Position', hint: g('position'), numeric: true, value: r => r.avgPosition || null, format: r => pos(r.avgPosition) },
  { key: 'registrations', label: 'Registr.', hint: g('registrations'), numeric: true, value: r => r.registrations, format: r => (r.registrations ? num(r.registrations) : '-') },
  { key: 'cars', label: 'Autos', hint: 'Aktive Autos dieses Modells bei uns', numeric: true, value: r => r.cars, format: r => num(r.cars) },
  { key: 'logs', label: 'Ladevorgänge', hint: 'Ladevorgänge in der Statistik, gesamt seit Start', numeric: true, value: r => r.logs, format: r => num(r.logs) },
  { key: 'finding', label: 'Befund', value: r => (r.finding ? FINDINGS[r.finding]?.label ?? r.finding : null) },
])

const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition focus:outline-none focus:ring-2 focus:ring-indigo-500',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]
</script>

<template>
  <div class="space-y-3" :class="{ 'opacity-60': loading }">
    <div class="flex gap-3 flex-wrap">
      <div class="flex gap-1" role="group" aria-label="Ebene">
        <button type="button" :class="btn(level === 'MODEL')" :aria-pressed="level === 'MODEL'" @click="level = 'MODEL'">Je Modell</button>
        <button type="button" :class="btn(level === 'BRAND')" :aria-pressed="level === 'BRAND'" @click="level = 'BRAND'">Je Marke</button>
      </div>
      <div v-if="level === 'MODEL'" class="flex gap-1 flex-wrap" role="group" aria-label="Befund">
        <button v-for="f in FILTERS" :key="f" type="button" :class="btn(filter === f)" :aria-pressed="filter === f" @click="filter = f">{{ f === 'ALL' ? 'Alle' : FINDINGS[f].label }}</button>
      </div>
    </div>
    <SeoFindingNote :finding="level === 'MODEL' && filter !== 'ALL' ? filter : null" />
    <p class="text-xs text-gray-500">Nur die deutsche Website. Autos und Ladevorgänge zählen alle echten Nutzer seit Start, unabhängig vom Zeitraum.<template v-if="level === 'BRAND'"> Je Marke zählen alle Modellseiten plus die Markenseite.</template></p>
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <SeoDataTable v-else :columns="columns" :rows="rows" :row-key="r => r.path" :search-keys="['brand', 'model']" search-placeholder="Marke oder Modell suchen"
                  :csv-name="level === 'MODEL' ? 'seo-modellseiten' : 'seo-marken'" empty="Keine Modellseiten im Zeitraum.">
      <template #cell-model="{ row }">
        <a :href="row.path" target="_blank" rel="noopener" class="hover:underline">{{ row.model }}</a>
      </template>
      <template #cell-brand="{ row }">
        <a v-if="level === 'BRAND'" :href="row.path" target="_blank" rel="noopener" class="hover:underline">{{ row.brand }}</a>
        <template v-else>{{ row.brand }}</template>
      </template>
      <template #cell-registrations="{ row }">
        <span :class="row.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ row.registrations ? num(row.registrations) : '-' }}</span>
      </template>
      <template #cell-finding="{ row }">
        <SeoFindingBadge v-if="row.finding" :finding="row.finding" />
      </template>
    </SeoDataTable>
  </div>
</template>
