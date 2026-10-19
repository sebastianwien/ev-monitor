/**
 * Ladestand als Spanne "62→80%".
 *
 * Einheitliche Darstellung fuer Einzel-Ladungen und Ladegruppen-Teilladungen -
 * vorher hatte jede Fundstelle im Logfeed ihre eigene Template-Kaskade und die
 * Teilladungen zeigten nur den Endwert.
 *
 * Ohne Endwert gibt es nichts sinnvoll anzuzeigen (der Startwert allein sagt
 * nichts ueber die Ladung aus), dann null.
 */
export function formatSocRange(
    before: number | null | undefined,
    after: number | null | undefined,
): string | null {
    if (after == null) return null
    // Live-Quellen (Tesla) liefern Nachkommastellen; ganze Prozent reichen und halten die Zeile schmal.
    const end = Math.round(after)
    const start = before == null ? null : Math.round(before)
    if (start == null || start === end) return `${end}%`
    return `${start}→${end}%`
}
