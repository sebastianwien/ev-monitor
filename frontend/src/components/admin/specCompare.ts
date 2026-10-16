/** Gemeinsame Bausteine der Admin-Spec-Ansichten: Spaltenraster und Grün/Gelb-Vergleich gegen eine Spec-Zeile. */

/** Feste Spalten ab lg, in Tab-Kopfzeile, Spec-Zeile und Autos darunter identisch: minmax(0, …) und feste Aktionsspalte, damit Inhalt die Spuren nicht verschiebt. */
export const SPEC_GRID = 'lg:grid lg:grid-cols-[minmax(0,1.8fr)_minmax(0,1fr)_minmax(0,1.1fr)_minmax(0,1.8fr)_minmax(0,1.4fr)_minmax(0,1.2fr)_14rem] lg:items-center lg:gap-x-3'

export const MATCH = 'text-green-300'
export const MISMATCH = 'text-amber-300'

/** true grün, false gelb, null (nichts zu vergleichen) neutral. */
export const fitClass = (fits: boolean | null) => fits == null ? '' : fits ? MATCH : MISMATCH

/** Text in Wörter zerlegt, jedes Wort gegen die Wörter von b gefärbt (Groß/klein egal): drin grün, nicht drin gelb, ohne b neutral. */
export function wordParts(a: string, b: string | null | undefined): { text: string; cls: string }[] {
    const words = b == null ? null : new Set(b.toLowerCase().split(/\s+/))
    return a.split(/\s+/).filter(Boolean).map(w => ({ text: w, cls: words == null ? '' : fitClass(words.has(w.toLowerCase())) }))
}

/** Liegt das Jahr im Zeitraum (jahresgenau)? Ohne Jahr oder ohne Beginn: null. */
export function yearFits(year: number | null, from: string | null, to: string | null): boolean | null {
    if (year == null || from == null) return null
    return Number(from.slice(0, 4)) <= year && (to == null || Number(to.slice(0, 4)) >= year)
}
