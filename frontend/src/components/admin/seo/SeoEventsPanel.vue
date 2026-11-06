<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSeoEvents, type SeoEventStats } from '../../../api/seoService'
import { EVENT_AREAS, type EventArea } from '../../../services/analyticsEvents'
import { eventRows, STATUS_HINT, STATUS_LABEL, type EventRow, type EventStatus } from './seoEvents'
import { num } from './seoFormat'

/** Alle Plausible-Events mit Bedeutung aus dem Katalog und Zahlen der letzten 30 Tage. */
const stats = ref<SeoEventStats | null>(null)
const error = ref('')
const loading = ref(false)
const filter = ref<EventStatus | 'all'>('all')
const search = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    stats.value = await getSeoEvents()
  } catch {
    error.value = 'Die Events konnten nicht aus Plausible geladen werden.'
  } finally {
    loading.value = false
  }
}
onMounted(load)

const rows = computed(() => eventRows(stats.value?.events ?? []))
const counts = computed(() => {
  const c: Record<EventStatus, number> = { ok: 0, no_goal: 0, silent: 0, unknown: 0 }
  for (const r of rows.value) c[r.status]++
  return c
})
const visible = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter(r => (filter.value === 'all' || r.status === filter.value)
    && (!q || `${r.name} ${r.title} ${r.question ?? ''} ${r.where ?? ''}`.toLowerCase().includes(q)))
})
const groups = computed(() => {
  const order = [...(Object.keys(EVENT_AREAS) as EventArea[]), null]
  return order
    .map(area => ({ area, label: area ? EVENT_AREAS[area] : 'Nicht im Katalog', rows: visible.value.filter(r => r.area === area).sort((a, b) => b.visitors - a.visitors) }))
    .filter(g => g.rows.length)
})

const FILTERS: Array<{ key: EventStatus | 'all'; label: string }> = [
  { key: 'all', label: 'Alle' },
  { key: 'no_goal', label: STATUS_LABEL.no_goal },
  { key: 'silent', label: STATUS_LABEL.silent },
  { key: 'unknown', label: STATUS_LABEL.unknown },
]
const BADGE: Record<EventStatus, string> = {
  ok: 'bg-emerald-900/60 text-emerald-200',
  no_goal: 'bg-amber-900/60 text-amber-200',
  silent: 'bg-gray-700 text-gray-300',
  unknown: 'bg-red-900/60 text-red-200',
}
const chip = (on: boolean) => `px-3 py-1.5 rounded-sm text-xs font-medium ${on ? 'bg-indigo-600 text-white' : 'bg-gray-700 text-gray-200 hover:bg-gray-600'}`
const filterCount = (k: EventStatus | 'all') => (k === 'all' ? rows.value.length : counts.value[k])
const propsList = (r: EventRow) => Object.entries(r.props ?? {})
</script>

<template>
  <div class="space-y-4" :class="{ 'opacity-60': loading }">
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="stats && !stats.enabled" class="text-sm text-gray-400">Plausible ist hier nicht angebunden. Die Texte stehen trotzdem, die Zahlen fehlen.</p>
    <p v-else-if="stats" class="text-xs text-gray-400">Zahlen vom {{ stats.from }} bis {{ stats.to }}, live aus Plausible, bis zu einer Stunde alt. Texte aus dem Katalog im Code (analyticsEvents.ts).</p>

    <div class="flex flex-wrap items-center gap-2">
      <button v-for="f in FILTERS" :key="f.key" type="button" :class="chip(filter === f.key)" :aria-pressed="filter === f.key" @click="filter = f.key">
        {{ f.label }} <span class="opacity-70">{{ filterCount(f.key) }}</span>
      </button>
      <input v-model="search" type="search" placeholder="Event oder Bedeutung suchen" class="ml-auto w-56 rounded-sm bg-gray-800 border border-gray-600 px-2 py-1.5 text-sm text-gray-100" aria-label="Events suchen" />
    </div>

    <section v-for="g in groups" :key="g.area ?? 'none'" :aria-label="g.label">
      <h3 class="text-sm font-semibold text-gray-200 mb-2">{{ g.label }}</h3>
      <ul class="divide-y divide-gray-700 rounded-sm border border-gray-700">
        <li v-for="r in g.rows" :key="r.name" class="px-3 py-2.5">
          <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span class="font-medium text-gray-100">{{ r.title }}</span>
            <span class="rounded-sm px-1.5 py-0.5 text-[11px] font-semibold" :class="BADGE[r.status]" :title="STATUS_HINT[r.status]">{{ STATUS_LABEL[r.status] }}</span>
            <span class="ml-auto text-sm tabular-nums text-gray-200" :title="`${num(r.events)} Auslösungen`">{{ num(r.visitors) }} Besucher</span>
          </div>
          <p v-if="r.question" class="mt-0.5 text-sm text-gray-300">{{ r.question }}</p>
          <p class="mt-0.5 text-xs text-gray-500">
            <code class="text-gray-400">{{ r.name }}</code><template v-if="r.where"> · {{ r.where }}</template>
            <template v-if="propsList(r).length"> · <span v-for="([k, v], i) in propsList(r)" :key="k"><template v-if="i">, </template><code class="text-gray-400">{{ k }}</code> {{ v }}</span></template>
          </p>
          <p v-if="r.status !== 'ok'" class="mt-0.5 text-xs text-gray-500">{{ STATUS_HINT[r.status] }}</p>
        </li>
      </ul>
    </section>
    <p v-if="!groups.length && !error" class="text-sm text-gray-400">Keine Events für diesen Filter.</p>
  </div>
</template>
