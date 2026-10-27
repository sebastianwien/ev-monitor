<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminChargingTariffService, type TariffCommand, type TariffOverview, type TariffRow } from '../../api/adminChargingTariffService'
import { apiErrorText } from '../../api/adminVehicleSpecService'
import AdminTariffCrawlPanel from './AdminTariffCrawlPanel.vue'

/**
 * Admin-Tab "Ladekarten": die gepflegten Tarife hinter dem Story-Moment "Teurer als üblich"
 * und dem Schnellladepreis des Rechners. Oben der Tarif-Crawler mit seinen Vorschlägen.
 * Preisänderung = alten Tarif beenden, neuen anlegen; "Ändern" ist für Tippfehler und erneuert
 * den Stand, "Geprüft" erneuert nur den Stand.
 */
const data = ref<TariffOverview | null>(null)
const loadError = ref('')
const showEnded = ref(false)
const editing = ref<{ id: string | null; form: TariffCommand } | null>(null)
const busy = ref(false)
const formError = ref('')

const today = () => new Date().toISOString().slice(0, 10)
const de = (iso: string) => new Date(`${iso.slice(0, 10)}T00:00:00`).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
const ct = (eur: number) => `${(eur * 100).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} ct`
const euro = (v: number) => `${v.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`

async function load() {
  try {
    data.value = await adminChargingTariffService.overview()
    loadError.value = ''
  } catch (e) {
    loadError.value = apiErrorText(e, 'Tarife konnten nicht geladen werden.')
  }
}
onMounted(load)

const isEnded = (r: TariffRow) => r.validUntil != null && r.validUntil < today()
const rows = computed(() => (data.value?.tariffs ?? []).filter(r => showEnded.value || !isEnded(r)))
/** Ohne Betreiber und Tier gilt ein Tarif nur, wenn der Anbieter selbst ein Netz im Katalog hat (ADAC, Maingau nicht). */
function scope(r: TariffRow): string {
  if (r.cpoName) return r.cpoName
  if (r.priceTier) return r.appliesElsewhere ? `Tier ${r.priceTier} und alle anderen Betreiber` : `Tier ${r.priceTier}`
  if (r.appliesElsewhere) return 'alle anderen Betreiber'
  return data.value?.cpos.includes(r.empName) ? 'eigenes Netz' : 'nirgends: kein eigenes Netz, Betreiber setzen'
}
const kind = (r: TariffRow) => r.minPowerKw != null ? `${r.chargingType} über ${r.minPowerKw} kW` : r.chargingType
const validity = (r: TariffRow) => r.validUntil ? `${de(r.validFrom)} bis ${de(r.validUntil)}` : `ab ${de(r.validFrom)}`

const blank = (): TariffCommand => ({
  empName: '', tariffVariant: '', cpoName: null, priceTier: null, chargingType: 'DC', pricePerKwh: 0,
  sessionFeeEur: 0, monthlyFeeEur: 0, blockingFeePerMin: null, blockingFeeAfterMin: null, dynamicPricing: false,
  validFrom: today(), validUntil: null, sourceUrl: '', appliesElsewhere: false, minPowerKw: null, blockingFeeCapEur: null,
})
function startCreate() {
  formError.value = ''
  editing.value = { id: null, form: blank() }
}
function startEdit(r: TariffRow) {
  formError.value = ''
  const { id, lastVerifiedAt: _verified, ...form } = r
  editing.value = { id, form: { ...form, tariffVariant: form.tariffVariant ?? '', sourceUrl: form.sourceUrl ?? '' } }
}

async function save() {
  if (!editing.value) return
  busy.value = true
  formError.value = ''
  const f = editing.value.form
  const cmd: TariffCommand = {
    ...f,
    cpoName: f.cpoName || null,
    priceTier: f.priceTier || null,
    validUntil: f.validUntil || null,
    pricePerKwh: Number(f.pricePerKwh),
    sessionFeeEur: Number(f.sessionFeeEur || 0),
    monthlyFeeEur: Number(f.monthlyFeeEur || 0),
    blockingFeePerMin: f.blockingFeePerMin === null || (f.blockingFeePerMin as unknown) === '' ? null : Number(f.blockingFeePerMin),
    blockingFeeAfterMin: f.blockingFeeAfterMin === null || (f.blockingFeeAfterMin as unknown) === '' ? null : Number(f.blockingFeeAfterMin),
    minPowerKw: f.minPowerKw === null || (f.minPowerKw as unknown) === '' ? null : Number(f.minPowerKw),
    blockingFeeCapEur: f.blockingFeeCapEur === null || (f.blockingFeeCapEur as unknown) === '' ? null : Number(f.blockingFeeCapEur),
  }
  try {
    if (editing.value.id) await adminChargingTariffService.update(editing.value.id, cmd)
    else await adminChargingTariffService.create(cmd)
    editing.value = null
    await load()
  } catch (e) {
    formError.value = apiErrorText(e, 'Speichern hat nicht geklappt.')
  } finally {
    busy.value = false
  }
}

