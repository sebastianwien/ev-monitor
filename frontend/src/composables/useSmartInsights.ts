import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { StatisticsData } from './useDashboardStats'

export type InsightSentiment = 'positive' | 'neutral' | 'warning'

export interface ChartBar {
  value: number
  projectedValue?: number  // when set: bar shows value (solid) + remainder to projectedValue (striped)
  formattedValue: string
  style: 'solid' | 'dashed'
  muted?: boolean
  label: string  // i18n key resolved in useSmartInsights
}

export interface SmartInsight {
  id: string
  sentiment: InsightSentiment
  headline: string
  body: string
  delta?: string
  deltaSecondary?: string
  deltaTertiary?: string
  chartBars?: ChartBar[]
}

// Pure function - testable without Vue context
export function computeInsights(
  stats: StatisticsData,
  lastMonthStats: StatisticsData | null,
  today: Date = new Date(),
): SmartInsight[] {
  const candidates: SmartInsight[] = []

  // ── peer_cost ────────────────────────────────────────────────────────────
  const pb = stats.peerBenchmark
  if (
    pb != null &&
    pb.peerAvgCostPerKwh != null &&
    pb.userLifetimeCostPerKwh != null &&
    pb.sameCountryPeerUsers >= 1
  ) {
    const diffPct = Math.round(
      ((pb.userLifetimeCostPerKwh - pb.peerAvgCostPerKwh) / pb.peerAvgCostPerKwh) * 100,
    )
    if (Math.abs(diffPct) >= 5) {
      const absCt = Math.abs((pb.userLifetimeCostPerKwh - pb.peerAvgCostPerKwh) * 100)
      candidates.push({
        id: 'peer_cost',
        sentiment: diffPct < 0 ? 'positive' : 'warning',
        headline: `peer_cost_${diffPct < 0 ? 'cheaper' : 'expensive'}`,
        body: 'peer_cost_body',
        delta: `${diffPct > 0 ? '+' : ''}${diffPct}%`,
        deltaSecondary: `${diffPct > 0 ? '+' : '-'}${absCt.toFixed(1)} ct/kWh`,
        chartBars: [
          { value: pb.userLifetimeCostPerKwh, formattedValue: `${(pb.userLifetimeCostPerKwh * 100).toFixed(1)} ct`, style: 'solid', label: 'chart_you' },
          { value: pb.peerAvgCostPerKwh, formattedValue: `${(pb.peerAvgCostPerKwh * 100).toFixed(1)} ct`, style: 'solid', muted: true, label: 'chart_avg' },
        ],
      })
    }
  }

  // ── peer_consumption ──────────────────────────────────────────────────────
  if (
    pb != null &&
    pb.peerAvgConsumptionKwhPer100km != null &&
    pb.userLifetimeConsumptionKwhPer100km != null
  ) {
    const diffPct = Math.round(
      ((pb.userLifetimeConsumptionKwhPer100km - pb.peerAvgConsumptionKwhPer100km) /
        pb.peerAvgConsumptionKwhPer100km) *
        100,
    )
    if (Math.abs(diffPct) >= 5) {
      const absKwh = Math.abs(
        pb.userLifetimeConsumptionKwhPer100km - pb.peerAvgConsumptionKwhPer100km,
      )
      candidates.push({
        id: 'peer_consumption',
        sentiment: diffPct < 0 ? 'positive' : 'neutral',
        headline: `peer_consumption_${diffPct < 0 ? 'better' : 'worse'}`,
        body: 'peer_consumption_body',
        delta: `${diffPct > 0 ? '+' : ''}${diffPct}%`,
        deltaSecondary: `${diffPct > 0 ? '+' : '-'}${absKwh.toFixed(1)} kWh/100km`,
        chartBars: [
          { value: pb.userLifetimeConsumptionKwhPer100km, formattedValue: `${pb.userLifetimeConsumptionKwhPer100km.toFixed(1)}`, style: 'solid', label: 'chart_you' },
          { value: pb.peerAvgConsumptionKwhPer100km, formattedValue: `${pb.peerAvgConsumptionKwhPer100km.toFixed(1)}`, style: 'solid', muted: true, label: 'chart_avg' },
        ],
      })
    }
  }

  // ── trend_consumption ─────────────────────────────────────────────────────
  if (
    lastMonthStats != null &&
    stats.totalCharges >= 1 &&
    lastMonthStats.totalCharges >= 1 &&
    stats.avgConsumptionKwhPer100km != null &&
    lastMonthStats.avgConsumptionKwhPer100km != null
  ) {
    const diffPct = Math.round(
      ((stats.avgConsumptionKwhPer100km - lastMonthStats.avgConsumptionKwhPer100km) /
        lastMonthStats.avgConsumptionKwhPer100km) *
        100,
    )
    if (Math.abs(diffPct) >= 5) {
      const absKwh = Math.abs(
        stats.avgConsumptionKwhPer100km - lastMonthStats.avgConsumptionKwhPer100km,
      )
      candidates.push({
        id: 'trend_consumption',
        sentiment: diffPct < 0 ? 'positive' : 'neutral',
        headline: `trend_consumption_${diffPct < 0 ? 'down' : 'up'}`,
        body: 'trend_consumption_body',
        delta: `${diffPct > 0 ? '+' : ''}${diffPct}%`,
        deltaSecondary: `${diffPct > 0 ? '+' : '-'}${absKwh.toFixed(1)} kWh/100km`,
        chartBars: [
          { value: lastMonthStats.avgConsumptionKwhPer100km, formattedValue: `${lastMonthStats.avgConsumptionKwhPer100km.toFixed(1)}`, style: 'solid', muted: true, label: 'chart_prev' },
          { value: stats.avgConsumptionKwhPer100km, formattedValue: `${stats.avgConsumptionKwhPer100km.toFixed(1)}`, style: 'solid', label: 'chart_current' },
        ],
      })
    }
  }

  // ── seasonal_delta ────────────────────────────────────────────────────────
  if (
    stats.summerConsumptionKwhPer100km != null &&
    stats.winterConsumptionKwhPer100km != null
  ) {
    const absKwh = stats.winterConsumptionKwhPer100km - stats.summerConsumptionKwhPer100km
    const diffPct = Math.round((absKwh / stats.summerConsumptionKwhPer100km) * 100)
    candidates.push({
      id: 'seasonal_delta',
      sentiment: 'neutral',
      headline: 'seasonal_delta_headline',
      body: 'seasonal_delta_body',
      delta: `+${diffPct}%`,
      deltaSecondary: `+${absKwh.toFixed(1)} kWh/100km`,
      chartBars: [
        { value: stats.summerConsumptionKwhPer100km, formattedValue: `${stats.summerConsumptionKwhPer100km.toFixed(1)}`, style: 'solid', muted: true, label: 'chart_summer' },
        { value: stats.winterConsumptionKwhPer100km, formattedValue: `${stats.winterConsumptionKwhPer100km.toFixed(1)}`, style: 'solid', label: 'chart_winter' },
      ],
    })
  }

  // ── trend_cost ────────────────────────────────────────────────────────────
  const daysElapsed = today.getDate()
  const daysInMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate()
  if (
    lastMonthStats != null &&
    stats.totalCharges >= 1 &&
    lastMonthStats.totalCharges >= 1 &&
    stats.totalCostEur > 0 &&
    lastMonthStats.totalCostEur > 0 &&
    daysElapsed >= 7
  ) {
    const projectedCostEur = (stats.totalCostEur / daysElapsed) * daysInMonth
    const totalDiffPct = Math.round(
      ((projectedCostEur - lastMonthStats.totalCostEur) / lastMonthStats.totalCostEur) * 100,
    )
    if (Math.abs(totalDiffPct) >= 5) {
      const priceDiffPct =
        lastMonthStats.avgCostPerKwh > 0
          ? Math.round(
              ((stats.avgCostPerKwh - lastMonthStats.avgCostPerKwh) / lastMonthStats.avgCostPerKwh) * 100,
            )
          : 0
      const priceSignificant = Math.abs(priceDiffPct) >= 5
      const priceSameDirection = (totalDiffPct > 0) === (priceDiffPct > 0)
      const totalUp = totalDiffPct > 0

      let bodyKey: string
      if (priceSignificant && priceSameDirection) {
        bodyKey = totalUp ? 'trend_cost_body_up_price_up' : 'trend_cost_body_down_price_down'
      } else if (priceSignificant && !priceSameDirection) {
        bodyKey = totalUp ? 'trend_cost_body_up_price_down' : 'trend_cost_body_down_price_up'
      } else {
        bodyKey = totalUp ? 'trend_cost_body_up_volume' : 'trend_cost_body_down_volume'
      }

      const absEur = Math.abs(projectedCostEur - lastMonthStats.totalCostEur)
      candidates.push({
        id: 'trend_cost',
        sentiment: totalUp ? 'warning' : 'positive',
        headline: `trend_cost_${totalUp ? 'up' : 'down'}`,
        body: bodyKey,
        delta: `${totalUp ? '+' : ''}${totalDiffPct}%`,
        deltaSecondary: `${totalUp ? '+' : '-'}${Math.round(absEur)} €`,
        deltaTertiary: (() => {
          const ctNow = stats.avgCostPerKwh * 100
          const ctDiff = (stats.avgCostPerKwh - lastMonthStats!.avgCostPerKwh) * 100
          const sign = ctDiff >= 0 ? '+' : ''
          return `Ø ${ctNow.toFixed(1)} ct (${sign}${ctDiff.toFixed(1)})`
        })(),
        chartBars: [
          { value: lastMonthStats.totalCostEur, formattedValue: `${Math.round(lastMonthStats.totalCostEur)} €`, style: 'solid', muted: true, label: 'chart_prev' },
          { value: stats.totalCostEur, projectedValue: projectedCostEur, formattedValue: `~${Math.round(projectedCostEur)} €`, style: 'solid', label: 'chart_current' },
        ],
      })
    }
  }

  // ── trend_distance ────────────────────────────────────────────────────────
  if (
    lastMonthStats != null &&
    stats.totalCharges >= 1 &&
    lastMonthStats.totalCharges >= 1 &&
    stats.totalDistanceKm != null &&
    lastMonthStats.totalDistanceKm != null &&
    lastMonthStats.totalDistanceKm > 0 &&
    daysElapsed >= 7
  ) {
    const projectedKm = Math.round((stats.totalDistanceKm / daysElapsed) * daysInMonth)
    const diffKm = Math.round(projectedKm - lastMonthStats.totalDistanceKm)
    const diffPct = Math.round((diffKm / lastMonthStats.totalDistanceKm) * 100)
    if (Math.abs(diffPct) >= 10) {
      candidates.push({
        id: 'trend_distance',
        sentiment: 'neutral',
        headline: `trend_distance_${diffKm > 0 ? 'up' : 'down'}`,
        body: 'trend_distance_body',
        delta: `~${diffKm > 0 ? '+' : ''}${diffKm} km`,
        deltaSecondary: `${diffPct > 0 ? '+' : ''}${diffPct}%`,
        chartBars: [
          { value: lastMonthStats.totalDistanceKm!, formattedValue: `${Math.round(lastMonthStats.totalDistanceKm!)} km`, style: 'solid', muted: true, label: 'chart_prev' },
          { value: stats.totalDistanceKm!, projectedValue: projectedKm, formattedValue: `~${projectedKm} km`, style: 'solid', label: 'chart_current' },
        ],
      })
    }
  }

  // ── charge_efficiency ─────────────────────────────────────────────────────
  const eff = stats.chargingEfficiencySplit
  if (eff != null && eff.coveredLogCount >= 1 && eff.gridKwh > 0) {
    const lossRatio = (eff.gridKwh - eff.vehicleKwh) / eff.gridKwh
    if (lossRatio > 0.10) {
      const lossKwh = eff.gridKwh - eff.vehicleKwh
      candidates.push({
        id: 'charge_efficiency',
        sentiment: 'warning',
        headline: 'charge_efficiency_headline',
        body: 'charge_efficiency_body',
        delta: `${Math.round(lossRatio * 100)}%`,
        deltaSecondary: `${lossKwh.toFixed(1)} kWh`,
        chartBars: [
          { value: eff.vehicleKwh, projectedValue: eff.gridKwh, formattedValue: `${eff.vehicleKwh.toFixed(1)} kWh`, style: 'solid', label: 'chart_battery' },
        ],
      })
    }
  }

  // home_charging and dc_ratio intentionally omitted:
  // both values are already shown in the "Ladeverteilung" card above the insights.

  // Max 3, Priorität: peer > trend_cost > trend_consumption > trend_distance > seasonal > charge_efficiency
  return candidates.slice(0, 3)
}

export function useSmartInsights(
  stats: () => StatisticsData | null,
  lastMonthStats: () => StatisticsData | null,
) {
  const { t, locale } = useI18n()

  const insights = computed<SmartInsight[]>(() => {
    const s = stats()
    if (!s) return []
    const raw = computeInsights(s, lastMonthStats())

    const now = new Date()
    const fmt = new Intl.DateTimeFormat(locale.value, { month: 'short' })
    const prevMonthLabel = fmt.format(new Date(now.getFullYear(), now.getMonth() - 1, 1))
    const currentMonthLabel = fmt.format(now)

    return raw.map(insight => ({
      ...insight,
      headline: t(`insights.${insight.headline}`),
      body: t(`insights.${insight.body}`),
      chartBars: insight.chartBars?.map(bar => ({
        ...bar,
        label: bar.label === 'chart_prev' ? prevMonthLabel
          : bar.label === 'chart_current' ? currentMonthLabel
          : t(`insights.${bar.label}`),
      })),
    }))
  })

  const heroInsight = computed(() => insights.value[0] ?? null)

  return { insights, heroInsight }
}
