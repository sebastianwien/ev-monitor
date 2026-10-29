<script setup lang="ts">
import { ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import type { CrawlProposal } from '../../api/adminChargingTariffService'

/** Ein Vorschlag des Tarif-Crawlers: was die Seite sagt, der Satz von dort, was Übernehmen bewirkt. */
const props = defineProps<{
  proposal: CrawlProposal
  /** Was anders ist oder was neu wäre, je ein Satz. */
  lines: string[]
  /** Haltegrund, der zur Vorsicht mahnt (Sprung, mehrere Preise). */
  warning: string | null
  effect: string
  link: string | null
  busy: boolean
}>()
const emit = defineEmits<{ decide: [how: 'apply' | 'reject'] }>()

const when = () => new Date(props.proposal.lastSeenAt)
  .toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <div class="flex flex-wrap gap-3 items-start justify-between">
    <div class="min-w-0 max-w-3xl space-y-1">
      <p v-for="l in lines" :key="l" class="text-sm text-gray-100">{{ l }}</p>
      <p v-if="warning" class="text-xs text-amber-300">Achtung: {{ warning }}</p>
      <p v-if="proposal.evidence" class="text-xs text-gray-400">
        Seite: <span class="italic">„{{ proposal.evidenceBefore }}<mark class="bg-amber-400/25 text-amber-100 not-italic rounded-sm px-0.5">{{ proposal.evidence }}</mark>{{ proposal.evidenceAfter }}“</span>
        <a v-if="link" data-testid="evidence-link" :href="link" target="_blank" rel="noopener noreferrer"
           class="ml-2 inline-flex items-center gap-1 text-indigo-400 hover:underline">an der Stelle öffnen<ArrowTopRightOnSquareIcon class="w-3.5 h-3.5" /></a>
      </p>
      <p class="text-xs text-gray-500">{{ effect }} {{ proposal.seenCount }}× gesehen, zuletzt {{ when() }}.</p>
    </div>
    <div class="flex gap-2 shrink-0">
      <button type="button" :disabled="busy" class="px-3 py-1.5 rounded-sm bg-indigo-600 text-white text-sm hover:bg-indigo-500 disabled:opacity-50" @click="emit('decide', 'apply')">Übernehmen</button>
      <button type="button" :disabled="busy" class="px-3 py-1.5 rounded-sm border border-gray-700 text-gray-300 text-sm hover:bg-gray-800 disabled:opacity-50" @click="emit('decide', 'reject')">Verwerfen</button>
    </div>
  </div>
</template>
