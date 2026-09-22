/**
 * Reine Geometrie fuer die oeffentliche Fahrzeugseite: Stufenlinie des
 * Monatsverbrauchs und die Vergleichsskala gegen den Modell-Schnitt.
 * Keine Verbrauchsformeln - die Werte kommen fertig vom Backend.
 */

export interface MonthValue {
    /** ISO-Datum, erster Tag des Monats. */
    month: string
    consumptionKwhPer100km?: number | null
}

export interface ChartPoint {
    month: string
    value: number
    /** linke Kante des Monats-Slots */
    x: number
    /** Mitte des Slots, fuer Labels */
    cx: number
    y: number
}

export interface StepChart {
    width: number
    height: number
    /** Innenbereich, in dem die Linie liegt. */
    plot: { left: number; top: number; right: number; bottom: number }
    slotWidth: number
    points: ChartPoint[]
    path: string
    ticks: { value: number; y: number }[]
    axisMin: number
    axisMax: number
    avgY: number | null
    winterBands: { x: number; width: number }[]
}

export const CHART_WIDTH = 400
export const CHART_HEIGHT = 190
const PLOT = { left: 28, top: 16, right: CHART_WIDTH, bottom: 164 }

/** Winter wie im Backend: Oktober bis Maerz. */
export function isWinterMonth(isoMonth: string): boolean {
    const m = Number(isoMonth.slice(5, 7))
    return m >= 10 || m <= 3
}

/** Runde Achsenschritte: 1, 2, 5, 10 ... je nach Spanne. */
function tickStep(span: number): number {
    if (span <= 6) return 1
    if (span <= 12) return 2
    if (span <= 30) return 5
    return 10
}

export function buildStepChart(months: MonthValue[], avg: number | null): StepChart {
    const empty: StepChart = {
        width: CHART_WIDTH, height: CHART_HEIGHT, plot: PLOT, slotWidth: 0,
        points: [], path: '', ticks: [], axisMin: 0, axisMax: 0, avgY: null, winterBands: [],
    }
    const withValue = months.filter(m => m.consumptionKwhPer100km != null && m.consumptionKwhPer100km > 0)
    if (withValue.length < 2) return empty

    const values = withValue.map(m => m.consumptionKwhPer100km as number)
    const all = avg != null && avg > 0 ? [...values, avg] : values
    const rawMin = Math.min(...all)
    const rawMax = Math.max(...all)
    const step = tickStep(rawMax - rawMin)
    const axisMin = Math.floor(rawMin / step) * step
    const axisMax = Math.max(axisMin + step, Math.ceil(rawMax / step) * step)

    const plotH = PLOT.bottom - PLOT.top
    const y = (v: number) => PLOT.bottom - ((v - axisMin) / (axisMax - axisMin)) * plotH

    const slotWidth = (PLOT.right - PLOT.left) / withValue.length
    const points: ChartPoint[] = withValue.map((m, i) => {
        const x = PLOT.left + i * slotWidth
        return { month: m.month, value: m.consumptionKwhPer100km as number, x, cx: x + slotWidth / 2, y: y(m.consumptionKwhPer100km as number) }
    })

    const path = points.map((p, i) => {
        const seg = `H${(p.x + slotWidth).toFixed(1)}`
        if (i === 0) return `M${p.x.toFixed(1)} ${p.y.toFixed(1)} ${seg}`
        return `V${p.y.toFixed(1)} ${seg}`
    }).join(' ')

    const ticks: { value: number; y: number }[] = []
    for (let v = axisMin; v <= axisMax + 1e-9; v += step) ticks.push({ value: v, y: y(v) })

    const winterBands: { x: number; width: number }[] = []
    for (const p of points) {
        if (!isWinterMonth(p.month)) continue
        const last = winterBands[winterBands.length - 1]
        if (last && Math.abs(last.x + last.width - p.x) < 0.01) last.width += slotWidth
        else winterBands.push({ x: p.x, width: slotWidth })
    }

    return {
        width: CHART_WIDTH, height: CHART_HEIGHT, plot: PLOT, slotWidth, points, path, ticks,
        axisMin, axisMax, avgY: avg != null && avg > 0 ? y(avg) : null, winterBands,
    }
}

/**
 * Vergleichsskala: Schnitt in der Mitte, plus/minus 40 Prozent an den Raendern,
 * die Enden mit dem Verbrauch beschriftet, der dort laege.
 */
export function peerScale(you: number, avg: number) {
    const rel = (you - avg) / avg
    const youPos = Math.min(96, Math.max(4, 50 + rel * 115))
    return {
        youPos: Math.round(youPos * 10) / 10,
        avgPos: 50,
        minLabel: Math.round(avg * 0.6),
        maxLabel: Math.round(avg * 1.4),
    }
}
