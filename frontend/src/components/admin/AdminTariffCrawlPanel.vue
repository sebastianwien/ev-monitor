<script setup lang="ts">
import { ref } from 'vue'
import { adminTariffCrawlService, type CrawlOverview } from '../../api/adminChargingTariffService'
import { apiErrorText } from '../../api/adminVehicleSpecService'

/**
 * Kopf des Tarif-Crawlers im Admin-Tab "Ladekarten": was er tut, Lauf von Hand, Hinweise ohne
 * Sprachmodell oder bei abgeschaltetem Lauf, neue Quelle. Quellen und Vorschläge stehen je Anbieter
 * im Baum darunter. Mit {@code autoApply} übernimmt der Crawler eindeutige Preisänderungen selbst.
 */
const props = defineProps<{ overview: CrawlOverview | null }>()
const emit = defineEmits<{ changed: [] }>()

const message = ref('')
const error = ref('')
const adding = ref<{ empName: string; url: string; mode: 'EXTRACT' | 'WATCH' } | null>(null)

async function runNow() {
  error.value = ''
  try {
    message.value = (await adminTariffCrawlService.run())
      ? 'Prüfung läuft im Hintergrund. In ein bis zwei Minuten neu laden.'
      : 'Es läuft schon eine Prüfung.'
  } catch (e) {
    error.value = apiErrorText(e, 'Start hat nicht geklappt.')
  }
}

async function addSource() {
  if (!adding.value) return
  try {
    await adminTariffCrawlService.createSource({ ...adding.value, enabled: true })
    adding.value = null
    emit('changed')
  } catch (e) {
    error.value = apiErrorText(e, 'Quelle konnte nicht angelegt werden.')
  }
}

const field = 'bg-gray-900 border border-gray-700 rounded-sm px-2 py-1.5 text-sm text-gray-100 focus:outline-none focus:border-indigo-500'
</script>

<template>
  <section class="mb-4">
    <div class="flex flex-wrap items-baseline justify-between gap-3 mb-3">
      <div>
        <h3 class="text-base font-semibold text-white">Automatische Prüfung</h3>
        <p class="text-sm text-gray-400 max-w-2xl">
          Sonntags um 23:40 liest der Crawler alle Seiten. Unveränderte Preise bekommen den Stand von heute.
          <template v-if="!props.overview" />
          <template v-else-if="props.overview.autoApply">Eindeutige Preisänderungen übernimmt er nach einer zweiten Bestätigung selbst, der Rest steht beim Anbieter.</template>
          <template v-else>Jede Preisänderung steht beim Anbieter mit Zitat und bisherigem Wert zur Entscheidung.</template>
        </p>
      </div>
      <div class="flex items-center gap-4">
        <button v-if="!adding" type="button" class="text-sm text-indigo-400 hover:underline" @click="adding = { empName: '', url: '', mode: 'EXTRACT' }">Quelle hinzufügen</button>
        <button type="button" class="px-4 py-2 rounded-sm border border-gray-700 text-gray-200 text-sm hover:bg-gray-800" @click="runNow">Jetzt alle Seiten prüfen</button>
      </div>
    </div>

    <div v-if="props.overview && !props.overview.extractorAvailable" class="mb-3 p-3 bg-amber-900/30 border border-amber-700 text-amber-200 text-sm rounded-sm">
      Kein Sprachmodell konfiguriert (ANTHROPIC_API_KEY oder MISTRAL_API_KEY). Nur Seiten mit Preisbildern werden auf Änderungen geprüft.
    </div>
    <div v-if="props.overview && !props.overview.enabled" class="mb-3 p-3 bg-amber-900/30 border border-amber-700 text-amber-200 text-sm rounded-sm">
      Der Lauf am Sonntag ist abgeschaltet (TARIFF_CRAWL_ENABLED). Der Knopf funktioniert trotzdem.
    </div>
    <div v-if="message" class="mb-3 p-3 bg-gray-800 border border-gray-700 text-gray-200 text-sm rounded-sm">{{ message }}</div>
    <div v-if="error" class="mb-3 p-3 bg-red-900/40 border border-red-700 text-red-300 text-sm rounded-sm">{{ error }}</div>

    <form v-if="adding" class="mb-3 flex flex-wrap gap-2 items-end" @submit.prevent="addSource">
      <input v-model="adding.empName" name="crawlEmp" placeholder="Anbieter" :class="field" required />
      <input v-model="adding.url" name="crawlUrl" type="url" placeholder="https://" :class="[field, 'min-w-[24rem]']" required />
      <select v-model="adding.mode" name="crawlMode" :class="field">
        <option value="EXTRACT">Text lesen</option>
        <option value="WATCH">Nur auf Änderung achten</option>
      </select>
      <button type="submit" class="px-3 py-1.5 rounded-sm bg-indigo-600 text-white text-sm hover:bg-indigo-500">Anlegen</button>
      <button type="button" class="px-3 py-1.5 rounded-sm border border-gray-700 text-gray-300 text-sm hover:bg-gray-800" @click="adding = null">Abbrechen</button>
    </form>
  </section>
</template>