async function verifyTariff(r: TariffRow) {
  try {
    await adminChargingTariffService.verify(r.id)
    await load()
  } catch (e) {
    loadError.value = apiErrorText(e, 'Markieren hat nicht geklappt.')
  }
}

async function endTariff(r: TariffRow) {
  const until = window.prompt(`${r.empName} ${r.tariffVariant ?? ''} beenden. Letzter gültiger Tag (JJJJ-MM-TT):`, today())
  if (!until) return
  try {
    await adminChargingTariffService.end(r.id, until.trim())
    await load()
  } catch (e) {
    loadError.value = apiErrorText(e, 'Beenden hat nicht geklappt.')
  }
}

const field = 'w-full bg-gray-900 border border-gray-700 rounded-sm px-3 py-2 text-sm text-gray-100 placeholder:text-gray-600 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500'
const label = 'block text-xs text-gray-400 mb-1'
</script>

<template>
  <div>
    <div class="flex flex-wrap items-baseline justify-between gap-3 mb-4">
      <div>
        <h2 class="text-lg font-semibold text-white">Ladekarten</h2>
        <p class="text-sm text-gray-400 max-w-2xl">
          Gepflegte Tarife. Ein Tarif gilt beim genannten Betreiber, über das Tier-Mapping, im eigenen Netz des Anbieters
          oder an allen anderen Betreibern, in dieser Reihenfolge. Preisänderung: alten Tarif beenden, neuen anlegen.
        </p>
      </div>
      <div class="flex items-center gap-4">
        <label class="flex items-center gap-2 text-sm text-gray-300">
          <input v-model="showEnded" name="showEnded" type="checkbox" class="accent-indigo-500" /> Auch beendete zeigen
        </label>
        <button type="button" class="px-4 py-2 rounded-sm bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-500" @click="startCreate">Neuer Tarif</button>
      </div>
    </div>

    <AdminTariffCrawlPanel @changed="load" />

    <div v-if="loadError" class="mb-4 p-3 bg-red-900/40 border border-red-700 text-red-300 text-sm rounded-sm">{{ loadError }}</div>

    <form v-if="editing" class="mb-6 p-4 bg-gray-900 border border-gray-800 rounded-sm grid gap-3 sm:grid-cols-2 lg:grid-cols-4" @submit.prevent="save">
      <h3 class="sm:col-span-2 lg:col-span-4 text-base font-semibold text-white">{{ editing.id ? 'Tarif ändern' : 'Neuer Tarif' }}</h3>
      <div>
        <label :class="label">Anbieter</label>
        <input v-model="editing.form.empName" name="empName" list="tariff-emps" :class="field" required />
        <datalist id="tariff-emps"><option v-for="e in data?.emps ?? []" :key="e" :value="e" /></datalist>
      </div>
      <div>
        <label :class="label">Variante</label>
        <input v-model="editing.form.tariffVariant" name="tariffVariant" :class="field" />
      </div>
      <div>
        <label :class="label">Betreiber (leer = eigenes Netz oder Tier)</label>
        <select v-model="editing.form.cpoName" name="cpoName" :class="field">
          <option :value="null">-</option>
          <option v-for="c in data?.cpos ?? []" :key="c" :value="c">{{ c }}</option>
        </select>
      </div>
      <div>
        <label :class="label">Tier (nur ohne Betreiber)</label>
        <select v-model="editing.form.priceTier" name="priceTier" :class="field">
          <option :value="null">-</option>
          <option value="LOW">LOW</option>
          <option value="STANDARD">STANDARD</option>
          <option value="HIGH">HIGH</option>
        </select>
      </div>
      <div>
        <label :class="label">Ladeart</label>
        <select v-model="editing.form.chargingType" name="chargingType" :class="field">
          <option value="DC">DC</option>
          <option value="AC">AC</option>
        </select>
      </div>
      <div>
        <label :class="label">Preis €/kWh</label>
        <input v-model="editing.form.pricePerKwh" name="pricePerKwh" type="number" step="0.0001" min="0.01" max="2" :class="field" required />
      </div>
      <div>
        <label :class="label">Grundgebühr €/Monat</label>
        <input v-model="editing.form.monthlyFeeEur" name="monthlyFeeEur" type="number" step="0.01" min="0" max="100" :class="field" />
      </div>
      <div>
        <label :class="label">Startgebühr € je Ladung</label>
        <input v-model="editing.form.sessionFeeEur" name="sessionFeeEur" type="number" step="0.01" min="0" max="50" :class="field" />
      </div>
      <div>
        <label :class="label">Blockiergebühr €/min</label>
        <input v-model="editing.form.blockingFeePerMin" name="blockingFeePerMin" type="number" step="0.01" min="0" max="5" :class="field" />
      </div>
      <div>
        <label :class="label">Blockiergebühr ab Minute</label>
        <input v-model="editing.form.blockingFeeAfterMin" name="blockingFeeAfterMin" type="number" step="1" min="0" max="1440" :class="field" />
      </div>
      <div>
        <label :class="label">Blockiergebühr höchstens €</label>
        <input v-model="editing.form.blockingFeeCapEur" name="blockingFeeCapEur" type="number" step="0.01" min="0" max="100" :class="field" />
      </div>
      <div>
        <label :class="label">Nur über kW (DC, leer = alle)</label>
        <input v-model="editing.form.minPowerKw" name="minPowerKw" type="number" step="1" min="1" max="1000" :class="field" />
      </div>
      <div>
        <label :class="label">Gültig ab</label>
        <input v-model="editing.form.validFrom" name="validFrom" type="date" :class="field" required />
      </div>
      <div>
        <label :class="label">Gültig bis (leer = offen)</label>
        <input v-model="editing.form.validUntil" name="validUntil" type="date" :class="field" />
      </div>
      <div class="sm:col-span-2">
        <label :class="label">Quelle (Link)</label>
        <input v-model="editing.form.sourceUrl" name="sourceUrl" type="url" placeholder="https://" :class="field" />
      </div>
      <label class="flex items-center gap-2 text-sm text-gray-300 self-end pb-2">
        <input v-model="editing.form.dynamicPricing" name="dynamicPricing" type="checkbox" class="accent-indigo-500" /> Dynamischer Preis
      </label>
      <label class="flex items-center gap-2 text-sm text-gray-300 self-end pb-2">
        <input v-model="editing.form.appliesElsewhere" name="appliesElsewhere" type="checkbox" class="accent-indigo-500" /> Gilt an allen anderen Betreibern
      </label>
      <div v-if="formError" class="sm:col-span-2 lg:col-span-4 p-3 bg-red-900/40 border border-red-700 text-red-300 text-sm rounded-sm">{{ formError }}</div>
      <div class="sm:col-span-2 lg:col-span-4 flex gap-2">
        <button type="submit" :disabled="busy" class="px-4 py-2 rounded-sm bg-indigo-600 text-white text-sm font-medium hover:bg-indigo-500 disabled:opacity-50">Speichern</button>
        <button type="button" class="px-4 py-2 rounded-sm border border-gray-700 text-gray-300 text-sm hover:bg-gray-800" @click="editing = null">Abbrechen</button>
      </div>
    </form>

    <div class="overflow-x-auto border border-gray-800 rounded-sm">
      <table class="min-w-full text-sm">
        <thead class="bg-gray-900 text-gray-400 text-xs uppercase tracking-wider">
          <tr>
            <th class="px-3 py-2 text-left">Anbieter</th>
            <th class="px-3 py-2 text-left">Variante</th>
            <th class="px-3 py-2 text-left">Gilt bei</th>
            <th class="px-3 py-2 text-left">Art</th>
            <th class="px-3 py-2 text-right">Preis</th>
            <th class="px-3 py-2 text-right">Grundgebühr</th>
            <th class="px-3 py-2 text-left">Gültig</th>
            <th class="px-3 py-2 text-left">Stand</th>
            <th class="px-3 py-2 text-left">Quelle</th>
            <th class="px-3 py-2" />
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-800">
          <tr v-for="r in rows" :key="r.id" data-testid="tariff-row" :class="isEnded(r) ? 'text-gray-500' : 'text-gray-200'">
            <td class="px-3 py-2 font-medium">{{ r.empName }}</td>
            <td class="px-3 py-2">{{ r.tariffVariant ?? '-' }}</td>
            <td class="px-3 py-2">{{ scope(r) }}</td>
            <td class="px-3 py-2 whitespace-nowrap">{{ kind(r) }}</td>
            <td class="px-3 py-2 text-right tabular-nums whitespace-nowrap">{{ ct(r.pricePerKwh) }}</td>
            <td class="px-3 py-2 text-right tabular-nums whitespace-nowrap">{{ r.monthlyFeeEur > 0 ? euro(r.monthlyFeeEur) : '-' }}</td>
            <td class="px-3 py-2 whitespace-nowrap">{{ validity(r) }}</td>
            <td class="px-3 py-2 whitespace-nowrap">{{ de(r.lastVerifiedAt) }}</td>
            <td class="px-3 py-2">
              <a v-if="r.sourceUrl" :href="r.sourceUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-400 hover:underline">Link</a>
              <span v-else>-</span>
            </td>
            <td class="px-3 py-2 whitespace-nowrap text-right">
              <button v-if="!isEnded(r)" type="button" data-action="verify" class="px-2 py-1 text-emerald-400 hover:underline" @click="verifyTariff(r)">Geprüft</button>
              <button type="button" data-action="edit" class="px-2 py-1 text-indigo-400 hover:underline" @click="startEdit(r)">Ändern</button>
              <button v-if="!isEnded(r)" type="button" data-action="end" class="px-2 py-1 text-amber-400 hover:underline" @click="endTariff(r)">Beenden</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
