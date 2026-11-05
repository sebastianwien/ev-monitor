import { describe, it, expect } from 'vitest'
import type { SeoAuditRow } from '../../../../api/seoService'
import { auditCountsByPath, checksPresent, filterAuditRows, gscInspectUrl, indexState } from '../seoAudit'

const row = (path: string, findings: SeoAuditRow['findings']): SeoAuditRow => ({
    path, httpStatus: 200, score: 100, findings, vitals: null, index: null,
    errors: findings.filter(f => f.severity === 'ERROR').length,
    warnings: findings.filter(f => f.severity === 'WARNING').length,
})

const rows: SeoAuditRow[] = [
    row('/modelle/Alpha/One', [{ check: 'JSONLD', severity: 'ERROR', message: 'JSON-LD Product fehlt' }, { check: 'TITLE', severity: 'WARNING', message: 'Title hat 76 Zeichen' }]),
    row('/modelle/Beta', [{ check: 'ORPHAN', severity: 'WARNING', message: 'Keine andere Seite verlinkt hierher' }]),
    row('/', []),
]

describe('seoAudit', () => {
    it('filters by check, by errors only, or not at all', () => {
        expect(filterAuditRows(rows, 'ALL').map(r => r.path)).toEqual(['/modelle/Alpha/One', '/modelle/Beta', '/'])
        expect(filterAuditRows(rows, 'ERRORS').map(r => r.path)).toEqual(['/modelle/Alpha/One'])
        expect(filterAuditRows(rows, 'ORPHAN').map(r => r.path)).toEqual(['/modelle/Beta'])
        expect(filterAuditRows(rows, 'PRERENDER')).toEqual([])
    })

    it('lists only checks that occur, in fixed order', () => {
        expect(checksPresent(rows)).toEqual(['JSONLD', 'TITLE', 'ORPHAN'])
    })

    it('counts errors and warnings per path for the pages table', () => {
        const counts = auditCountsByPath(rows)
        expect(counts.get('/modelle/Alpha/One')).toEqual({ errors: 1, warnings: 1 })
        expect(counts.get('/')).toEqual({ errors: 0, warnings: 0 })
        expect(counts.has('/unbekannt')).toBe(false)
    })

    it('builds the Search Console inspect link with encoded property and URL', () => {
        expect(gscInspectUrl('/modelle/Tesla/Model_Y')).toBe(
            'https://search.google.com/search-console/inspect?resource_id=sc-domain%3Aev-monitor.net&id=https%3A%2F%2Fev-monitor.net%2Fmodelle%2FTesla%2FModel_Y')
        expect(gscInspectUrl('/modelle/Škoda/Elroq')).toContain('id=https%3A%2F%2Fev-monitor.net%2Fmodelle%2F%25C5%25A0koda%2FElroq')
    })

    it('reads the index state from the inspection', () => {
        const base = { coverageState: null, lastCrawlAt: null, googleCanonical: null, checkedAt: '2026-11-05T05:10:00', indexnowAt: null }
        expect(indexState({ ...base, verdict: 'PASS' })).toBe('INDEXED')
        expect(indexState({ ...base, verdict: 'NEUTRAL' })).toBe('NOT_INDEXED')
        expect(indexState({ ...base, verdict: null, checkedAt: null })).toBe('UNKNOWN')
        expect(indexState(null)).toBe('UNKNOWN')
    })
})
