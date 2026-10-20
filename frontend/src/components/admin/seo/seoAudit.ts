import type { SeoAuditCheck, SeoAuditRow } from '../../../api/seoService'

export type SeoAuditFilter = 'ALL' | 'ERRORS' | SeoAuditCheck

/** Reihenfolge der Filter-Chips: schwerste Prüfungen zuerst. */
export const CHECK_ORDER: SeoAuditCheck[] = ['STATUS', 'NOINDEX', 'PRERENDER', 'CANONICAL', 'JSONLD', 'BROKEN_LINK',
    'DESCRIPTION', 'TITLE', 'H1', 'NOINDEX_LINK', 'ORPHAN', 'CWV']

export const CHECK_LABELS: Record<SeoAuditCheck, { label: string; hint: string }> = {
    STATUS: { label: 'Nicht erreichbar', hint: 'Die Seite antwortet Google nicht mit "alles in Ordnung" (HTTP 200). Sie kann so nicht in der Suche erscheinen.' },
    NOINDEX: { label: 'Gesperrt (noindex)', hint: 'Die Seite steht in der Sitemap, verbietet Google aber die Aufnahme. Beides zusammen widerspricht sich: entweder aus der Sitemap nehmen oder freigeben.' },
    PRERENDER: { label: 'Google-Fassung', hint: 'Google bekommt eine vorab erzeugte Fassung der Modellseite. Darin fehlt der Modellname im Titel, der Verbrauch in kWh/100 km, oder es stehen Meilen bzw. Dollar drin.' },
    CANONICAL: { label: 'Canonical', hint: 'Die Seite muss genau einen Canonical-Hinweis haben, der auf sie selbst zeigt. Sonst gibt Google ihre Sichtbarkeit an eine andere Adresse weiter.' },
    JSONLD: { label: 'Strukturierte Daten', hint: 'Maschinenlesbare Angaben für Google. Fehler: ein Block ist unlesbar, oder ein Produkt hat weder Bewertung noch Preis und wird von Google verworfen. Hinweis: Brotkrumen oder Liste fehlen, das kostet nur ein erweitertes Suchergebnis. Ein Produkt-Block ist keine Pflicht, Modellseiten setzen ihn nur mit Fahrerbewertungen.' },
    BROKEN_LINK: { label: 'Kaputter Link', hint: 'Die Seite verlinkt eine eigene Seite, die nicht antwortet oder einen Fehler meldet.' },
    DESCRIPTION: { label: 'Beschreibung', hint: 'Der Text unter dem Titel in den Suchergebnissen fehlt oder hat nicht 70 bis 155 Zeichen. Zu lang wird abgeschnitten, zu kurz verschenkt Platz.' },
    TITLE: { label: 'Titel', hint: 'Die blaue Überschrift im Suchergebnis fehlt oder hat nicht 30 bis 60 Zeichen. Längere Titel schneidet Google ab.' },
    H1: { label: 'Hauptüberschrift', hint: 'Die Seite sollte genau eine Hauptüberschrift (H1) haben.' },
    NOINDEX_LINK: { label: 'Link auf gesperrte Seite', hint: 'Die Seite verlinkt eine eigene Seite, die nicht in den Index soll. Kein Fehler, aber der Link gibt keine Sichtbarkeit weiter.' },
    ORPHAN: { label: 'Ohne interne Links', hint: 'Keine andere Seite der Sitemap verlinkt hierher. Google findet und gewichtet solche Seiten schlechter.' },
    CWV: { label: 'Ladezeit', hint: 'Auf dem Handy lädt die Seite zu langsam oder springt beim Laden (Core Web Vitals über dem Grenzwert).' },
}

export function filterAuditRows(rows: SeoAuditRow[], filter: SeoAuditFilter): SeoAuditRow[] {
    if (filter === 'ALL') return rows
    if (filter === 'ERRORS') return rows.filter(r => r.errors > 0)
    return rows.filter(r => r.findings.some(f => f.check === filter))
}

/** Nur Prüfungen mit mindestens einem Befund, damit keine leeren Chips erscheinen. */
export function checksPresent(rows: SeoAuditRow[]): SeoAuditCheck[] {
    const present = new Set(rows.flatMap(r => r.findings.map(f => f.check)))
    return CHECK_ORDER.filter(c => present.has(c))
}

export function auditCountsByPath(rows: SeoAuditRow[]): Map<string, { errors: number; warnings: number }> {
    return new Map(rows.map(r => [r.path, { errors: r.errors, warnings: r.warnings }]))
}
