export interface CurvePoint {
    ts: number
    kw: number
    /** Gemessener Ladestand in Prozent. Fehlt bei Kurven aus der Zeit vor der
     *  SoC-Anreicherung und bei Sessions ohne Soc-Telemetrie. */
    soc?: number | null
}

export interface SocSeries {
    /** Ladestand in Prozent je Kurvenpunkt, index-gleich zu den Punkten. */
    values: number[]
    /** true = aus dem Ladeverlauf rekonstruiert, nicht gemessen. */
    derived: boolean
}

/**
 * Kumulierte Energie in kWh je Kurvenpunkt, Trapezregel ueber (kw, ts).
 *
 * Die Punkte kommen mit ungleichmaessigen Abstaenden - Tesla streamt on-change,
 * eine 20-Minuten-Pause liegt zwischen zwei Samples genauso wie eine Sekunde.
 * Index-basiertes Aufsummieren wuerde dichte Messphasen ueberbewerten.
 */
export function cumulativeKwh(points: CurvePoint[]): number[] {
    const cum: number[] = [0]
    for (let i = 1; i < points.length; i++) {
        const dtH = (points[i].ts - points[i - 1].ts) / 3_600_000
        cum.push(cum[i - 1] + ((points[i].kw + points[i - 1].kw) / 2) * dtH)
    }
    return cum
}

/**
 * Ladestand je Kurvenpunkt fuer die SoC-Achse.
 *
 * Zwei Quellen, in dieser Reihenfolge:
 *
 * 1. **Gemessen** - der Connector haengt seit der SoC-Anreicherung an jeden
 *    Kurvenpunkt den interpolierten Wert des `Soc`-Signals. Nur wenn *alle*
 *    Punkte einen Wert tragen, ist die Reihe belastbar.
 * 2. **Abgeleitet** - fuer Bestandskurven und Sessions ohne Soc-Telemetrie wird
 *    der Verlauf aus der kumulierten Energie rekonstruiert und auf die im Log
 *    stehenden Grenzen `socBefore -> socAfter` normiert.
 *
 * Der abgeleitete Verlauf setzt eine ueber die Session konstante Ladeeffizienz
 * voraus. Er bildet den Taper korrekt ab (mehr kWh pro Zeit = steilerer SoC),
 * ist aber eine Rekonstruktion und keine Messung - Aufrufer muessen das ueber
 * {@link SocSeries.derived} kenntlich machen.
 *
 * Gibt null zurueck, wenn keine der beiden Quellen traegt.
 */
export function buildSocSeries(
    points: CurvePoint[],
    socBefore: number | null | undefined,
    socAfter: number | null | undefined,
): SocSeries | null {
    if (points.length === 0) return null

    const measured = points.map(p => p.soc)
    if (measured.every(s => typeof s === 'number' && Number.isFinite(s))) {
        return { values: measured as number[], derived: false }
    }

    if (points.length < 2) return null
    if (typeof socBefore !== 'number' || typeof socAfter !== 'number') return null
    const span = socAfter - socBefore
    if (!(span > 0)) return null

    const cum = cumulativeKwh(points)
    const total = cum[cum.length - 1]
    if (!(total > 0)) return null

    return { values: cum.map(kwh => socBefore + (kwh / total) * span), derived: true }
}

/**
 * Ladestand zu einem beliebigen Zeitpunkt, linear zwischen den Kurvenpunkten
 * interpoliert und ausserhalb der Spanne auf die Randwerte geklemmt.
 *
 * Die X-Achsen-Beschriftungen sitzen auf festen Bruchteilen der Zeitspanne und
 * damit fast nie auf einem Messpunkt - deshalb interpoliert statt indiziert.
 */
export function socAtTs(points: CurvePoint[], values: number[], ts: number): number | null {
    const n = Math.min(points.length, values.length)
    if (n === 0) return null
    if (ts <= points[0].ts) return values[0]
    if (ts >= points[n - 1].ts) return values[n - 1]

    for (let i = 1; i < n; i++) {
        if (points[i].ts < ts) continue
        const dt = points[i].ts - points[i - 1].ts
        if (dt <= 0) return values[i]
        const ratio = (ts - points[i - 1].ts) / dt
        return values[i - 1] + ratio * (values[i] - values[i - 1])
    }
    return values[n - 1]
}
