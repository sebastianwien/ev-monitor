import { describe, it, expect } from 'vitest'
import { csvCell, searchRows, sortRows, toCsv, type SeoColumn } from '../seoTable'

interface Row { path: string; impressions: number; ctr: number; finding: string | null }
const rows: Row[] = [
    { path: '/modelle/Beta', impressions: 50, ctr: 0.021, finding: null },
    { path: '/modelle/Alpha', impressions: 900, ctr: 0.044, finding: 'MONEY' },
    { path: '/', impressions: 50, ctr: 0.1, finding: 'LEAK' },
]
const columns: SeoColumn<Row>[] = [
    { key: 'path', label: 'Seite', value: r => r.path },
    { key: 'impressions', label: 'Impressionen', numeric: true, value: r => r.impressions },
    { key: 'ctr', label: 'Klickrate', numeric: true, value: r => r.ctr, csv: r => r.ctr * 100 },
    { key: 'finding', label: 'Befund', value: r => r.finding },
]

describe('seoTable', () => {
    it('sorts numbers and text in both directions, nulls always last, stable on ties', () => {
        expect(sortRows(rows, columns[1], 'desc').map(r => r.path)).toEqual(['/modelle/Alpha', '/modelle/Beta', '/'])
        expect(sortRows(rows, columns[1], 'asc').map(r => r.path)).toEqual(['/modelle/Beta', '/', '/modelle/Alpha'])
        expect(sortRows(rows, columns[0], 'asc').map(r => r.path)).toEqual(['/', '/modelle/Alpha', '/modelle/Beta'])
        expect(sortRows(rows, columns[3], 'asc').map(r => r.finding)).toEqual(['LEAK', 'MONEY', null])
        expect(sortRows(rows, columns[3], 'desc').map(r => r.finding)).toEqual(['MONEY', 'LEAK', null])
    })

    it('searches case-insensitively in the given columns only', () => {
        expect(searchRows(rows, 'ALPHA', [columns[0]]).map(r => r.path)).toEqual(['/modelle/Alpha'])
        expect(searchRows(rows, '  ', [columns[0]])).toHaveLength(3)
        expect(searchRows(rows, 'money', [columns[0]])).toHaveLength(0)
    })

    it('writes Excel-friendly CSV: semicolon, decimal comma, quoted text, BOM', () => {
        const csv = toCsv(columns, rows.slice(1, 2))
        expect(csv.startsWith('﻿')).toBe(true)
        const lines = csv.slice(1).split('\r\n')
        expect(lines[0]).toBe('Seite;Impressionen;Klickrate;Befund')
        expect(lines[1]).toBe('/modelle/Alpha;900;4,4;MONEY')
    })

    it('escapes separators, quotes and formula prefixes in CSV cells', () => {
        expect(csvCell('a;b')).toBe('"a;b"')
        expect(csvCell('sag "hallo"')).toBe('"sag ""hallo"""')
        expect(csvCell('=HYPERLINK("x")')).toBe('"\'=HYPERLINK(""x"")"')
        expect(csvCell(null)).toBe('')
        expect(csvCell(0.5)).toBe('0,5')
    })
})
