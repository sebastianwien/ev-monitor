<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getSeoCampaigns, type SeoCampaignRow, type SeoPeriod } from '../../../api/seoService'
import SeoDataTable from './SeoDataTable.vue'
import SeoUtmBuilder from './SeoUtmBuilder.vue'
import { GLOSSARY } from './seoGlossary'
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
    error.value = 'Die Kampagnen konnten nicht geladen werden.'
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
  { key: 'medium', label: 'Medium', value: r => r.utmMedium || null },
  { key: 'campaign', label: 'Kampagne', value: r => r.utmCampaign || null },
  { key: 'visitors', label: 'Besucher', hint: `${GLOSSARY.visitors.term}: ${GLOSSARY.visitors.text}`, numeric: true, value: r => r.visitors, format: r => num(r.visitors) },
  { key: 'cta', label: 'CTA-Rate', hint: `${GLOSSARY.cta.term}: ${GLOSSARY.cta.text}`, numeric: true, value: r => r.ctaRate, format: r => pct(r.ctaRate), csv: r => r.ctaRate * 100 },
  { key: 'registrations', label: 'Registr.', hint: 'Neue Konten aus dieser Quelle. "-" heißt: nicht zuordenbar, weil der Link keine UTM-Parameter trug.', numeric: true, value: r => (r.attributed ? r.registrations : null), format: r => (r.attributed ? num(r.registrations) : '-') },
  { key: 'perThousand', label: 'Registr. je 1 000 Besucher', hint: 'Macht große und kleine Quellen vergleichbar', numeric: true, value: r => regPerThousand(r), format: r => (regPerThousand(r) == null ? '-' : (regPerThousand(r) as number).toFixed(1).replace('.', ',')) },
  { key: 'trials', label: 'Trials', hint: 'Davon haben ein Probeabo oder Abo begonnen', numeric: true, value: r => (r.attributed ? r.trials : null), format: r => (r.attributed ? num(r.trials) : '-') },
])
</script>

<template>
  <div class="space-y-3" :class="{ 'opacity-60': loading }">
    <SeoUtmBuilder />
    <p v-if="error" class="text-red-400 text-sm">{{ error }}</p>
    <SeoDataTable v-else :columns="columns" :rows="rows" :row-key="r => `${r.source}|${r.utmMedium}|${r.utmCampaign}`"
                  :search-keys="['source', 'medium', 'campaign']" search-placeholder="Quelle oder Kampagne suchen"
                  csv-name="seo-kampagnen" empty="Keine Besuche im Zeitraum.">
      <template #cell-registrations="{ row }">
        <span :class="row.attributed && row.registrations ? 'text-emerald-300' : 'text-gray-500'">{{ row.attributed ? num(row.registrations) : '-' }}</span>
      </template>
    </SeoDataTable>
  </div>
</template>
