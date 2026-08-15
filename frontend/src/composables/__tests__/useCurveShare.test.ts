// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useCurveShare } from '../useCurveShare'
import { curveShareService } from '../../api/curveShareService'

vi.mock('../../api/curveShareService', () => ({
    curveShareService: {
        create: vi.fn(),
        get: vi.fn(),
        revoke: vi.fn(),
        getPublic: vi.fn(),
    },
}))

const SHARE = { token: 'abc123xyz789', url: 'https://ev-monitor.net/ladekurve/abc123xyz789' }

function httpError(status: number) {
    return Object.assign(new Error('http'), { response: { status } })
}

describe('useCurveShare - Freigabe', () => {
    beforeEach(() => vi.clearAllMocks())

    it('setzt den Share nach erfolgreichem Anlegen', async () => {
        vi.mocked(curveShareService.create).mockResolvedValue(SHARE)
        const s = useCurveShare()

        const result = await s.enable('log-1')

        expect(result).toEqual(SHARE)
        expect(s.share.value).toEqual(SHARE)
        expect(s.error.value).toBeNull()
        expect(s.busy.value).toBe(false)
    })

    it('meldet fehlendes Entitlement getrennt von anderen Fehlern', async () => {
        vi.mocked(curveShareService.create).mockRejectedValue(httpError(403))
        const s = useCurveShare()

        expect(await s.enable('log-1')).toBeNull()
        expect(s.error.value).toBe('forbidden')
    })

    it('meldet eine fehlende Kurve als eigenen Fall', async () => {
        vi.mocked(curveShareService.create).mockRejectedValue(httpError(409))
        const s = useCurveShare()

        await s.enable('log-1')
        expect(s.error.value).toBe('no-curve')
    })

    it('faellt bei unbekannten Fehlern auf failed zurueck', async () => {
        vi.mocked(curveShareService.create).mockRejectedValue(new Error('offline'))
        const s = useCurveShare()

        await s.enable('log-1')
        expect(s.error.value).toBe('failed')
    })

    it('leert den Share beim Widerrufen', async () => {
        vi.mocked(curveShareService.create).mockResolvedValue(SHARE)
        vi.mocked(curveShareService.revoke).mockResolvedValue()
        const s = useCurveShare()
        await s.enable('log-1')

        await s.revoke('log-1')

        expect(s.share.value).toBeNull()
    })

    it('behaelt den Share wenn das Widerrufen fehlschlaegt', async () => {
        // Sonst glaubt der Nutzer, der Link sei tot, obwohl er noch lebt.
        vi.mocked(curveShareService.create).mockResolvedValue(SHARE)
        vi.mocked(curveShareService.revoke).mockRejectedValue(new Error('offline'))
        const s = useCurveShare()
        await s.enable('log-1')

        await s.revoke('log-1')

        expect(s.share.value).toEqual(SHARE)
        expect(s.error.value).toBe('failed')
    })

    it('schluckt Fehler beim Laden des Status', async () => {
        vi.mocked(curveShareService.get).mockRejectedValue(new Error('offline'))
        const s = useCurveShare()

        await s.load('log-1')

        expect(s.share.value).toBeNull()
    })
})

describe('useCurveShare - Link weitergeben', () => {
    const originalShare = navigator.share
    const originalClipboard = navigator.clipboard

    afterEach(() => {
        Object.defineProperty(navigator, 'share', { value: originalShare, configurable: true, writable: true })
        Object.defineProperty(navigator, 'clipboard', { value: originalClipboard, configurable: true, writable: true })
    })

    function setShareApi(impl: ((data: ShareData) => Promise<void>) | undefined) {
        Object.defineProperty(navigator, 'share', { value: impl, configurable: true, writable: true })
    }

    function setClipboard(writeText: (t: string) => Promise<void>) {
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true, writable: true })
    }

    it('nutzt das System-Teilen wenn vorhanden', async () => {
        const spy = vi.fn().mockResolvedValue(undefined)
        setShareApi(spy)
        const s = useCurveShare()

        expect(await s.shareLink(SHARE.url, 'Ladekurve')).toBe('shared')
        expect(spy).toHaveBeenCalledWith({ title: 'Ladekurve', url: SHARE.url })
    })

    it('wertet einen Abbruch durch den Nutzer nicht als Fehler', async () => {
        // Wer das Teilen-Menue wegwischt, darf keine Fehlermeldung sehen.
        setShareApi(() => Promise.reject(Object.assign(new Error('cancel'), { name: 'AbortError' })))
        const s = useCurveShare()

        expect(await s.shareLink(SHARE.url, 'Ladekurve')).toBe('shared')
    })

    it('kopiert in die Zwischenablage wenn es kein System-Teilen gibt', async () => {
        setShareApi(undefined)
        const writeText = vi.fn().mockResolvedValue(undefined)
        setClipboard(writeText)
        const s = useCurveShare()

        expect(await s.shareLink(SHARE.url, 'Ladekurve')).toBe('copied')
        expect(writeText).toHaveBeenCalledWith(SHARE.url)
    })

    it('meldet failed wenn weder Teilen noch Kopieren geht', async () => {
        setShareApi(undefined)
        setClipboard(() => Promise.reject(new Error('denied')))
        const s = useCurveShare()

        expect(await s.shareLink(SHARE.url, 'Ladekurve')).toBe('failed')
    })
})
