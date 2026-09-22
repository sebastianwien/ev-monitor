// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useCarShare } from '../useCarShare'
import { carShareService } from '../../api/carShareService'

vi.mock('../../api/carShareService', () => ({
    carShareService: {
        create: vi.fn(),
        get: vi.fn(),
        revoke: vi.fn(),
        getPublic: vi.fn(),
    },
}))

const SHARE = { token: 'abc123xyz789', url: 'https://ev-monitor.net/fahrzeug/abc123xyz789' }

describe('useCarShare - Freigabe', () => {
    beforeEach(() => vi.clearAllMocks())

    it('laedt den bestehenden Status', async () => {
        vi.mocked(carShareService.get).mockResolvedValue(SHARE)
        const s = useCarShare()
        await s.load('car-1')
        expect(s.share.value).toEqual(SHARE)
    })

    it('setzt den Share nach erfolgreichem Anlegen', async () => {
        vi.mocked(carShareService.create).mockResolvedValue(SHARE)
        const s = useCarShare()

        expect(await s.enable('car-1')).toEqual(SHARE)
        expect(s.share.value).toEqual(SHARE)
        expect(s.error.value).toBe(false)
        expect(s.busy.value).toBe(false)
    })

    it('meldet einen Fehler beim Anlegen und laesst den Share leer', async () => {
        vi.mocked(carShareService.create).mockRejectedValue(new Error('http'))
        const s = useCarShare()

        expect(await s.enable('car-1')).toBeNull()
        expect(s.share.value).toBeNull()
        expect(s.error.value).toBe(true)
    })

    it('leert den Share nach Widerruf', async () => {
        vi.mocked(carShareService.create).mockResolvedValue(SHARE)
        vi.mocked(carShareService.revoke).mockResolvedValue()
        const s = useCarShare()
        await s.enable('car-1')

        await s.revoke('car-1')

        expect(carShareService.revoke).toHaveBeenCalledWith('car-1')
        expect(s.share.value).toBeNull()
    })
})

describe('useCarShare - Link weitergeben', () => {
    const originalShare = navigator.share
    const originalClipboard = navigator.clipboard

    afterEach(() => {
        Object.defineProperty(navigator, 'share', { value: originalShare, configurable: true })
        Object.defineProperty(navigator, 'clipboard', { value: originalClipboard, configurable: true })
    })

    it('nutzt die Web-Share-API wenn vorhanden', async () => {
        const share = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, 'share', { value: share, configurable: true })
        const s = useCarShare()

        expect(await s.shareLink(SHARE.url, 'Titel')).toBe('shared')
        expect(share).toHaveBeenCalledWith({ title: 'Titel', url: SHARE.url })
    })

    it('kopiert in die Zwischenablage ohne Web-Share-API', async () => {
        Object.defineProperty(navigator, 'share', { value: undefined, configurable: true })
        const writeText = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
        const s = useCarShare()

        expect(await s.shareLink(SHARE.url, 'Titel')).toBe('copied')
        expect(writeText).toHaveBeenCalledWith(SHARE.url)
    })

    it('behandelt einen Abbruch durch den Nutzer nicht als Fehler', async () => {
        const abort = Object.assign(new Error('abort'), { name: 'AbortError' })
        Object.defineProperty(navigator, 'share', { value: vi.fn().mockRejectedValue(abort), configurable: true })
        const s = useCarShare()

        expect(await s.shareLink(SHARE.url, 'Titel')).toBe('shared')
    })
})
