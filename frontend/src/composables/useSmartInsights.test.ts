import { describe, it, expect } from 'vitest'
import { computeInsights } from './useSmartInsights'
import type { StatisticsData } from './useDashboardStats'

function makeStats(overrides: Partial<StatisticsData> = {}): StatisticsData {
  return {
    totalKwhCharged: 300,
    energyCostEur: 90,
    fixedCostEur: 0,
    totalCostEur: 90,
    avgCostPerKwh: 0.30,
    cheapestChargeEur: 5,
    mostExpensiveChargeEur: 30,
    avgChargeDurationMinutes: 60,
    totalCharges: 20,
    totalDistanceKm: 3000,
    avgConsumptionKwhPer100km: 22,
    estimatedConsumptionCount: 0,
    summerConsumptionKwhPer100km: null,
    winterConsumptionKwhPer100km: null,
    chargesOverTime: [],
    chargingTypeSplit: { acKwh: 240, dcKwh: 40, unknownKwh: 20 },
    locationSplit: { publicKwh: 30, privateKwh: 270 },
    peerBenchmark: null,
    chargingEfficiencySplit: null,
    ...overrides,
  }
}

// ── peer_cost ────────────────────────────────────────────────────────────────

describe('peer_cost insight', () => {
  it('fires when user is cheaper than peers', () => {
    const stats = makeStats({
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 22,
        peerAvgConsumptionKwhPer100km: 20,
        userLifetimeCostPerKwh: 0.25,
        peerAvgCostPerKwh: 0.35,
        uniquePeerUsers: 10,
        peerTripCount: 100,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const insights = computeInsights(stats, null)
    expect(insights.some(i => i.id === 'peer_cost')).toBe(true)
    const insight = insights.find(i => i.id === 'peer_cost')!
    expect(insight.sentiment).toBe('positive')
  })

  it('fires when user is more expensive than peers', () => {
    const stats = makeStats({
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 22,
        peerAvgConsumptionKwhPer100km: 20,
        userLifetimeCostPerKwh: 0.45,
        peerAvgCostPerKwh: 0.30,
        uniquePeerUsers: 10,
        peerTripCount: 100,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const insights = computeInsights(stats, null)
    const insight = insights.find(i => i.id === 'peer_cost')!
    expect(insight.sentiment).toBe('warning')
  })

  it('fires with a single same-country peer (no minimum threshold)', () => {
    const stats = makeStats({
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 22,
        peerAvgConsumptionKwhPer100km: 20,
        userLifetimeCostPerKwh: 0.25,
        peerAvgCostPerKwh: 0.35,
        uniquePeerUsers: 1,
        peerTripCount: 3,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const insights = computeInsights(stats, null)
    expect(insights.some(i => i.id === 'peer_cost')).toBe(true)
  })

  it('guard: does not fire when peerAvgCostPerKwh is null', () => {
    const stats = makeStats({
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 22,
        peerAvgConsumptionKwhPer100km: 20,
        userLifetimeCostPerKwh: 0.25,
        peerAvgCostPerKwh: null,
        uniquePeerUsers: 10,
        peerTripCount: 100,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const insights = computeInsights(stats, null)
    expect(insights.some(i => i.id === 'peer_cost')).toBe(false)
  })
})

// ── peer_consumption ─────────────────────────────────────────────────────────

describe('peer_consumption insight', () => {
  it('fires positive when user consumes less than peers', () => {
    const stats = makeStats({
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 18,
        peerAvgConsumptionKwhPer100km: 22,
        userLifetimeCostPerKwh: null,
        peerAvgCostPerKwh: null,
        uniquePeerUsers: 10,
        peerTripCount: 100,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const insights = computeInsights(stats, null)
    expect(insights.some(i => i.id === 'peer_consumption')).toBe(true)
    expect(insights.find(i => i.id === 'peer_consumption')!.sentiment).toBe('positive')
  })

  it('fires with a single peer (no minimum threshold)', () => {
    const stats = makeStats({
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 18,
        peerAvgConsumptionKwhPer100km: 22,
        userLifetimeCostPerKwh: null,
        peerAvgCostPerKwh: null,
        uniquePeerUsers: 1,
        peerTripCount: 3,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    expect(computeInsights(stats, null).some(i => i.id === 'peer_consumption')).toBe(true)
  })
})

// ── seasonal_delta ───────────────────────────────────────────────────────────

describe('seasonal_delta insight', () => {
  it('fires when both summer and winter consumption available', () => {
    const stats = makeStats({ summerConsumptionKwhPer100km: 18, winterConsumptionKwhPer100km: 25 })
    const insights = computeInsights(stats, null)
    expect(insights.some(i => i.id === 'seasonal_delta')).toBe(true)
  })

  it('guard: does not fire when only summer available', () => {
    const stats = makeStats({ summerConsumptionKwhPer100km: 18, winterConsumptionKwhPer100km: null })
    expect(computeInsights(stats, null).some(i => i.id === 'seasonal_delta')).toBe(false)
  })

  it('guard: does not fire when only winter available', () => {
    const stats = makeStats({ summerConsumptionKwhPer100km: null, winterConsumptionKwhPer100km: 25 })
    expect(computeInsights(stats, null).some(i => i.id === 'seasonal_delta')).toBe(false)
  })
})

// ── trend_consumption ─────────────────────────────────────────────────────────

describe('trend_consumption insight', () => {
  it('fires when both months have >=3 charges and consumption available', () => {
    const stats = makeStats({ totalCharges: 5, avgConsumptionKwhPer100km: 22 })
    const lastMonth = makeStats({ totalCharges: 4, avgConsumptionKwhPer100km: 26 })
    expect(computeInsights(stats, lastMonth).some(i => i.id === 'trend_consumption')).toBe(true)
  })

  it('fires positive when consumption decreased', () => {
    const stats = makeStats({ totalCharges: 5, avgConsumptionKwhPer100km: 20 })
    const lastMonth = makeStats({ totalCharges: 4, avgConsumptionKwhPer100km: 26 })
    const insight = computeInsights(stats, lastMonth).find(i => i.id === 'trend_consumption')!
    expect(insight.sentiment).toBe('positive')
  })

  it('guard: does not fire when current month has 0 charges', () => {
    const stats = makeStats({ totalCharges: 0, avgConsumptionKwhPer100km: 22 })
    const lastMonth = makeStats({ totalCharges: 5, avgConsumptionKwhPer100km: 26 })
    expect(computeInsights(stats, lastMonth).some(i => i.id === 'trend_consumption')).toBe(false)
  })

  it('guard: does not fire when lastMonthStats is null', () => {
    const stats = makeStats({ totalCharges: 5, avgConsumptionKwhPer100km: 22 })
    expect(computeInsights(stats, null).some(i => i.id === 'trend_consumption')).toBe(false)
  })

  it('guard: does not fire when consumption not available', () => {
    const stats = makeStats({ totalCharges: 5, avgConsumptionKwhPer100km: null })
    const lastMonth = makeStats({ totalCharges: 4, avgConsumptionKwhPer100km: null })
    expect(computeInsights(stats, lastMonth).some(i => i.id === 'trend_consumption')).toBe(false)
  })
})

// shared mock date: day 15 of a 30-day month → projection factor 2×
const DAY15 = new Date(2026, 3, 15) // 15 Apr 2026

// ── trend_cost ────────────────────────────────────────────────────────────────

describe('trend_cost insight', () => {
  it('ignores fixed costs - only energy cost is projected', () => {
    // Regression: a one-time 2000 € repair in the current month was extrapolated
    // over the month and produced a +17869% "Ladekosten" insight.
    const stats = makeStats({
      totalCharges: 5,
      energyCostEur: 45,
      fixedCostEur: 2000,
      totalCostEur: 2045,
    })
    const lastMonth = makeStats({
      totalCharges: 4,
      energyCostEur: 100,
      fixedCostEur: 0,
      totalCostEur: 100,
    })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    // 45 € on day 15 → projected 90 €; last month 100 € → -10%
    expect(insight.sentiment).toBe('positive')
    expect(insight.delta).toBe('-10%')
    expect(insight.chartBars![1].formattedValue).toBe('~90 €')
  })

  it('fires positive when projected energy cost is lower than last month by >= 5%', () => {
    // 40 € on day 15 → projected 80 €; last month 100 € → -20%
    const stats = makeStats({ totalCharges: 5, energyCostEur: 40 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight).toBeDefined()
    expect(insight.sentiment).toBe('positive')
  })

  it('fires warning when projected total cost is higher than last month by >= 5%', () => {
    // 60 € on day 15 → projected 120 €; last month 100 € → +20%
    const stats = makeStats({ totalCharges: 5, energyCostEur: 60 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight).toBeDefined()
    expect(insight.sentiment).toBe('warning')
  })

  // body key selection
  it('body: up_price_up when total and price both higher', () => {
    // projected +20%, price/kWh +27%
    const stats = makeStats({ totalCharges: 5, energyCostEur: 60, avgCostPerKwh: 0.38 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100, avgCostPerKwh: 0.30 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.body).toBe('trend_cost_body_up_price_up')
  })

  it('body: up_volume when total higher but price neutral', () => {
    // projected +20%, price/kWh +3% (neutral)
    const stats = makeStats({ totalCharges: 5, energyCostEur: 60, avgCostPerKwh: 0.31 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100, avgCostPerKwh: 0.30 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.body).toBe('trend_cost_body_up_volume')
  })

  it('body: up_price_down when total higher but price cheaper', () => {
    // projected +20%, price/kWh -27%
    const stats = makeStats({ totalCharges: 5, energyCostEur: 60, avgCostPerKwh: 0.22 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100, avgCostPerKwh: 0.30 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.body).toBe('trend_cost_body_up_price_down')
  })

  it('body: down_price_down when total and price both lower', () => {
    // projected -20%, price/kWh -27%
    const stats = makeStats({ totalCharges: 5, energyCostEur: 40, avgCostPerKwh: 0.22 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100, avgCostPerKwh: 0.30 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.body).toBe('trend_cost_body_down_price_down')
  })

  it('body: down_volume when total lower but price neutral', () => {
    // projected -20%, price/kWh +3% (neutral)
    const stats = makeStats({ totalCharges: 5, energyCostEur: 40, avgCostPerKwh: 0.31 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100, avgCostPerKwh: 0.30 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.body).toBe('trend_cost_body_down_volume')
  })

  it('body: down_price_up when total lower but price more expensive', () => {
    // projected -20%, price/kWh +27%
    const stats = makeStats({ totalCharges: 5, energyCostEur: 40, avgCostPerKwh: 0.38 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100, avgCostPerKwh: 0.30 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.body).toBe('trend_cost_body_down_price_up')
  })

  // guards
  it('guard: does not fire when diff < 5%', () => {
    const stats = makeStats({ totalCharges: 5, energyCostEur: 51 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100 })
    expect(computeInsights(stats, lastMonth, DAY15).some(i => i.id === 'trend_cost')).toBe(false)
  })

  it('guard: does not fire before day 7', () => {
    const today = new Date(2026, 3, 6)
    const stats = makeStats({ totalCharges: 5, energyCostEur: 10 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100 })
    expect(computeInsights(stats, lastMonth, today).some(i => i.id === 'trend_cost')).toBe(false)
  })

  it('guard: does not fire when lastMonthStats is null', () => {
    const stats = makeStats({ totalCharges: 5, energyCostEur: 40 })
    expect(computeInsights(stats, null, DAY15).some(i => i.id === 'trend_cost')).toBe(false)
  })

  it('guard: does not fire when current charges = 0', () => {
    const stats = makeStats({ totalCharges: 0, energyCostEur: 40 })
    const lastMonth = makeStats({ totalCharges: 5, energyCostEur: 100 })
    expect(computeInsights(stats, lastMonth, DAY15).some(i => i.id === 'trend_cost')).toBe(false)
  })

  it('guard: does not fire when lastMonth charges = 0', () => {
    const stats = makeStats({ totalCharges: 5, energyCostEur: 40 })
    const lastMonth = makeStats({ totalCharges: 0, energyCostEur: 100 })
    expect(computeInsights(stats, lastMonth, DAY15).some(i => i.id === 'trend_cost')).toBe(false)
  })
})

// ── trend_distance ────────────────────────────────────────────────────────────

describe('trend_distance insight', () => {
  it('fires neutral-up when projected distance exceeds last month by >= 10%', () => {
    // 500 km on day 15 → projected 1000 km; last month 800 km → +25%
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 500 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 800 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_distance')!
    expect(insight).toBeDefined()
    expect(insight.sentiment).toBe('neutral')
    expect(insight.headline).toContain('up')
  })

  it('fires neutral-down when projected distance is below last month by >= 10%', () => {
    // 300 km on day 15 → projected 600 km; last month 800 km → -25%
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 300 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 800 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_distance')!
    expect(insight).toBeDefined()
    expect(insight.headline).toContain('down')
  })

  it('delta shows absolute km with ~ prefix', () => {
    // 500 km on day 15 → projected 1000 km; last month 800 km → +200 km
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 500 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 800 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_distance')!
    expect(insight.delta).toBe('~+200 km')
  })

  it('delta rounds to whole km when last month is a float', () => {
    // Regression: 889.7 aus der API ergab ~+110.29999999999995 km statt ~+110 km
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 500 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 889.7 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_distance')!
    expect(insight.delta).toBe('~+110 km')
  })

  it('guard: does not fire when diff < 10%', () => {
    // 410 km on day 15 → projected 820 km; last month 800 km → +2.5%
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 410 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 800 })
    expect(computeInsights(stats, lastMonth, DAY15).some(i => i.id === 'trend_distance')).toBe(false)
  })

  it('guard: does not fire before day 7', () => {
    const today = new Date(2026, 3, 6) // day 6
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 500 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 300 })
    expect(computeInsights(stats, lastMonth, today).some(i => i.id === 'trend_distance')).toBe(false)
  })

  it('guard: does not fire when totalDistanceKm is null', () => {
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: null })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 800 })
    expect(computeInsights(stats, lastMonth, DAY15).some(i => i.id === 'trend_distance')).toBe(false)
  })

  it('guard: does not fire when lastMonthStats is null', () => {
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 500 })
    expect(computeInsights(stats, null, DAY15).some(i => i.id === 'trend_distance')).toBe(false)
  })

  it('guard: does not fire when current charges = 0', () => {
    const stats = makeStats({ totalCharges: 0, totalDistanceKm: 500 })
    const lastMonth = makeStats({ totalCharges: 5, totalDistanceKm: 300 })
    expect(computeInsights(stats, lastMonth, DAY15).some(i => i.id === 'trend_distance')).toBe(false)
  })
})

// ── charge_efficiency ─────────────────────────────────────────────────────────

describe('charge_efficiency insight', () => {
  it('fires warning when charging loss > 10% with >= 1 covered log', () => {
    const stats = makeStats({
      chargingEfficiencySplit: { gridKwh: 100, vehicleKwh: 85, coveredLogCount: 5, totalLogCount: 10 },
    })
    const insight = computeInsights(stats, null).find(i => i.id === 'charge_efficiency')!
    expect(insight).toBeDefined()
    expect(insight.sentiment).toBe('warning')
  })

  it('guard: does not fire when loss <= 10%', () => {
    const stats = makeStats({
      chargingEfficiencySplit: { gridKwh: 100, vehicleKwh: 92, coveredLogCount: 5, totalLogCount: 10 },
    })
    expect(computeInsights(stats, null).some(i => i.id === 'charge_efficiency')).toBe(false)
  })

  it('guard: does not fire when coveredLogCount = 0', () => {
    const stats = makeStats({
      chargingEfficiencySplit: { gridKwh: 100, vehicleKwh: 80, coveredLogCount: 0, totalLogCount: 10 },
    })
    expect(computeInsights(stats, null).some(i => i.id === 'charge_efficiency')).toBe(false)
  })

  it('guard: does not fire when chargingEfficiencySplit is null', () => {
    const stats = makeStats({ chargingEfficiencySplit: null })
    expect(computeInsights(stats, null).some(i => i.id === 'charge_efficiency')).toBe(false)
  })

  it('chartBars: has 1 bar with value=vehicleKwh and projectedValue=gridKwh', () => {
    const stats = makeStats({
      chargingEfficiencySplit: { gridKwh: 100, vehicleKwh: 85, coveredLogCount: 5, totalLogCount: 10 },
    })
    const insight = computeInsights(stats, null).find(i => i.id === 'charge_efficiency')!
    expect(insight.chartBars).toHaveLength(1)
    expect(insight.chartBars![0].value).toBe(85)
    expect(insight.chartBars![0].projectedValue).toBe(100)
  })
})

// ── chartBars: projection structure ──────────────────────────────────────────

describe('trend_cost chartBars', () => {
  it('has 2 bars; second has value=current and projectedValue=projected', () => {
    // 60 € on day 15 → projected 120 €; last month 100 €
    const stats = makeStats({ totalCharges: 5, energyCostEur: 60 })
    const lastMonth = makeStats({ totalCharges: 4, energyCostEur: 100 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_cost')!
    expect(insight.chartBars).toHaveLength(2)
    expect(insight.chartBars![0].value).toBe(100)       // prev month
    expect(insight.chartBars![0].projectedValue).toBeUndefined()
    expect(insight.chartBars![1].value).toBe(60)        // current actual
    expect(insight.chartBars![1].projectedValue).toBe(120) // projected (60/15*30)
  })
})

describe('trend_distance chartBars', () => {
  it('has 2 bars; second has value=current and projectedValue=projected', () => {
    // 500 km on day 15 → projected 1000 km; last month 800 km
    const stats = makeStats({ totalCharges: 5, totalDistanceKm: 500 })
    const lastMonth = makeStats({ totalCharges: 4, totalDistanceKm: 800 })
    const insight = computeInsights(stats, lastMonth, DAY15).find(i => i.id === 'trend_distance')!
    expect(insight.chartBars).toHaveLength(2)
    expect(insight.chartBars![0].value).toBe(800)        // prev month
    expect(insight.chartBars![0].projectedValue).toBeUndefined()
    expect(insight.chartBars![1].value).toBe(500)        // current actual
    expect(insight.chartBars![1].projectedValue).toBe(1000) // projected (500/15*30)
  })
})

// ── prioritization ────────────────────────────────────────────────────────────

describe('prioritization', () => {
  it('returns max 3 insights', () => {
    const stats = makeStats({
      totalCharges: 10,
      summerConsumptionKwhPer100km: 18,
      winterConsumptionKwhPer100km: 25,
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 18,
        peerAvgConsumptionKwhPer100km: 22,
        userLifetimeCostPerKwh: 0.25,
        peerAvgCostPerKwh: 0.35,
        uniquePeerUsers: 10,
        peerTripCount: 100,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const lastMonth = makeStats({ totalCharges: 4, avgConsumptionKwhPer100km: 26 })
    expect(computeInsights(stats, lastMonth).length).toBeLessThanOrEqual(3)
  })

  it('peer insights come before seasonal insights', () => {
    const stats = makeStats({
      totalCharges: 10,
      summerConsumptionKwhPer100km: 18,
      winterConsumptionKwhPer100km: 25,
      peerBenchmark: {
        userLifetimeConsumptionKwhPer100km: 18,
        peerAvgConsumptionKwhPer100km: 22,
        userLifetimeCostPerKwh: 0.25,
        peerAvgCostPerKwh: 0.35,
        uniquePeerUsers: 10,
        peerTripCount: 100,
        peerLogCount: 50,
        matchType: 'SPEC',
      },
    })
    const insights = computeInsights(stats, null)
    const firstId = insights[0]?.id
    expect(['peer_cost', 'peer_consumption'].includes(firstId)).toBe(true)
  })
})
