/** Spalte der gemeinsamen SEO-Tabelle: value für Sortierung, Suche und CSV, format für die Anzeige. */
export interface SeoColumn<T> {
    key: string
    label: string
    /** Erklärung im Spaltenkopf (Tooltip und Screenreader). */
    hint?: string
    numeric?: boolean
    value: (row: T) => string | number | null | undefined
    format?: (row: T) => string
    /** Abweichender CSV-Wert, z. B. Prozent statt Anteil. */
    csv?: (row: T) => string | number | null | undefined
    sortable?: boolean
}

export type SortDir = 'asc' | 'desc'

/** Stabil, leere Werte immer ans Ende. */
export function sortRows<T>(rows: T[], column: SeoColumn<T>, dir: SortDir): T[] {
    const sign = dir === 'asc' ? 1 : -1
    return rows
        .map((row, index) => ({ row, index, v: column.value(row) }))
        .sort((a, b) => {
            const aEmpty = a.v === null || a.v === undefined || a.v === ''
            const bEmpty = b.v === null || b.v === undefined || b.v === ''
            if (aEmpty !== bEmpty) return aEmpty ? 1 : -1
            if (aEmpty && bEmpty) return a.index - b.index
            const cmp = typeof a.v === 'number' && typeof b.v === 'number'
                ? a.v - b.v
                : String(a.v).localeCompare(String(b.v), 'de', { numeric: true, sensitivity: 'base' })
            return cmp === 0 ? a.index - b.index : cmp * sign
        })
        .map(x => x.row)
}

export function searchRows<T>(rows: T[], term: string, columns: SeoColumn<T>[]): T[] {
    const needle = term.trim().toLowerCase()
    if (!needle) return rows
    return rows.filter(row => columns.some(c => String(c.value(row) ?? '').toLowerCase().includes(needle)))
}

/**
 * Eine CSV-Zelle für deutsches Excel: Dezimalkomma, Text in Anführungszeichen, wenn nötig. Werte, die Excel als
 * Formel lesen würde (=, +, -, @ am Anfang), bekommen ein Hochkomma: Suchbegriffe sind fremder Text.
 */
export function csvCell(value: string | number | null | undefined): string {
    if (value === null || value === undefined) return ''
    if (typeof value === 'number') return Number.isFinite(value) ? String(Math.round(value * 10000) / 10000).replace('.', ',') : ''
    let text = value
    if (/^[=+\-@]/.test(text)) text = `'${text}`
    return /[;"\r\n']/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

export function toCsv<T>(columns: SeoColumn<T>[], rows: T[]): string {
    const lines = [columns.map(c => csvCell(c.label)).join(';')]
    for (const row of rows) lines.push(columns.map(c => csvCell((c.csv ?? c.value)(row))).join(';'))
    return '﻿' + lines.join('\r\n')
}

export function downloadCsv(filename: string, csv: string): void {
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
}
