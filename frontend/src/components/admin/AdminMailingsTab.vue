<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'
import {
  getAdminMailingPreview,
  getAdminMailingRecent,
  getAdminMailings,
  goalRate,
  type AdminMailing,
  type AdminMailingSend,
} from '../../api/adminMailingService'

const PERIODS = [7, 30, 90, 365]
const days = ref(30)
const mailings = ref<AdminMailing[]>([])
const loading = ref(false)
const error = ref('')

const openKey = ref<string | null>(null)
const preview = ref('')
const recent = ref<AdminMailingSend[]>([])
const detailLoading = ref(false)
const detailError = ref('')

const groups = computed(() => [
  { title: 'Lebenszyklus', hint: 'Sollen etwas bewirken, gemessen am Ziel-Goal in Plausible.', items: mailings.value.filter(m => m.kind === 'LEBENSZYKLUS') },
  { title: 'Pflicht', hint: 'Gehen an jeden, der sie auslöst, ohne Ziel-Goal.', items: mailings.value.filter(m => m.kind === 'PFLICHT') },
])

const fmtDate = (iso: string | null) => (iso ? new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '-')

async function load() {
  loading.value = true
  error.value = ''
  try {
    mailings.value = await getAdminMailings(days.value)
  } catch {
    error.value = 'Mailings konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function toggle(key: string) {
  if (openKey.value === key) {
    openKey.value = null
    return
  }
  openKey.value = key
  preview.value = ''
  recent.value = []
  detailError.value = ''
  detailLoading.value = true
  try {
    const [html, sends] = await Promise.all([getAdminMailingPreview(key), getAdminMailingRecent(key)])
    if (openKey.value !== key) return
    preview.value = html
    recent.value = sends
  } catch {
    if (openKey.value === key) detailError.value = 'Vorschau oder Empfänger konnten nicht geladen werden.'
  } finally {
    if (openKey.value === key) detailLoading.value = false
  }
}

watch(days, load)
onMounted(load)
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
      <h2 class="text-lg font-semibold text-white">Mailings</h2>
      <div class="flex gap-1 bg-gray-900 rounded-sm p-1" role="group" aria-label="Zeitraum">
        <button
          v-for="p in PERIODS"
          :key="p"
          type="button"
          :class="['px-3 py-1 rounded-sm text-sm', days === p ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200']"
          @click="days = p"
        >
          {{ p }} Tage
        </button>
      </div>
    </div>

    <p class="text-sm text-gray-400 mb-6 max-w-3xl">
      Versand aus dem eigenen Protokoll (seit Einführung, höchstens 12 Monate). Besuche und Goals aus Plausible über
      <span class="font-mono">utm_campaign</span>, Summe der Tage im Zeitraum. Die Doku zu Zweck und Goal steht in
      <span class="font-mono">docs/features/mailings.md</span>.
    </p>

    <p v-if="error" class="text-red-400 text-sm mb-4">{{ error }}</p>
    <p v-else-if="loading && !mailings.length" class="text-gray-400 text-sm">Lädt...</p>

    <section v-for="g in groups" :key="g.title" class="mb-8">
      <h3 class="text-base font-semibold text-white">{{ g.title }}</h3>
      <p class="text-xs text-gray-500 mb-3">{{ g.hint }}</p>

      <div class="space-y-2">
        <article v-for="m in g.items" :key="m.key" class="bg-gray-900 rounded-sm">
          <button
            type="button"
            class="w-full text-left p-4 grid gap-3 md:grid-cols-[minmax(0,2fr)_repeat(4,minmax(0,1fr))_auto] md:items-center"
            :aria-expanded="openKey === m.key"
            @click="toggle(m.key)"
          >
            <span class="min-w-0">
              <span class="block text-sm font-medium text-white">{{ m.title }}</span>
              <span class="block text-xs text-gray-500 font-mono truncate">{{ m.key }}</span>
            </span>
            <span class="text-sm tabular-nums">
              <span class="block text-xs text-gray-500">Versendet</span>
              {{ m.sent.toLocaleString('de-DE') }}
              <span v-if="m.failed" class="text-red-400 text-xs">({{ m.failed }} fehlgeschlagen)</span>
            </span>
            <span class="text-sm tabular-nums">
              <span class="block text-xs text-gray-500">Zuletzt</span>
              {{ fmtDate(m.lastSentAt) }}
            </span>
            <span class="text-sm tabular-nums">
              <span class="block text-xs text-gray-500">Besuche aus Mail</span>
              {{ m.visits.toLocaleString('de-DE') }}
            </span>
            <span class="text-sm tabular-nums">
              <span class="block text-xs text-gray-500">Goal <span v-if="m.goal" class="font-mono">{{ m.goal }}</span></span>
              <template v-if="m.goal">
                {{ m.goalVisitors.toLocaleString('de-DE') }}
                <span v-if="goalRate(m.goalVisitors, m.visits) !== null" class="text-gray-400 text-xs" title="Goal-Besucher geteilt durch Besuche, beides als Summe der Tage: eine Näherung">(ca. {{ goalRate(m.goalVisitors, m.visits) }} %)</span>
              </template>
              <span v-else class="text-gray-500">nur Info</span>
            </span>
            <component :is="openKey === m.key ? ChevronUpIcon : ChevronDownIcon" class="w-5 h-5 text-gray-400 justify-self-end" aria-hidden="true" />
          </button>

          <div v-if="openKey === m.key" class="border-t border-gray-800 p-4 space-y-4">
            <dl class="grid gap-2 text-sm md:grid-cols-3">
              <div><dt class="text-xs text-gray-500">Zweck</dt><dd>{{ m.purpose }}</dd></div>
              <div><dt class="text-xs text-gray-500">Auslöser</dt><dd>{{ m.trigger }}</dd></div>
              <div><dt class="text-xs text-gray-500">Empfänger</dt><dd>{{ m.audience }}</dd></div>
            </dl>

            <p v-if="detailError" class="text-red-400 text-sm">{{ detailError }}</p>
            <p v-else-if="detailLoading" class="text-gray-400 text-sm">Lädt...</p>

            <div v-else class="grid gap-4 lg:grid-cols-2">
              <div class="min-w-0">
                <h4 class="text-xs text-gray-500 mb-2">Vorschau mit Beispieldaten</h4>
                <iframe
                  :srcdoc="preview"
                  sandbox=""
                  :title="`Vorschau ${m.title}`"
                  class="w-full h-[32rem] bg-white rounded-sm"
                />
              </div>
              <div class="min-w-0">
                <h4 class="text-xs text-gray-500 mb-2">Letzte Empfänger</h4>
                <div class="overflow-x-auto max-h-[32rem]">
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="text-left text-xs text-gray-500">
                        <th class="py-1 pr-3 font-medium">Zeit</th>
                        <th class="py-1 pr-3 font-medium">Empfänger</th>
                        <th class="py-1 font-medium">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(r, i) in recent" :key="i" class="border-t border-gray-800">
                        <td class="py-1 pr-3 tabular-nums whitespace-nowrap">{{ fmtDate(r.sentAt) }}</td>
                        <td class="py-1 pr-3 break-all">{{ r.email ?? 'ohne Konto' }}</td>
                        <td class="py-1" :class="r.sent ? 'text-gray-300' : 'text-red-400'">{{ r.sent ? 'gesendet' : 'fehlgeschlagen' }}</td>
                      </tr>
                      <tr v-if="!recent.length">
                        <td colspan="3" class="py-2 text-gray-500">Im Protokoll noch kein Versand.</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>
