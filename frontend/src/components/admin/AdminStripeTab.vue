<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '../../api/axios'
import { ArrowDownTrayIcon, ArrowPathIcon, ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  type ChartData,
  type ChartOptions,
} from 'chart.js'
import { Bar, Line } from 'vue-chartjs'
import { toCsv, downloadCsv } from '../../utils/csv'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend)

// ── Types (mirror AdminStripeReport.java) ─────────────────────────────────────
interface Summary {
  mrrEur: number; arrEur: number; mrrByCurrency: Record<string, number>
  active: number; activeCancelling: number; trialing: number; canceled: number
  trialsEnded: number; trialsConverted: number; trialsCancelled: number; trialConversionRate: number | null
  churnedPaid: number; churnRate: number | null; avgSubscriptionAgeDays: number | null; withReferralDiscount: number
  balanceAvailable: number; balancePending: number
  grossRevenue: number; fees: number; netRevenue: number; refunds: number; totalPaidAllTime: number
}
interface MonthRow {
  month: string; gross: number; fees: number; net: number; refunds: number; mrrEur: number
  newSubscriptions: number; canceledSubscriptions: number; trialsCancelled: number
}
interface ProductRow { product: string; interval: string; count: number; mrrEur: number; currency: string }
interface CountryRow { country: string; invoiceCount: number; net: number; tax: number; gross: number; currency: string }
interface SubscriptionRow {
  id: string; customerId: string; email: string | null; appUsername: string | null; status: string
  cancelAtPeriodEnd: boolean; trialEnd: string | null; canceledAt: string | null; product: string; interval: string
  amount: number; currency: string; totalPaid: number; createdAt: string; currentPeriodEnd: string | null
  country: string | null; hasDiscount: boolean
}
interface InvoiceRow {
  id: string; number: string | null; date: string; email: string | null; country: string; subtotal: number
  tax: number; total: number; amountPaid: number; currency: string; status: string; hostedUrl: string | null
}
interface Report {
  configured: boolean; generatedAt: string; months: number; summary: Summary; monthly: MonthRow[]
  byProduct: ProductRow[]; byCountry: CountryRow[]; subscriptions: SubscriptionRow[]
  invoices: InvoiceRow[]; openInvoices: InvoiceRow[]
}

// ── State ─────────────────────────────────────────────────────────────────────
const report = ref<Report | null>(null)
const loading = ref(false)
const error = ref('')
const months = ref(12)
const showCanceled = ref(false)

const PRESETS = [
  { label: '3M', months: 3 },
  { label: '6M', months: 6 },
  { label: '12M', months: 12 },
  { label: 'Alles', months: 0 },
]

const load = async (refresh = false) => {
  loading.value = true
  error.value = ''
  try {
    const res = await api.get<Report>(`/admin/stripe/report?months=${months.value}&refresh=${refresh}`)
    report.value = res.data
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Fehler beim Laden'
  } finally {
    loading.value = false
  }
}

const setPreset = (m: number) => {
  months.value = m
  load()
}

onMounted(() => load())

// ── Formatting ────────────────────────────────────────────────────────────────
const eur = (v: number | null | undefined, currency = 'EUR') =>
  v === null || v === undefined
    ? '-'
    : new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format(v)
