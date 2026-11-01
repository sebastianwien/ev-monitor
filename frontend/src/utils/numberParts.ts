export interface TextPart { text: string; number: boolean }

// Zahl mit Tausender- und Dezimaltrennern, optional mit Prozent; nicht als Teil eines Worts (CO2).
const NUMBER = /(?<![\p{L}\d])\d+(?:[.,  ]\d+)*%?/gu

/** Zerlegt einen fertig übersetzten Satz in Text und Zahlen, damit die Zahlen fett gesetzt werden können. */
export function numberParts(text: string): TextPart[] {
    const parts: TextPart[] = []
    let last = 0
    for (const m of text.matchAll(NUMBER)) {
        if (m.index > last) parts.push({ text: text.slice(last, m.index), number: false })
        parts.push({ text: m[0], number: true })
        last = m.index + m[0].length
    }
    if (last < text.length || !parts.length) parts.push({ text: text.slice(last), number: false })
    return parts
}
