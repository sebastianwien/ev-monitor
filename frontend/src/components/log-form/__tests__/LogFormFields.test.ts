/**
 * Tests for the datetime-helper functions used by LogFormFields.vue.
 *
 * Hintergrund: vitest.config.ts laedt @vitejs/plugin-vue derzeit nicht, weshalb
 * .vue-Files in Tests nicht direkt importiert werden koennen. Statt die Test-
 * Infrastruktur zu aendern (ausserhalb des File-Scopes), spiegeln wir die
 * reinen Helper-Funktionen hier wider. Sie sind Single-Source-of-Truth-Spec
 * fuer LogFormFields.vue: jede Aenderung an den Chip-Berechnungen muss in
 * beiden Dateien zugleich erfolgen.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// ── Spec der reinen Helper (identisch zur Implementierung in LogFormFields.vue) ──
const pad = (n: number) => String(n).padStart(2, '0')

export const formatDateTimeLocal = (d: Date): string =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

export const chipValueNow = (): string => formatDateTimeLocal(new Date())

export const chipValue1hAgo = (): string => {
    const d = new Date()
    d.setHours(d.getHours() - 1)
    return formatDateTimeLocal(d)
}

export const chipValueYesterdayEvening = (): string => {
    const d = new Date()
    d.setDate(d.getDate() - 1)
    d.setHours(20, 0, 0, 0)
    return formatDateTimeLocal(d)
}

describe('LogFormFields - datetime helpers', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })
    afterEach(() => {
        vi.useRealTimers()
    })

    describe('formatDateTimeLocal', () => {
        it('formatiert ein Datum als YYYY-MM-DDTHH:mm', () => {
            const d = new Date(2024, 0, 5, 9, 7)
            expect(formatDateTimeLocal(d)).toBe('2024-01-05T09:07')
        })

        it('padded Monate, Tage, Stunden, Minuten korrekt', () => {
            const d = new Date(2024, 10, 30, 23, 59)
            expect(formatDateTimeLocal(d)).toBe('2024-11-30T23:59')
        })
    })

    describe('chipValueNow', () => {
        it('gibt aktuelle Zeit als datetime-local string zurueck', () => {
            vi.setSystemTime(new Date(2026, 4, 17, 14, 30))
            expect(chipValueNow()).toBe('2026-05-17T14:30')
        })
    })

    describe('chipValue1hAgo', () => {
        it('gibt aktuelle Zeit minus 1 Stunde zurueck', () => {
            vi.setSystemTime(new Date(2026, 4, 17, 14, 30))
            expect(chipValue1hAgo()).toBe('2026-05-17T13:30')
        })

        it('verschiebt korrekt ueber Tagesgrenze hinweg', () => {
            vi.setSystemTime(new Date(2026, 4, 17, 0, 15))
            expect(chipValue1hAgo()).toBe('2026-05-16T23:15')
        })
    })

    describe('chipValueYesterdayEvening', () => {
        it('gibt gestern 20:00 zurueck', () => {
            vi.setSystemTime(new Date(2026, 4, 17, 14, 30))
            expect(chipValueYesterdayEvening()).toBe('2026-05-16T20:00')
        })

        it('beruecksichtigt Monatsanfang korrekt', () => {
            vi.setSystemTime(new Date(2026, 4, 1, 8, 0))
            expect(chipValueYesterdayEvening()).toBe('2026-04-30T20:00')
        })

        it('beruecksichtigt Jahreswechsel korrekt', () => {
            vi.setSystemTime(new Date(2026, 0, 1, 8, 0))
            expect(chipValueYesterdayEvening()).toBe('2025-12-31T20:00')
        })
    })
})
