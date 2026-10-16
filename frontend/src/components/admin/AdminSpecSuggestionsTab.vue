<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminVehicleSpecService, apiErrorText, type SpecCommand, type SpecRow } from '../../api/adminVehicleSpecService'

/**
 * Vorschläge für Spec-Zeilen aus einer lokalen Datei `public/spec-vorschlaege.json` (gitignored, nur auf dem
 * Entwicklungsrechner vorhanden). Woher die Datei kommt, weiß dieser Code nicht. Ohne Datei zeigt der Tab nur einen Hinweis.
 */
interface Suggestion { label: string; availableFrom: string | null; availableTo: string | null; sourceUrl: string | null }

const suggestions = ref<Record<string, Suggestion[]> | null>(null)
const rows = ref<SpecRow[]>([])
const applied = ref(new Set<string>())
const onlyOpen = ref(true)
const busy = ref<string | null>(null)
const error = ref('')
const loading = ref(true)

onMounted(async () => {
    try {
        const res = await fetch('/spec-vorschlaege.json', { cache: 'no-store' })
        if (!res.ok) return
        suggestions.value = await res.json()
        const overview = await adminVehicleSpecService.overview()
        rows.value = overview.models.flatMap(m => m.rows)
    } catch (err) {
        error.value = apiErrorText(err, 'Laden fehlgeschlagen.')
    } finally {
        loading.value = false
    }
})

const visible = computed(() => rows.value.filter(r => suggestions.value?.[r.id]?.length
    && !applied.value.has(r.id)
    && (!onlyOpen.value || !r.availableFrom || !r.sourceUrl)))

function toCommand(r: SpecRow): SpecCommand {
    return {
        model: r.carModel, batteryCapacityKwh: r.batteryCapacityKwh, netBatteryCapacityKwh: r.netBatteryCapacityKwh,
        officialRangeKm: r.officialRangeKm, officialConsumptionKwhPer100km: r.officialConsumptionKwhPer100km,
        variantName: r.variantName ?? '', trimLevel: r.trimLevel ?? '', availableFrom: r.availableFrom, availableTo: r.availableTo,
        sourceUrl: r.sourceUrl ?? '', ratingSource: r.ratingSource,
    }
}

async function apply(r: SpecRow, s: Suggestion) {
    busy.value = r.id
    error.value = ''
    try {
        const result = await adminVehicleSpecService.update(r.id, {
            ...toCommand(r),
            availableFrom: s.availableFrom ?? r.availableFrom,
            availableTo: s.availableTo ?? r.availableTo,
            sourceUrl: s.sourceUrl ?? r.sourceUrl ?? '',
        })
        rows.value = rows.value.map(x => x.id === r.id ? result.row : x)
        applied.value = new Set([...applied.value, r.id])
    } catch (err) {
        error.value = `${r.variantName ?? r.carModel}: ${apiErrorText(err, 'Speichern fehlgeschlagen.')}`
    } finally {
        busy.value = null
    }
}

const period = (from: string | null, to: string | null) => from ? `${from} bis ${to ?? 'offen'}` : 'kein Zeitraum'
</script>

<template>
  <div class="space-y-4">
    <p v-if="loading" class="text-sm text-gray-400">Lade …</p>
    <p v-else-if="!suggestions" class="text-sm text-gray-400">Keine Vorschlagsdatei gefunden. Dieser Tab arbeitet nur mit einer lokalen Datei.</p>
    <template v-else>
      <div class="flex flex-wrap items-center gap-3 text-sm">
        <label class="flex items-center gap-2"><input v-model="onlyOpen" type="checkbox" /> nur Zeilen ohne Zeitraum oder Quelle</label>
        <span class="text-gray-400">{{ visible.length }} offen · {{ applied.size }} übernommen</span>
      </div>
      <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>
      <ul class="space-y-2">
        <li v-for="r in visible" :key="r.id" class="bg-gray-800 rounded-sm p-3 text-sm">
          <div class="flex flex-wrap items-baseline gap-x-3">
            <span class="font-medium">{{ r.carBrand }} {{ r.carModel }} · {{ r.variantName ?? 'ohne Name' }}</span>
            <span class="text-gray-400">{{ r.batteryCapacityKwh }} / {{ r.netBatteryCapacityKwh ?? '?' }} kWh · {{ period(r.availableFrom, r.availableTo) }}</span>
            <a v-if="r.sourceUrl" :href="r.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-300 underline">Quelle</a>
          </div>
          <ul class="mt-2 space-y-1">
            <li v-for="(s, i) in suggestions[r.id]" :key="i" class="flex flex-wrap items-center gap-x-3 gap-y-1 bg-gray-900 rounded-sm px-3 py-1.5">
              <span>{{ s.label }}</span>
              <span class="text-gray-400">{{ period(s.availableFrom, s.availableTo) }}</span>
              <a v-if="s.sourceUrl" :href="s.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-300 underline">ev-database</a>
              <button type="button" class="ml-auto px-3 py-1 bg-indigo-600 hover:bg-indigo-500 rounded-sm text-white disabled:opacity-40"
                      :disabled="busy === r.id" @click="apply(r, s)">Übernehmen</button>
            </li>
          </ul>
        </li>
      </ul>
      <p v-if="visible.length === 0" class="text-sm text-gray-500">Nichts mehr offen.</p>
    </template>
  </div>
</template>
