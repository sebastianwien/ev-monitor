<script setup lang="ts">
import { computed, ref } from 'vue'
import { ClipboardDocumentIcon } from '@heroicons/vue/24/outline'
import { buildUtmLink } from './seoUtm'

/** Link-Baukasten: erzeugt Links mit UTM-Parametern in einheitlicher Schreibweise. */
const MEDIA = [
  { key: 'forum', label: 'Forum-Beitrag' },
  { key: 'social', label: 'Social Media' },
  { key: 'email', label: 'E-Mail oder Newsletter' },
  { key: 'partner', label: 'Partner oder Kooperation' },
  { key: 'cpc', label: 'Bezahlte Anzeige (Google Ads)' },
  { key: 'print', label: 'Druck, QR-Code' },
]

const url = ref('https://ev-monitor.net/')
const source = ref('')
const medium = ref('forum')
const campaign = ref('')
const copied = ref(false)

const link = computed(() => buildUtmLink({ url: url.value, source: source.value, medium: medium.value, campaign: campaign.value }))
const urlInvalid = computed(() => url.value.trim() !== '' && source.value.trim() !== '' && !link.value)

async function copy() {
  if (!link.value) return
  try {
    await navigator.clipboard.writeText(link.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    copied.value = false
  }
}

const field = 'w-full bg-gray-950 border border-gray-700 rounded-sm px-2 py-1.5 text-sm text-gray-200 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500'
</script>

<template>
  <details class="bg-gray-900 rounded-sm border border-gray-800 p-4">
    <summary class="cursor-pointer text-sm font-semibold text-gray-200">Kampagnen-Link bauen</summary>
    <p class="text-xs text-gray-400 mt-2">Jeder Link auf ev-monitor.net, den wir irgendwo posten oder verschicken, braucht diese Zusätze. Nur dann erscheint die Kampagne unten mit Registrierungen.</p>
    <div class="grid md:grid-cols-2 gap-3 mt-3">
      <label class="block text-xs text-gray-300">Ziel-Seite
        <input v-model="url" type="url" :class="field" class="mt-1 font-mono" />
      </label>
      <label class="block text-xs text-gray-300">Wo wird der Link gepostet? (Quelle)
        <input v-model="source" type="text" placeholder="z. B. goingelectric, vw-id-forum, newsletter" :class="field" class="mt-1" />
      </label>
      <label class="block text-xs text-gray-300">Art (Medium)
        <select v-model="medium" :class="field" class="mt-1">
          <option v-for="m in MEDIA" :key="m.key" :value="m.key">{{ m.label }}</option>
        </select>
      </label>
      <label class="block text-xs text-gray-300">Anlass (Kampagne, optional)
        <input v-model="campaign" type="text" placeholder="z. B. winter-reichweite-2026" :class="field" class="mt-1" />
      </label>
    </div>
    <p v-if="urlInvalid" class="text-xs text-red-300 mt-2">Die Ziel-Seite muss eine vollständige Adresse auf ev-monitor.net sein.</p>
    <div v-if="link" class="mt-3 flex items-center gap-2 flex-wrap">
      <code class="flex-1 min-w-0 break-all bg-gray-950 border border-gray-800 rounded-sm px-2 py-1.5 text-xs text-emerald-300">{{ link }}</code>
      <button type="button" class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-sm text-xs font-medium bg-indigo-600 text-white hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400" @click="copy">
        <ClipboardDocumentIcon class="h-4 w-4" aria-hidden="true" />
        {{ copied ? 'Kopiert' : 'Kopieren' }}
      </button>
    </div>
    <p class="text-xs text-gray-500 mt-3">Schreibweise wird vereinheitlicht: klein, ohne Umlaute, Leerzeichen als Bindestrich. Dieselbe Quelle immer gleich nennen, sonst zerfällt sie in mehrere Zeilen.</p>
  </details>
</template>
