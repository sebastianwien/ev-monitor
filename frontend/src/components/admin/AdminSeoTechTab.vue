<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSeoAudit, triggerSeoAudit, type SeoAuditView, type SeoVitals } from '../../api/seoService'
import { CHECK_LABELS, checksPresent, filterAuditRows, type SeoAuditFilter } from './seoAudit'

const view = ref<SeoAuditView | null>(null)
const loading = ref(false)
const error = ref('')
const runMessage = ref('')
const filter = ref<SeoAuditFilter>('ALL')
const open = ref<Set<string>>(new Set())

async function load() {
  loading.value = true
  error.value = ''
  try {
    view.value = await getSeoAudit()
  } catch {
    error.value = 'Prüfberichte konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function startRun() {
  runMessage.value = ''
  try {
    const started = await triggerSeoAudit()
    runMessage.value = started
      ? 'Prüfung gestartet, alle Sitemap-URLs werden im Hintergrund geholt. In ein paar Minuten neu laden.'
      : 'Keine Prüfung gestartet: SEO_AUDIT_BASE_URL fehlt oder ein Lauf ist noch aktiv.'
  } catch {
    runMessage.value = 'Prüfung konnte nicht gestartet werden.'
  }
}

function toggle(path: string) {
  const next = new Set(open.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  open.value = next
}

onMounted(load)

const rows = computed(() => filterAuditRows(view.value?.rows ?? [], filter.value))
const chips = computed(() => checksPresent(view.value?.rows ?? []))
const hasVitals = computed(() => (view.value?.rows ?? []).some(r => r.vitals))

const tiles = computed(() => {
  const s = view.value?.summary
  if (!s) return []
  return [
    { label: 'URLs geprüft', value: s.checked, bad: false },
    { label: 'mit Fehler', value: s.withErrors, bad: s.withErrors > 0 },
    { label: 'Prerender-Fehler', value: s.prerenderFailed, bad: s.prerenderFailed > 0 },
    { label: 'mit kaputtem Link', value: s.brokenLinkPages, bad: s.brokenLinkPages > 0 },
    { label: 'verwaist', value: s.orphans, bad: false },
  ]
})

const date = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleDateString('de-DE') : '-')
const dateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'
const seconds = (ms: number | null) => (ms == null ? '-' : `${(ms / 1000).toFixed(1).replace('.', ',')} s`)
const ms = (v: number | null) => (v == null ? '-' : `${v} ms`)
const cls = (v: number | null) => (v == null ? '-' : v.toFixed(2).replace('.', ','))
const vitalsTitle = (v: SeoVitals) => (v.source === 'FIELD' ? 'Felddaten echter Chrome-Nutzer (28 Tage)' : 'Lighthouse-Messung, keine Felddaten vorhanden')

const btn = (active: boolean) => [
  'px-3 py-1.5 rounded-sm text-xs font-medium transition',
  active ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-300 hover:bg-gray-700',
]
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-start justify-between gap-3 flex-wrap">
      <p class="text-xs text-gray-400">
        <template v-if="view?.day">
          Prüftag {{ date(view.day) }}, Lauf {{ dateTime(view.lastRunAt) }}. Jede URL der Sitemap so geholt, wie Googlebot sie bekommt (über den Prerender).
        </template>
        <template v-else-if="view && !view.enabled">On-Page-Check aus (SEO_AUDIT_BASE_URL fehlt).</template>
        <template v-else-if="view">Noch kein Prüflauf. Der nächste läuft nachts um 5 Uhr.</template>
      </p>
      <button type="button" class="px-3 py-1.5 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 border border-gray-700"
              :disabled="view !== null && !view.enabled" @click="startRun">
        Jetzt prüfen
      </button>
    </div>
    <p v-if="runMessage" class="text-xs text-gray-300">{{ runMessage }}</p>
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <p v-else-if="loading && !view" class="text-gray-400 text-sm">Lädt...</p>

    <template v-else-if="view?.day">
      <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div v-for="t in tiles" :key="t.label" class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div :class="['text-xl font-bold tabular-nums', t.bad ? 'text-red-300' : 'text-indigo-300']">{{ t.value }}</div>
          <div class="text-xs text-gray-300 mt-1">{{ t.label }}</div>
        </div>
      </div>

      <div class="flex gap-1 flex-wrap">
        <button type="button" :class="btn(filter === 'ALL')" @click="filter = 'ALL'">Alle</button>
        <button type="button" :class="btn(filter === 'ERRORS')" @click="filter = 'ERRORS'">Nur Fehler</button>
        <button v-for="c in chips" :key="c" type="button" :class="btn(filter === c)" :title="CHECK_LABELS[c].hint" @click="filter = c">
          {{ CHECK_LABELS[c].label }}
        </button>
      </div>

      <p v-if="!rows.length" class="text-sm text-gray-500">Keine Seite mit diesem Befund.</p>
      <div v-else class="overflow-x-auto bg-gray-900 rounded-sm border border-gray-800">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-xs text-gray-400 border-b border-gray-800">
              <th class="text-left px-3 py-2 font-medium">Pfad</th>
              <th class="text-right px-3 py-2 font-medium" title="HTTP-Status für Googlebot, 0 = nicht erreichbar">Status</th>
              <th class="text-right px-3 py-2 font-medium" title="100 minus 20 je Fehler minus 5 je Hinweis">Score</th>
              <th class="text-right px-3 py-2 font-medium">Fehler</th>
              <th class="text-right px-3 py-2 font-medium">Hinweise</th>
              <template v-if="hasVitals">
                <th class="text-right px-3 py-2 font-medium" title="Largest Contentful Paint, mobil, Ziel bis 2,5 s">LCP</th>
                <th class="text-right px-3 py-2 font-medium" title="Cumulative Layout Shift, mobil, Ziel bis 0,1">CLS</th>
                <th class="text-right px-3 py-2 font-medium" title="Interaction to Next Paint, mobil, Ziel bis 200 ms">INP</th>
              </template>
              <th class="text-left px-3 py-2 font-medium">Befunde</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="r in rows" :key="r.path">
              <tr class="border-b border-gray-800/60 cursor-pointer hover:bg-gray-800/40" @click="toggle(r.path)">
                <td class="px-3 py-1.5 font-mono text-gray-200 whitespace-nowrap">
                  <a :href="r.path" target="_blank" rel="noopener" class="hover:underline" @click.stop>{{ r.path }}</a>
                </td>
                <td class="px-3 py-1.5 text-right tabular-nums" :class="r.httpStatus === 200 ? 'text-gray-300' : 'text-red-300'">{{ r.httpStatus }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.score }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums" :class="r.errors ? 'text-red-300' : 'text-gray-500'">{{ r.errors || '-' }}</td>
                <td class="px-3 py-1.5 text-right tabular-nums" :class="r.warnings ? 'text-amber-300' : 'text-gray-500'">{{ r.warnings || '-' }}</td>
                <template v-if="hasVitals">
                  <td class="px-3 py-1.5 text-right tabular-nums text-gray-300" :title="r.vitals ? vitalsTitle(r.vitals) : ''">{{ r.vitals ? seconds(r.vitals.lcpMs) : '' }}</td>
                  <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.vitals ? cls(r.vitals.cls) : '' }}</td>
                  <td class="px-3 py-1.5 text-right tabular-nums text-gray-300">{{ r.vitals ? ms(r.vitals.inpMs) : '' }}</td>
                </template>
                <td class="px-3 py-1.5">
                  <span v-if="!r.findings.length" class="text-xs text-emerald-400">ok</span>
                  <span v-for="c in [...new Set(r.findings.map(f => f.check))]" :key="c"
                        :class="['inline-block text-xs px-2 py-0.5 rounded-full mr-1',
                                 r.findings.some(f => f.check === c && f.severity === 'ERROR') ? 'bg-red-900/60 text-red-200' : 'bg-amber-900/60 text-amber-200']">
                    {{ CHECK_LABELS[c].label }}
                  </span>
                </td>
              </tr>
              <tr v-if="open.has(r.path) && r.findings.length" class="border-b border-gray-800/60 bg-gray-950/40">
                <td :colspan="hasVitals ? 9 : 6" class="px-3 py-2">
                  <ul class="space-y-1">
                    <li v-for="(f, i) in r.findings" :key="i" class="text-xs flex gap-2">
                      <span :class="f.severity === 'ERROR' ? 'text-red-300' : 'text-amber-300'" class="shrink-0 w-16">{{ f.severity === 'ERROR' ? 'Fehler' : 'Hinweis' }}</span>
                      <span class="text-gray-400 shrink-0 w-28">{{ CHECK_LABELS[f.check].label }}</span>
                      <span class="text-gray-200 break-all">{{ f.message }}</span>
                    </li>
                  </ul>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
