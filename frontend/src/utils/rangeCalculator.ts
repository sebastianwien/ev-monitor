/**
 * Reichweiten-Rechner der Modell-Detailseite.
 *
 * Die Seite zeigte Reichweite bisher als eine einzige Zahl (Community-Schnitt im
 * weitesten Ladefenster). Reale Reichweite ist aber eine Verteilung: derselbe
 * Wagen faehrt im Sommer auf der Landstrasse deutlich weiter als im Winter auf
 * der Autobahn, und kaum jemand faehrt sein Ladefenster von 100 auf 10 % leer.
 * Diese Funktionen machen beide Achsen - Verbrauch und Ladefenster - einstellbar,
 * damit jeder Nutzer seine eigene Zahl reproduzieren kann statt dem Schnitt
 * widersprechen zu muessen.
 *
 * Alle Verbrauchswerte hier sind kWh/100km. Die Umrechnung nach mi/kWh passiert
 * erst in der Anzeige (`useLocaleFormat`), damit die Rechnung einheitenfrei bleibt.
 */

/** Ladefenster als Start- und Zielladestand in Prozent. */
export interface ChargeWindow {
    /** Ladestand bei Fahrtbeginn, in Prozent. */
    from: number
    /** Ladestand bei Fahrtende, in Prozent. */
    to: number
}

/**
 * Auswaehlbare Ladefenster, absteigend nach nutzbarer Energie.
 *
 * `100→10` steht bewusst vorn: es ist der Default und entspricht exakt der
 * Hero-Kennzahl darueber (netto x 0,9), damit Hero und Rechner sich nie
 * widersprechen. `80→10` ist das Autobahn-Fenster - auf Langstrecke laedt
 * niemand bis 100 %, weil die letzten 20 % zu lange dauern.
 */
export const CHARGE_WINDOWS: readonly ChargeWindow[] = [
    { from: 100, to: 10 },
    { from: 90, to: 10 },
    { from: 80, to: 20 },
    { from: 80, to: 10 },
]

/** Absolute Grenzen der Verbrauchsskala - ausserhalb liegt kein realistisches BEV. */
export const CONSUMPTION_ABS_MIN = 5
export const CONSUMPTION_ABS_MAX = 50

/** Mindestbreite der Skala, damit der Slider auch bei enger Datenlage steuerbar bleibt. */
export const MIN_SCALE_SPAN = 12

/** Fallback-Skala, wenn kein einziger Marker verwertbar ist. */
const FALLBACK_SCALE: ConsumptionScale = { min: 12, max: 28 }

/** Kopfraum der Skala unterhalb des sparsamsten bzw. oberhalb des hungrigsten Markers. */
const PADDING_BELOW = 2
const PADDING_ABOVE = 4

export interface ConsumptionScale {
    min: number
    max: number
}

/**
 * Anteil der Netto-Kapazitaet, den ein Ladefenster nutzt. `null` fuer leere oder
 * invertierte Fenster - daraus laesst sich keine Reichweite ableiten.
 */
export function windowFraction(window: ChargeWindow): number | null {
    const span = window.from - window.to
    if (span <= 0) return null
    return span / 100
}

/**
 * Reichweite in km aus Netto-Kapazitaet, Verbrauch und Ladefenster.
 *
 * Rundet auf 10 km, wie die Hero-Kennzahl: die Eingangsgroessen sind
 * Community-Mittelwerte, eine Zahl auf den Kilometer genau wuerde eine
 * Praezision suggerieren, die die Daten nicht haben.
 *
 * `null`, sobald eine Eingabe fehlt oder unplausibel ist (<= 0) - lieber keine
 * Zahl als eine aus einer Division durch Null.
 */
export function calcRangeKm(
    netCapacityKwh: number | null | undefined,
    consumptionKwhPer100km: number | null | undefined,
    window: ChargeWindow,
): number | null {
    if (netCapacityKwh == null || consumptionKwhPer100km == null) return null
    if (netCapacityKwh <= 0 || consumptionKwhPer100km <= 0) return null
    const fraction = windowFraction(window)
    if (fraction === null) return null

    const usableKwh = netCapacityKwh * fraction
    return Math.round(usableKwh / consumptionKwhPer100km * 100 / 10) * 10
}

/**
 * Verbrauchsskala fuer den Slider, aus den vorhandenen Markern (WLTP, Community,
 * Sommer, Winter) hergeleitet - so passt der Regelweg zum Modell statt zu einer
 * globalen Annahme.
 *
 * Der Kopfraum ist bewusst asymmetrisch: nach oben mehr, weil der hoechste Marker
 * (typisch Winter-Schnitt) noch nicht der Worst Case ist - Autobahn bei 130 und
 * Anhaengerbetrieb liegen darueber. Nach unten reicht wenig, weil unter dem
 * WLTP-Wert real kaum Luft ist.
 */
export function buildConsumptionScale(
    markers: readonly (number | null | undefined)[],
): ConsumptionScale {
    const valid = markers.filter((v): v is number => v != null && v > 0)
    if (valid.length === 0) return { ...FALLBACK_SCALE }

    let min = Math.max(CONSUMPTION_ABS_MIN, Math.floor(Math.min(...valid)) - PADDING_BELOW)
    let max = Math.min(CONSUMPTION_ABS_MAX, Math.ceil(Math.max(...valid)) + PADDING_ABOVE)

    // Enge Marker-Cluster aufweiten - erst nach oben (Worst-Case-Richtung), dann nach unten.
    if (max - min < MIN_SCALE_SPAN) {
        max = Math.min(CONSUMPTION_ABS_MAX, min + MIN_SCALE_SPAN)
        min = Math.max(CONSUMPTION_ABS_MIN, max - MIN_SCALE_SPAN)
    }

    return { min, max }
}

/** Ein belegter Verbrauchswert, den der Nutzer per Chip anspringen kann. */
export interface RangeMarker {
    key: string
    label: string
    value: number
}

/**
 * Filtert und ordnet die Slider-Marken: nur belegte Werte, aufsteigend nach
 * Verbrauch (also in Slider-Richtung von sparsam nach hungrig), und pro
 * Verbrauchswert nur eine Marke - zwei Chips auf derselben Slider-Position
 * (typisch WLTP == Community-Schnitt) wuerden nur verwirren. Bei Gleichstand
 * gewinnt der zuerst uebergebene Kandidat.
 */
export function buildMarkers(
    candidates: readonly { key: string; label: string; value: number | null | undefined }[],
): RangeMarker[] {
    const byValue = new Map<number, RangeMarker>()
    for (const c of candidates) {
        if (c.value == null || c.value <= 0) continue
        if (!byValue.has(c.value)) byValue.set(c.value, { key: c.key, label: c.label, value: c.value })
    }
    return [...byValue.values()].sort((a, b) => a.value - b.value)
}

/** Haelt einen Verbrauchswert innerhalb der Skala - der Slider kann nicht mehr hergeben. */
export function clampConsumption(value: number, scale: ConsumptionScale): number {
    return Math.min(scale.max, Math.max(scale.min, value))
}
