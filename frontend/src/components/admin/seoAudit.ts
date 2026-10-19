import type { SeoAuditCheck, SeoAuditRow } from '../../api/seoService'

export type SeoAuditFilter = 'ALL' | 'ERRORS' | SeoAuditCheck

/** Reihenfolge der Filter-Chips: schwerste Prüfungen zuerst. */
export const CHECK_ORDER: SeoAuditCheck[] = ['STATUS', 'NOINDEX', 'PRERENDER', 'CANONICAL', 'JSONLD', 'BROKEN_LINK',
    'DESCRIPTION', 'TITLE', 'H1', 'NOINDEX_LINK', 'ORPHAN', 'CWV']

export const CHECK_LABELS: Record<SeoAuditCheck, { label: string; hint: string }> = {
    STATUS: { label: 'Status', hint: 'Seite antwortet Googlebot nicht mit 200' },
    NOINDEX: { label: 'noindex', hint: 'Steht in der Sitemap, soll aber nicht in den Index' },
    PRERENDER: { label: 'Prerender', hint: 'Modellseite ohne JS: Modellname im H1, Verbrauch in kWh/100 km, keine Meilen oder Dollar' },
    CANONICAL: { label: 'Canonical', hint: 'Genau ein Canonical, das auf die Seite selbst zeigt' },
    JSONLD: { label: 'JSON-LD', hint: 'Strukturierte Daten lesbar und Pflicht-Typen je Seitentyp vorhanden' },
    BROKEN_LINK: { label: 'Kaputter Link', hint: 'Interner Link auf eine Seite mit Fehlerstatus oder ohne Antwort' },
    NOINDEX_LINK: { label: 'Link auf noindex', hint: 'Interner Link auf eine Seite, die nicht in den Index soll' },
    DESCRIPTION: { label: 'Description', hint: 'Meta Description vorhanden, 70-155 Zeichen' },
    TITLE: { label: 'Title', hint: 'Title vorhanden, 30-60 Zeichen' },
    H1: { label: 'H1', hint: 'Genau ein H1' },
    ORPHAN: { label: 'Verwaist', hint: 'Keine andere Seite der Sitemap verlinkt hierher' },
    CWV: { label: 'Core Web Vitals', hint: 'LCP, CLS oder INP über dem Grenzwert (mobil)' },
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
