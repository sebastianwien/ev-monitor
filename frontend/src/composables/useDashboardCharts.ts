import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useThemeStore } from '../stores/theme'
import { useLocaleFormat } from './useLocaleFormat'
import { convertFromEur } from '../config/exchangeRates'
import type { StatisticsData } from './useDashboardStats'
import type { VehicleSpecification } from '../api/vehicleSpecificationService'
import type { Ref } from 'vue'

const CUSTOM_COMPARE_LS_KEY = 'ev_custom_compare_kwh'

export function useDashboardCharts(
  stats: Ref<StatisticsData | null>,
  wltp: Ref<VehicleSpecification | null>,
  hasDistanceData: Ref<boolean>,
  selectedGroupBy: Ref<string>,
) {
  const { t, locale } = useI18n()
  const { isDark } = storeToRefs(useThemeStore())
  const { formatConsumption, consumptionUnitLabel, convertConsumption, convertDistance, distanceUnitLabel, currencySymbol, currency } = useLocaleFormat()

  // Dataset toggles - Chart 1 (Charging & Costs)
  const showCostPerKwh = ref(true)
  const showKwh = ref(true)

  // Dataset toggles - Chart 2 (Range & Efficiency)
  const showDistance = ref(true)
  const showConsumption = ref(true)

  // Custom comparison value
  const customCompareValue = ref<number | null>(
    (() => { const v = localStorage.getItem(CUSTOM_COMPARE_LS_KEY); return v ? parseFloat(v) : null })()
  )
  const customCompareInput = ref(customCompareValue.value?.toString() ?? '')
  const showCompareInput = ref(false)

  const effectiveCompareValue = computed(() =>
    customCompareValue.value ?? wltp.value?.wltpConsumptionKwhPer100km ?? 0
  )
  const isCustomCompare = computed(() => customCompareValue.value !== null)

  function saveCustomCompare() {
    const parsed = parseFloat(String(customCompareInput.value).replace(',', '.'))
    if (!isNaN(parsed) && parsed > 0 && parsed < 100) {
      customCompareValue.value = parsed
      localStorage.setItem(CUSTOM_COMPARE_LS_KEY, String(parsed))
      showCompareInput.value = false
    }
  }

  function resetToWltp() {
    customCompareValue.value = null
    customCompareInput.value = ''
    localStorage.removeItem(CUSTOM_COMPARE_LS_KEY)
    showCompareInput.value = false
  }

  // Helpers
  const formatLabel = (timestamp: string) => {
    const date = new Date(timestamp)
    const currentYear = new Date().getFullYear()
    const year = date.getFullYear()
    const yearSuffix = year !== currentYear ? ` '${String(year).slice(-2)}` : ''

    if (selectedGroupBy.value === 'DAY')
      return date.toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { month: 'short', day: 'numeric' }) + yearSuffix
    if (selectedGroupBy.value === 'WEEK')
      return `${t('dashboard.week_abbr')} ${Math.ceil(date.getDate() / 7)} ${date.toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { month: 'short' })}${yearSuffix}`
    return date.toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'de-DE', { month: 'short', year: 'numeric' })
  }

  // ── Chart 1: Charging & Costs ──
  const chargingChartData = computed(() => {
    if (!stats.value || stats.value.chargesOverTime.length === 0) return null
    const labels = stats.value.chargesOverTime.map(d => formatLabel(d.timestamp))
    const datasets: any[] = []
    if (showCostPerKwh.value) {
      datasets.push({
        label: `${t('dashboard.chart_cost_per_kwh_label')} (${currencySymbol.value}/kWh)`,
        data: stats.value.chargesOverTime.map(d =>
          d.kwhCharged > 0 ? +(convertFromEur(d.costEur / d.kwhCharged, currency.value)).toFixed(3) : null
        ),
        borderColor: isDark.value ? '#818cf8' : '#4f46e5',
        backgroundColor: isDark.value ? 'rgba(129,140,248,0.13)' : 'rgba(79,70,229,0.1)',
        tension: 0, fill: true, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y'
      })
    }
    if (showKwh.value) {
      datasets.push({
        label: t('dashboard.chart_kwh'),
        data: stats.value.chargesOverTime.map(d => d.kwhCharged),
        borderColor: isDark.value ? '#fcd34d' : '#f59e0b',
        backgroundColor: isDark.value ? 'rgba(252,211,77,0.13)' : 'rgba(245,158,11,0.1)',
        tension: 0, fill: true, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y1'
      })
    }
    return { labels, datasets }
  })

  const chargingChartOptions = computed(() => ({
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index' as const, intersect: false },
    plugins: {
      legend: { display: false },
      datalabels: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx: any) => {
            const lbl = ctx.dataset.label
            const v = ctx.parsed.y
            if (v == null) return `${lbl}: -`
            if (ctx.datasetIndex === 0 && showCostPerKwh.value) return `${lbl}: ${v.toFixed(2)} ${currencySymbol.value}/kWh`
            if (lbl.includes('kWh')) return `${lbl}: ${v.toFixed(1)} kWh`
            return `${lbl}: ${v}`
          }
        }
      }
    },
    scales: {
      y: {
        type: 'linear' as const, position: 'left' as const,
        title: { display: true, text: `${currencySymbol.value}/kWh`, color: isDark.value ? '#9ca3af' : '#6b7280' },
        beginAtZero: true,
        grid: { color: isDark.value ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.06)' },
        ticks: { color: isDark.value ? '#9ca3af' : '#6b7280' }
      },
      y1: {
        type: 'linear' as const, position: 'right' as const,
        title: { display: true, text: 'kWh', color: isDark.value ? '#9ca3af' : '#6b7280' },
        beginAtZero: true,
        grid: { drawOnChartArea: false },
        ticks: { color: isDark.value ? '#9ca3af' : '#6b7280' }
      }
    }
  }))

  // ── Chart 2: Range & Efficiency ──
  const efficiencyChartData = computed(() => {
    if (!stats.value || stats.value.chargesOverTime.length === 0 || !hasDistanceData.value) return null
    const labels = stats.value.chargesOverTime.map(d => formatLabel(d.timestamp))
    const datasets: any[] = []
    if (showConsumption.value) {
      datasets.push({
        label: `${t('dashboard.chart_consumption_label')} (${consumptionUnitLabel()})`,
        data: stats.value.chargesOverTime.map(d => d.consumptionKwhPer100km != null ? +convertConsumption(d.consumptionKwhPer100km).toFixed(2) : null),
        borderColor: isDark.value ? '#f87171' : '#ef4444',
        backgroundColor: isDark.value ? 'rgba(248,113,113,0.13)' : 'rgba(239,68,68,0.1)',
        tension: 0, fill: true, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y'
      })
    }
    if (showDistance.value) {
      datasets.push({
        label: `${t('dashboard.chart_distance_label')} (${distanceUnitLabel()})`,
        data: stats.value.chargesOverTime.map(d => d.distanceKm != null ? Math.round(convertDistance(d.distanceKm)) : null),
        borderColor: isDark.value ? '#34d399' : '#10b981',
        backgroundColor: isDark.value ? 'rgba(52,211,153,0.13)' : 'rgba(16,185,129,0.1)',
        tension: 0, fill: true, pointRadius: 4, pointHoverRadius: 6, yAxisID: 'y1'
      })
    }
    return { labels, datasets }
  })

  const efficiencyChartOptions = computed(() => ({
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index' as const, intersect: false },
    plugins: {
      legend: { display: false },
      datalabels: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx: any) => {
            const lbl = ctx.dataset.label
            const v = ctx.parsed.y
            if (v == null) return `${lbl}: -`
            if (lbl.includes(consumptionUnitLabel())) return `${lbl}: ${v.toFixed(1)}`
            if (lbl.includes(distanceUnitLabel())) return `${lbl}: ${Math.round(v).toLocaleString()} ${distanceUnitLabel()}`
            return `${lbl}: ${v}`
          }
        }
      }
    },
    scales: {
      y: {
        type: 'linear' as const, position: 'left' as const,
        title: { display: true, text: consumptionUnitLabel(), color: isDark.value ? '#9ca3af' : '#6b7280' },
        beginAtZero: true,
        grid: { color: isDark.value ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.06)' },
        ticks: { color: isDark.value ? '#9ca3af' : '#6b7280' }
      },
      y1: {
        type: 'linear' as const, position: 'right' as const,
        title: { display: true, text: distanceUnitLabel(), color: isDark.value ? '#9ca3af' : '#6b7280' },
        beginAtZero: true,
        grid: { drawOnChartArea: false },
        ticks: { color: isDark.value ? '#9ca3af' : '#6b7280' }
      }
    }
  }))

  // ── WLTP delta bar chart ──
  const wltpChartData = computed(() => {
    if (!stats.value || !wltp.value || !hasDistanceData.value) return null
    const wltpVal = effectiveCompareValue.value
    const points = stats.value.chargesOverTime.filter(d => d.consumptionKwhPer100km != null)
    if (points.length === 0) return null
    const labels = points.map(d => formatLabel(d.timestamp))
    const rawDeltas = points.map(d => d.consumptionKwhPer100km! - wltpVal)
    const deltas = rawDeltas.map(d => {
      const absDelta = Math.abs(d)
      const convertedAbs = Math.abs(convertConsumption(wltpVal + absDelta) - convertConsumption(wltpVal))
      return +(Math.sign(d) * convertedAbs).toFixed(2)
    })
    return {
      labels,
      datasets: [{
        label: `\u0394 ${t('dashboard.chart_consumption_label')} vs. ${isCustomCompare.value ? formatConsumption(effectiveCompareValue.value) : 'WLTP'} (${consumptionUnitLabel()})`,
        data: deltas,
        backgroundColor: rawDeltas.map(v => v > 0
          ? (isDark.value ? 'rgba(248,113,113,0.6)' : 'rgba(239,68,68,0.7)')
          : (isDark.value ? 'rgba(52,211,153,0.6)' : 'rgba(16,185,129,0.7)')
        ),
        borderColor: rawDeltas.map(v => v > 0
          ? (isDark.value ? '#f87171' : '#dc2626')
          : (isDark.value ? '#34d399' : '#059669')
        ),
        borderWidth: 1,
        borderRadius: 3,
      }]
    }
  })

  const wltpChartOptions = computed(() => {
    const dataPoints = wltpChartData.value?.labels?.length || 0
    let barPercentage = 0.8
    let categoryPercentage = 0.9
    if (dataPoints >= 20) { barPercentage = 0.6; categoryPercentage = 0.8 }
    else if (dataPoints >= 10) { barPercentage = 0.7; categoryPercentage = 0.85 }

    return {
      indexAxis: 'y' as const,
      responsive: true,
      maintainAspectRatio: false,
      datasets: { bar: { barPercentage, categoryPercentage } },
      plugins: {
        legend: { display: false },
        datalabels: {
          align: 'end' as const,
          anchor: 'end' as const,
          color: isDark.value ? '#d1d5db' : '#374151',
          font: { weight: 'bold' as const, size: 12 },
          formatter: (value: number) => {
            const convertedCompare = convertConsumption(effectiveCompareValue.value || 0)
            const percentDiff = convertedCompare !== 0 ? (value / convertedCompare) * 100 : 0
            return `${percentDiff > 0 ? '+' : ''}${percentDiff.toFixed(1)}%`
          }
        },
        tooltip: {
          callbacks: {
            label: (ctx: any) => {
              const v = ctx.parsed.x
              const sign = v > 0 ? '+' : ''
              const convertedCompare = convertConsumption(effectiveCompareValue.value || 0)
              const compareLabel = isCustomCompare.value ? formatConsumption(effectiveCompareValue.value) : 'WLTP'
              const percentDiff = convertedCompare !== 0 ? ((v / convertedCompare) * 100).toFixed(1) : '0.0'
              return [
                `${sign}${v.toFixed(2)} ${consumptionUnitLabel()} vs. ${compareLabel}`,
                `${t('dashboard.chart_compare_value')}: ${formatConsumption(effectiveCompareValue.value || 0)}`,
                `${t('dashboard.chart_deviation')}: ${sign}${percentDiff}%`
              ]
            }
          }
        }
      },
      scales: {
        x: {
          title: {
            display: true,
            text: isCustomCompare.value
              ? `\u0394 ${consumptionUnitLabel()} (+ = ${t('dashboard.chart_more_than')} ${formatConsumption(effectiveCompareValue.value)})`
              : `\u0394 ${consumptionUnitLabel()} (+ = ${t('dashboard.chart_more_than')} WLTP)`,
            color: isDark.value ? '#9ca3af' : '#6b7280'
          },
          grid: { color: (ctx: any) => ctx.tick.value === 0 ? (isDark.value ? '#9ca3af' : '#6b7280') : (isDark.value ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.06)') },
          ticks: {
            callback: (v: any) => `${v > 0 ? '+' : ''}${v}`,
            color: isDark.value ? '#9ca3af' : '#6b7280'
          }
        },
        y: {
          grid: { display: false },
          ticks: { color: isDark.value ? '#9ca3af' : '#6b7280' }
        }
      }
    }
  })

  const wltpChartHeight = computed(() => {
    const dataPoints = wltpChartData.value?.labels?.length || 0
    const dynamicHeight = Math.max(400, dataPoints * 35 + 100)
    const maxHeight = 1150
    return `${Math.min(dynamicHeight, maxHeight)}px`
  })

  const wltpChartScrollable = computed(() => {
    const dataPoints = wltpChartData.value?.labels?.length || 0
    return dataPoints >= 30
  })

  return {
    // Dataset toggles
    showCostPerKwh,
    showKwh,
    showDistance,
    showConsumption,
    // Custom compare
    customCompareValue,
    customCompareInput,
    showCompareInput,
    effectiveCompareValue,
    isCustomCompare,
    saveCustomCompare,
    resetToWltp,
    // Chart 1
    chargingChartData,
    chargingChartOptions,
    // Chart 2
    efficiencyChartData,
    efficiencyChartOptions,
    // WLTP delta
    wltpChartData,
    wltpChartOptions,
    wltpChartHeight,
    wltpChartScrollable,
    // Helpers
    formatLabel,
    isDark,
  }
}