const pct = (v: number | null) => (v === null ? '-' : `${(v * 100).toFixed(0)} %`)
const date = (iso: string | null) => {
  if (!iso) return '-'
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y.slice(2)}`
}
const INTERVAL_LABEL: Record<string, string> = { month: 'Monat', year: 'Jahr', week: 'Woche', day: 'Tag' }
const intervalLabel = (i: string) => INTERVAL_LABEL[i] ?? i
const monthLabel = (ym: string) => {
  const [y, m] = ym.split('-')
  return `${m}/${y.slice(2)}`
}

const statusLabel = (s: SubscriptionRow) => {
  if (s.status === 'active') return s.cancelAtPeriodEnd ? 'Aktiv (kündigt)' : 'Aktiv'
  if (s.status === 'trialing') return `Trial bis ${date(s.trialEnd)}`
  if (s.status === 'canceled') return `Storniert ${date(s.canceledAt)}`
  return s.status
}
const statusClass = (s: SubscriptionRow) => {
  if (s.status === 'active') return s.cancelAtPeriodEnd ? 'text-emerald-400/70' : 'text-emerald-400'
  if (s.status === 'trialing') return 'text-amber-400'
  if (s.status === 'canceled') return 'text-red-400'
  return 'text-gray-400'
}

const summary = computed(() => report.value?.summary)
const visibleSubs = computed(() =>
  (report.value?.subscriptions ?? []).filter((s) => showCanceled.value || s.status === 'active' || s.status === 'trialing')
)
const otherCurrencies = computed(() =>
  Object.entries(summary.value?.mrrByCurrency ?? {}).filter(([c]) => c !== 'EUR')
)

// ── Charts ────────────────────────────────────────────────────────────────────
const labels = computed(() => (report.value?.monthly ?? []).map((m) => monthLabel(m.month)))

const revenueChart = computed<ChartData<'bar'>>(() => ({
  labels: labels.value,
  datasets: [
    { label: 'Netto', data: (report.value?.monthly ?? []).map((m) => m.net), backgroundColor: '#34d399', stack: 'a' },
    { label: 'Gebühren', data: (report.value?.monthly ?? []).map((m) => m.fees), backgroundColor: '#818cf8', stack: 'a' },
    { label: 'Erstattungen', data: (report.value?.monthly ?? []).map((m) => -m.refunds), backgroundColor: '#f87171', stack: 'a' },
  ],
}))

const mrrChart = computed<ChartData<'line'>>(() => ({
  labels: labels.value,
  datasets: [
    {
      label: 'MRR (EUR)',
      data: (report.value?.monthly ?? []).map((m) => m.mrrEur),
      borderColor: '#818cf8',
      backgroundColor: 'rgba(129,140,248,0.15)',
      fill: true,
      tension: 0.3,
    },
  ],
}))

const subsChart = computed<ChartData<'bar'>>(() => ({
  labels: labels.value,
  datasets: [
    { label: 'Neue Abos', data: (report.value?.monthly ?? []).map((m) => m.newSubscriptions), backgroundColor: '#34d399' },
    { label: 'Kündigungen (bezahlt)', data: (report.value?.monthly ?? []).map((m) => -(m.canceledSubscriptions - m.trialsCancelled)), backgroundColor: '#f87171' },
    { label: 'Trial abgebrochen', data: (report.value?.monthly ?? []).map((m) => -m.trialsCancelled), backgroundColor: '#fbbf24' },
  ],
}))

const gridColor = 'rgba(255,255,255,0.06)'
const tickColor = '#9ca3af'
const baseOptions = (stacked: boolean, money: boolean): ChartOptions<'bar' | 'line'> => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: tickColor, boxWidth: 12 } } },
  scales: {
    x: { stacked, grid: { color: gridColor }, ticks: { color: tickColor } },
    y: {
      stacked,
      grid: { color: gridColor },
      ticks: { color: tickColor, callback: (v) => (money ? `${v} €` : v) },
    },
  },
})
const revenueOptions = computed(() => baseOptions(true, true) as ChartOptions<'bar'>)
const subsOptions = computed(() => baseOptions(true, false) as ChartOptions<'bar'>)
const mrrOptions = computed(() => baseOptions(false, true) as ChartOptions<'line'>)

// ── CSV ───────────────────────────────────────────────────────────────────────
const exportInvoices = () => {
  const rows = (report.value?.invoices ?? []).map((i) => [
    i.number, i.date, i.email, i.country, i.subtotal, i.tax, i.total, i.amountPaid, i.currency, i.status,
  ])
  const suffix = months.value === 0 ? 'alle' : `${months.value}m`
  downloadCsv(
    `stripe-rechnungen-${suffix}.csv`,
    toCsv(['Nummer', 'Datum', 'Kunde', 'Land', 'Netto', 'Steuer', 'Brutto', 'Bezahlt', 'Währung', 'Status'], rows)
  )
}
</script>

<template>
  <div>
    <!-- Header + presets -->
    <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
      <h2 class="text-lg font-semibold text-white">Stripe</h2>
      <div class="flex items-center gap-2">
        <div class="flex gap-1">
          <button
            v-for="p in PRESETS"
            :key="p.label"
            @click="setPreset(p.months)"
            :class="[
              'px-2.5 py-1 rounded-sm text-xs font-medium transition',
              months === p.months ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200',
            ]"
          >
            {{ p.label }}
          </button>
        </div>
        <button
          @click="load(true)"
          :disabled="loading"
          title="Cache umgehen und neu von Stripe laden"
          class="p-1.5 rounded-sm bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-gray-200 disabled:opacity-50"
        >
          <ArrowPathIcon class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        </button>
      </div>
    </div>

    <div v-if="loading && !report" class="text-gray-400 text-sm py-8 text-center">Lade Stripe-Daten...</div>
    <div v-else-if="error" class="text-red-400 text-sm py-4">{{ error }}</div>
    <div v-else-if="report && !report.configured" class="text-gray-500 text-sm py-8 text-center">
      Keine Daten - STRIPE_SECRET_KEY gesetzt und Stripe erreichbar?
    </div>

    <template v-else-if="report && summary">
      <!-- KPI: BizDev -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-3">
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-indigo-400">{{ eur(summary.mrrEur) }}</div>
          <div class="text-xs text-gray-400 mt-1">MRR · ARR {{ eur(summary.arrEur) }}</div>
          <div v-if="otherCurrencies.length" class="text-[11px] text-gray-500 mt-1">
            + <span v-for="([c, v], i) in otherCurrencies" :key="c">{{ i > 0 ? ' · ' : '' }}{{ eur(v, c) }}</span> / Monat
          </div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-emerald-400">{{ summary.active }}</div>
          <div class="text-xs text-gray-400 mt-1">
            Aktiv<span v-if="summary.activeCancelling"> · {{ summary.activeCancelling }} kündigt</span>
          </div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-amber-400">{{ summary.trialing }}</div>
          <div class="text-xs text-gray-400 mt-1">Im Trial</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-red-400">{{ summary.trialsCancelled }}</div>
          <div class="text-xs text-gray-400 mt-1">
            Trial abgebrochen · {{ summary.trialsConverted }} von {{ summary.trialsEnded }} konvertiert ({{ pct(summary.trialConversionRate) }})
          </div>
        </div>
      </div>

      <!-- KPI: Accounting (window) -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-3">
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-white">{{ eur(summary.netRevenue) }}</div>
          <div class="text-xs text-gray-400 mt-1">Netto im Zeitraum · brutto {{ eur(summary.grossRevenue) }}</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-indigo-300">{{ eur(summary.fees) }}</div>
          <div class="text-xs text-gray-400 mt-1">Stripe-Gebühren · Erstattungen {{ eur(summary.refunds) }}</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-emerald-300">{{ eur(summary.balanceAvailable) }}</div>
          <div class="text-xs text-gray-400 mt-1">Balance verfügbar · pending {{ eur(summary.balancePending) }}</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-2xl font-bold text-white">{{ eur(summary.totalPaidAllTime) }}</div>
          <div class="text-xs text-gray-400 mt-1">Bezahlt gesamt (alle Zeit)</div>
        </div>
      </div>

      <!-- KPI: retention -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-xl font-bold text-red-300">{{ summary.churnedPaid }} <span class="text-sm text-gray-500">({{ pct(summary.churnRate) }})</span></div>
          <div class="text-xs text-gray-400 mt-1">Churn zahlender Abos im Zeitraum</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-xl font-bold text-gray-200">{{ summary.avgSubscriptionAgeDays === null ? '-' : Math.round(summary.avgSubscriptionAgeDays) + ' Tage' }}</div>
          <div class="text-xs text-gray-400 mt-1">Ø Abo-Alter (aktiv + Trial)</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-xl font-bold text-gray-200">{{ summary.withReferralDiscount }}</div>
          <div class="text-xs text-gray-400 mt-1">Abos mit Referral-Rabatt</div>
        </div>
        <div class="bg-gray-900 rounded-sm px-4 py-3 border border-gray-800">
          <div class="text-xl font-bold text-gray-200">{{ summary.canceled }}</div>
          <div class="text-xs text-gray-400 mt-1">Storniert gesamt</div>
        </div>
      </div>

      <!-- Charts -->
      <div class="grid md:grid-cols-2 gap-4 mb-6">
        <div class="bg-gray-900 rounded-sm p-4 border border-gray-800 md:col-span-2">
          <h3 class="text-sm font-medium text-gray-300 mb-2">Umsatz pro Monat (Netto, Gebühren, Erstattungen)</h3>
          <div class="h-64 md:h-80"><Bar :data="revenueChart" :options="revenueOptions" /></div>
        </div>
        <div class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-medium text-gray-300 mb-2">MRR-Verlauf (EUR, Monatsende)</h3>
          <div class="h-64"><Line :data="mrrChart" :options="mrrOptions" /></div>
        </div>
        <div class="bg-gray-900 rounded-sm p-4 border border-gray-800">
          <h3 class="text-sm font-medium text-gray-300 mb-2">Neue Abos, Kündigungen, Trial-Abbrüche</h3>
          <div class="h-64"><Bar :data="subsChart" :options="subsOptions" /></div>
        </div>
      </div>

      <!-- Distributions -->
      <div class="grid md:grid-cols-2 gap-4 mb-6">
        <div class="bg-gray-900 rounded-sm border border-gray-800 overflow-x-auto">
          <h3 class="text-sm font-medium text-gray-300 px-4 pt-3 pb-2">Nach Produkt (aktiv + Trial)</h3>
          <table class="w-full text-sm">
            <thead class="text-xs text-gray-500">
              <tr><th class="text-left px-4 py-1">Produkt</th><th class="text-left px-2 py-1">Intervall</th><th class="text-right px-2 py-1">Anzahl</th><th class="text-right px-4 py-1">MRR</th></tr>
            </thead>
            <tbody>
              <tr v-for="p in report.byProduct" :key="p.product + p.interval + p.currency" class="border-t border-gray-800 text-gray-200">
                <td class="px-4 py-1.5">{{ p.product }}</td>
                <td class="px-2 py-1.5">{{ intervalLabel(p.interval) }}</td>
                <td class="px-2 py-1.5 text-right">{{ p.count }}</td>
                <td class="px-4 py-1.5 text-right">{{ eur(p.mrrEur, p.currency) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="bg-gray-900 rounded-sm border border-gray-800 overflow-x-auto">
          <h3 class="text-sm font-medium text-gray-300 px-4 pt-3 pb-2">Nach Land (bezahlte Rechnungen, OSS-Basis)</h3>
          <table class="w-full text-sm">
            <thead class="text-xs text-gray-500">
              <tr><th class="text-left px-4 py-1">Land</th><th class="text-right px-2 py-1">Rechnungen</th><th class="text-right px-2 py-1">Netto</th><th class="text-right px-2 py-1">Steuer</th><th class="text-right px-4 py-1">Brutto</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in report.byCountry" :key="c.country" class="border-t border-gray-800 text-gray-200">
                <td class="px-4 py-1.5">{{ c.country }}</td>
                <td class="px-2 py-1.5 text-right">{{ c.invoiceCount }}</td>
                <td class="px-2 py-1.5 text-right">{{ eur(c.net, c.currency) }}</td>
                <td class="px-2 py-1.5 text-right">{{ eur(c.tax, c.currency) }}</td>
                <td class="px-4 py-1.5 text-right">{{ eur(c.gross, c.currency) }}</td>
              </tr>
              <tr v-if="report.byCountry.length === 0"><td colspan="5" class="px-4 py-3 text-gray-500 text-center">Keine bezahlten Rechnungen im Zeitraum</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Open invoices -->
      <div v-if="report.openInvoices.length" class="bg-red-950/40 rounded-sm border border-red-900 mb-6 overflow-x-auto">
        <h3 class="text-sm font-medium text-red-300 px-4 pt-3 pb-2">Offene Posten ({{ report.openInvoices.length }})</h3>
        <table class="w-full text-sm">
          <tbody>
            <tr v-for="i in report.openInvoices" :key="i.id" class="border-t border-red-900/60 text-gray-200">
              <td class="px-4 py-1.5 whitespace-nowrap">{{ date(i.date) }}</td>
              <td class="px-2 py-1.5">{{ i.email ?? '-' }}</td>
              <td class="px-2 py-1.5 text-red-300">{{ i.status }}</td>
              <td class="px-2 py-1.5 text-right whitespace-nowrap">{{ eur(i.total, i.currency) }}</td>
              <td class="px-4 py-1.5 text-right">
                <a v-if="i.hostedUrl" :href="i.hostedUrl" target="_blank" rel="noopener" class="text-indigo-400 hover:text-indigo-300"><ArrowTopRightOnSquareIcon class="w-4 h-4 inline" /></a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Customers -->
      <div class="flex items-center justify-between mb-2 gap-3 flex-wrap">
        <h3 class="text-sm font-medium text-gray-300">Kunden ({{ visibleSubs.length }})</h3>
        <label class="flex items-center gap-2 text-xs text-gray-400 cursor-pointer">
          <input v-model="showCanceled" type="checkbox" class="accent-indigo-500" />
          Stornierte anzeigen ({{ summary.canceled }})
        </label>
      </div>
      <div class="bg-gray-900 rounded-sm border border-gray-800 overflow-x-auto mb-6">
        <table class="w-full text-sm whitespace-nowrap">
          <thead class="text-xs text-gray-500">
            <tr>
              <th class="text-left px-4 py-2">Email</th>
              <th class="text-left px-2 py-2">App-User</th>
              <th class="text-left px-2 py-2">Status</th>
              <th class="text-left px-2 py-2">Produkt</th>
              <th class="text-right px-2 py-2">Preis</th>
              <th class="text-right px-2 py-2">Bezahlt</th>
              <th class="text-left px-2 py-2">Seit</th>
              <th class="text-left px-2 py-2">Nächste Abr.</th>
              <th class="text-left px-2 py-2">Land</th>
              <th class="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in visibleSubs" :key="s.id" class="border-t border-gray-800 text-gray-200">
              <td class="px-4 py-1.5 max-w-[220px] truncate" :title="s.email ?? ''">{{ s.email ?? '?' }}</td>
              <td class="px-2 py-1.5 text-gray-400">{{ s.appUsername ?? '-' }}</td>
              <td class="px-2 py-1.5" :class="statusClass(s)">{{ statusLabel(s) }}</td>
              <td class="px-2 py-1.5">{{ s.product }} / {{ intervalLabel(s.interval) }}<span v-if="s.hasDiscount" class="text-[10px] text-indigo-300 ml-1">Referral</span></td>
              <td class="px-2 py-1.5 text-right">{{ eur(s.amount, s.currency) }}</td>
              <td class="px-2 py-1.5 text-right" :class="s.totalPaid > 0 ? 'font-semibold' : 'text-gray-500'">{{ eur(s.totalPaid, s.currency) }}</td>
              <td class="px-2 py-1.5 text-gray-400">{{ date(s.createdAt) }}</td>
              <td class="px-2 py-1.5 text-gray-400">{{ s.status === 'canceled' ? '-' : date(s.currentPeriodEnd) }}</td>
              <td class="px-2 py-1.5 text-gray-400">{{ s.country ?? '-' }}</td>
              <td class="px-4 py-1.5 text-right">
                <a :href="`https://dashboard.stripe.com/customers/${s.customerId}`" target="_blank" rel="noopener" title="In Stripe öffnen" class="text-indigo-400 hover:text-indigo-300"><ArrowTopRightOnSquareIcon class="w-4 h-4 inline" /></a>
              </td>
            </tr>
            <tr v-if="visibleSubs.length === 0"><td colspan="10" class="px-4 py-4 text-gray-500 text-center">Keine Abos</td></tr>
          </tbody>
        </table>
      </div>

      <!-- Invoices -->
      <div class="flex items-center justify-between mb-2 gap-3 flex-wrap">
        <h3 class="text-sm font-medium text-gray-300">Rechnungen im Zeitraum ({{ report.invoices.length }})</h3>
        <button
          @click="exportInvoices"
          :disabled="report.invoices.length === 0"
          class="flex items-center gap-1.5 px-2.5 py-1 rounded-sm text-xs font-medium bg-gray-800 text-gray-300 hover:bg-gray-700 disabled:opacity-50"
        >
          <ArrowDownTrayIcon class="w-4 h-4" /> CSV für Steuerberater
        </button>
      </div>
      <div class="bg-gray-900 rounded-sm border border-gray-800 overflow-x-auto">
        <table class="w-full text-sm whitespace-nowrap">
          <thead class="text-xs text-gray-500">
            <tr>
              <th class="text-left px-4 py-2">Nummer</th>
              <th class="text-left px-2 py-2">Datum</th>
              <th class="text-left px-2 py-2">Kunde</th>
              <th class="text-left px-2 py-2">Land</th>
              <th class="text-right px-2 py-2">Netto</th>
              <th class="text-right px-2 py-2">Steuer</th>
              <th class="text-right px-2 py-2">Brutto</th>
              <th class="text-left px-2 py-2">Status</th>
              <th class="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="i in report.invoices" :key="i.id" class="border-t border-gray-800 text-gray-200">
              <td class="px-4 py-1.5 text-gray-400">{{ i.number ?? i.id }}</td>
              <td class="px-2 py-1.5">{{ date(i.date) }}</td>
              <td class="px-2 py-1.5 max-w-[200px] truncate" :title="i.email ?? ''">{{ i.email ?? '-' }}</td>
              <td class="px-2 py-1.5">{{ i.country }}</td>
              <td class="px-2 py-1.5 text-right">{{ eur(i.subtotal, i.currency) }}</td>
              <td class="px-2 py-1.5 text-right">{{ eur(i.tax, i.currency) }}</td>
              <td class="px-2 py-1.5 text-right font-semibold">{{ eur(i.total, i.currency) }}</td>
              <td class="px-2 py-1.5" :class="i.status === 'paid' ? 'text-emerald-400' : 'text-red-400'">{{ i.status }}</td>
              <td class="px-4 py-1.5 text-right">
                <a v-if="i.hostedUrl" :href="i.hostedUrl" target="_blank" rel="noopener" class="text-indigo-400 hover:text-indigo-300"><ArrowTopRightOnSquareIcon class="w-4 h-4 inline" /></a>
              </td>
            </tr>
            <tr v-if="report.invoices.length === 0"><td colspan="9" class="px-4 py-4 text-gray-500 text-center">Keine Rechnungen im Zeitraum</td></tr>
          </tbody>
        </table>
      </div>
      <p class="text-[11px] text-gray-600 mt-3">Stand {{ new Date(report.generatedAt).toLocaleString('de-DE') }} · Cache 10 Minuten · MRR/ARR nur EUR-Abos, Umsatz aus Stripe-Balance-Transaktionen in EUR</p>
    </template>
  </div>
</template>
