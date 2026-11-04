<script setup lang="ts">
import { computed } from 'vue'
import type { SeoCampaignRow } from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import { summarizeChannels, type ChannelSummary } from './seoAnalysis'
import { GLOSSARY } from './seoGlossary'
import { dec, num } from './seoFormat'
import type { SeoColumn } from './seoTable'

/** Besucher und Sign-ups je Channel (Default Channel Group), aus den Quellzeilen der Akquisition. */
const props = defineProps<{ rows: SeoCampaignRow[] }>()

const channels = computed(() => summarizeChannels(props.rows))
const columns: SeoColumn<ChannelSummary>[] = [
  { key: 'channel', label: 'Channel', hint: `${GLOSSARY.channel.term}: ${GLOSSARY.channel.text}`, value: r => r.label },
  { key: 'visitors', label: 'Besucher', hint: GLOSSARY.visitors.text, source: 'Plausible', numeric: true, value: r => r.visitors, format: r => num(r.visitors) },
  { key: 'registrations', label: 'Sign-ups', hint: 'Nur Sign-ups, die sich zuordnen lassen: Links mit UTM-Parametern und die Google-Suche.', source: 'DB', numeric: true,
    value: r => (r.perThousand == null ? null : r.registrations), format: r => (r.perThousand == null ? '-' : num(r.registrations)) },
  { key: 'perThousand', label: 'Sign-ups je 1 000 Besucher', hint: 'Macht große und kleine Channels vergleichbar. Gerechnet nur über Besucher mit zuordenbaren Sign-ups.', numeric: true,
    value: r => r.perThousand, format: r => (r.perThousand == null ? '-' : dec(r.perThousand)) },
]
</script>

<template>
  <SeoDataTable :columns="columns" :rows="channels" :row-key="r => r.channel" csv-name="seo-channels"
                :initial-sort="{ key: 'perThousand', dir: 'desc' }" empty="Keine Besuche im Zeitraum." />
</template>
