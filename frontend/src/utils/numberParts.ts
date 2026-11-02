export interface TextPart { text: string; number: boolean }

// Zahl mit Tausender- und Dezimaltrennern, optional mit Minus davor und Prozent dahinter; nicht als Teil eines Worts (CO2) und kein Bindestrich zwischen Zahlen (2-3).
const NUMBER = /(?<![\p{L}\d])[-\u2212]?\d+(?:[.,  ]\d+)*%?/gu
// Dieselbe Zahl samt Einheit dahinter (nach keepUnits mit geschütztem Leerzeichen), "kg CO₂" als eine Einheit.
const NUMBER_WITH_UNIT = /(?<![\p{L}\d])[-\u2212]?\d+(?:[.,  ]\d+)*%?(?:[  ](?:kg[  ]CO₂|kWh|kg|km|mi|ct|kr|€)(?!\p{L}))?/gu

/** Zerlegt einen fertig übersetzten Satz in Text und Zahlen, damit die Zahlen fett gesetzt werden können. */
export function numberParts(text: string, opts?: { units?: boolean }): TextPart[] {
    const parts: TextPart[] = []
    let last = 0
    for (const m of text.matchAll(opts?.units ? NUMBER_WITH_UNIT : NUMBER)) {
        if (m.index > last) parts.push({ text: text.slice(last, m.index), number: false })
        parts.push({ text: m[0], number: true })
        last = m.index + m[0].length
    }
    if (last < text.length || !parts.length) parts.push({ text: text.slice(last), number: false })
    return parts
}
