<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getSeoCampaigns, type SeoCampaignRow, type SeoPeriod } from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoUtmBuilder from './SeoUtmBuilder.vue'
import SeoChannelTable from './SeoChannelTable.vue'
import { GLOSSARY } from './seoGlossary'
import { CHANNEL_LABELS, channelOf } from './seoAnalysis'
import { num, pct } from './seoFormat'
import type { SeoColumn } from './seoTable'

const props = defineProps<{ days: SeoPeriod }>()

const rows = ref<SeoCampaignRow[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await getSeoCampaigns(props.days)
  } catch {
    error.value = 'Die Akquisitionsdaten konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

watch(() => props.days, load)
onMounted(load)

const sourceLabel = (s: string) => (s === 'Direct / None' ? 'Direkt (ohne Verweis)' : s)
const regPerThousand = (r: SeoCampaignRow) => (r.attributed && r.visitors ? (r.registrations / r.visitors) * 1000 : null)
const columns = computed<SeoColumn<SeoCampaignRow>[]>(() => [
  { key: 'source', label: 'Quelle', hint: 'Website oder utm_source, von der der Besuch kam', value: r => sourceLabel(r.source) },
  { key: 'channel', label: 'Channel', hint: `${GLOSSARY.channel.term}: ${GLOSSARY.channel.text}`, value: r => CHANNEL_LABELS[channelOf(r.source, r.utmMedium)] },
  { key: 'medium', label: 'Medium', value: r => r.utmMedium || null },
  { key: 'campaign', label: 'Kampagne', value: r => r.utmCampaign || null },
  { key: 'visitors', label: 'Besucher', hint: `${GLOSSARY.visitors.term}: ${GLOSSARY.visitors.text}`, source: 'Plausible', numeric: true, value: r => r.visitors, format: r => num(r.visitors) },
  { key: 'cta', label: 'Conversion Rate', hint: `${GLOSSARY.cta.term}: ${GLOSSARY.cta.text}`, source: 'Plausible', numeric: true, value: r => r.ctaRate, format: r => pct(r.ctaRate), csv: r => r.ctaRate * 100 },
  { key: 'registrations', label: 'Sign-ups', hint: 'Neue Konten aus dieser Quelle. "-" heißt: nicht zuordenbar, weil der Link keine UTM-Parameter trug.', source: 'DB', numeric: true, value: r => (r.attributed ? r.registrations : null), format: r => (r.attributed ? num(r.registrations) : '-') },
  { key: 'perThousand', label: 'Sign-ups je 1 000 Besucher', hint: 'Macht große und kleine Quellen vergleichbar', numeric: true, value: r => regPerThousand(r), format: r => (regPerThousand(r) == null ? '-' : (regPerThousand(r) as number).toFixed(1).replace('.', ',')) },
  { key: 'trials', label: 'Trials', hint: 'Davon haben ein Probeabo oder Abo begonnen', source: 'DB', numeric: true, value: r => (r.attributed ? r.trials : null), format: r => (r.attributed ? num(r.trials) : '-') },
])
</script>

<template>
  <div class="space-y-3" :class="{ 'opacity-60': loading }">
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <section v-if="rows.length" aria-labelledby="seo-acq-channels">
      <h3 id="seo-acq-channels" class="text-sm font-semibold text-gray-200 mb-2">Channels</h3>
      <SeoChannelTable :rows="rows" />
    </section>
    <SeoUtmBuilder />
    <h3 v-if="rows.length" class="text-sm font-semibold text-gray-200">Quellen und Kampagnen</h3>
    <SeoDataTable v-if="!error" :columns="columns" :rows="rows" :row-key="r => `${r.source}|${r.utmMedium}|${r.utmCampaign}`"
                  :search-keys="['source', 'medium', 'campaign']" search-placeholder="Quelle oder Kampagne suchen"
                  csv-name="seo-kampagnen" empty="Keine Besuche im Zeitraum.">
      <template #cell-registrations="{ row }">
        <span :class="row.attributed && row.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ row.attributed ? num(row.registrations) : '-' }}</span>
      </template>
    </SeoDataTable>
  </div>
</template>
