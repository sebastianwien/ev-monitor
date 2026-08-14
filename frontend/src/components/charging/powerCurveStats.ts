export interface PowerPoint { ts: number; kw: number }

export interface CurveStats {
    /** Hoechster gemessener Momentanwert in kW. */
    peakKw: number
    /** Zeitgewichteter Mittelwert in kW - nicht der Mittelwert der Samples. */
    avgKw: number
    /** Zeitspanne zwischen erstem und letztem Messpunkt. */
    durationMs: number
    /** Ueber die Kurve integrierte Energie in kWh. */
    energyKwh: number
}

/**
 * Kennzahlen einer Ladekurve fuer den Modal-Header.
 *
 * Bewusst aus den Kurvenpunkten abgeleitet statt aus den Log-Feldern: die Kurve
 * ist die feinere Quelle (Tesla streamt on-change), und Peak/Schnitt existieren
 * am Log ueberhaupt nicht.
 *
 * Die Punkte kommen mit ungleichmaessigen Abstaenden - eine 20-Minuten-Pause
 * liegt zwischen zwei Samples genauso wie eine Sekunde. Deshalb wird die Energie
 * per Trapezregel ueber (kw, ts) integriert und der Schnitt daraus abgeleitet;
 * ein arithmetischer Mittelwert ueber die Samples wuerde dichte Messphasen
 * ueberbewerten.
 */
export function computeCurveStats(points: PowerPoint[]): CurveStats | null {
    if (points.length === 0) return null

    const peakKw = Math.max(...points.map(p => p.kw))
    const durationMs = points[points.length - 1].ts - points[0].ts

    let energyKwh = 0
    for (let i = 1; i < points.length; i++) {
        const dtH = (points[i].ts - points[i - 1].ts) / 3_600_000
        energyKwh += ((points[i].kw + points[i - 1].kw) / 2) * dtH
    }

    // Ohne Zeitspanne (Einzelpunkt oder identische Timestamps) gibt es keinen
    // Mittelwert zu bilden - der Peak ist dann die einzige belastbare Aussage.
    const avgKw = durationMs > 0 ? energyKwh / (durationMs / 3_600_000) : peakKw

    return { peakKw, avgKw, durationMs, energyKwh }
}
