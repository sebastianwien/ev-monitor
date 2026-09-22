// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useCarShare, buildSignature, bannerUrlFor } from '../useCarShare'
import { carShareService } from '../../api/carShareService'

vi.mock('../../api/carShareService', () => ({
    carShareService: {
        create: vi.fn(),
        get: vi.fn(),
        revoke: vi.fn(),
        getPublic: vi.fn(),
    },
}))

const SHARE = { token: 'abc123xyz789', url: 'https://ev-monitor.net/fahrzeug/abc123xyz789', bannerUrl: 'https://ev-monitor.net/api/public/car/abc123xyz789/banner.png' }

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

describe('useCarShare - Forum-Signatur', () => {
    const originalClipboard = navigator.clipboard
    const withBanner = {
        token: 'abc123xyz789',
        url: 'https://ev-monitor.net/fahrzeug/abc123xyz789?ref=MAX&x=1',
        bannerUrl: 'https://ev-monitor.net/api/public/car/abc123xyz789/banner.png',
    }

    afterEach(() => {
        Object.defineProperty(navigator, 'clipboard', { value: originalClipboard, configurable: true })
    })

    it('baut BBCode mit Link (inkl. Referral) und Banner-Bild', () => {
        expect(buildSignature(withBanner, 'Tesla Model 3', 'bbcode')).toBe(
            '[url=https://ev-monitor.net/fahrzeug/abc123xyz789?ref=MAX&x=1][img]https://ev-monitor.net/api/public/car/abc123xyz789/banner.png[/img][/url]',
        )
    })

    it('haengt die Sprache der Bildtexte an die Banner-URL', () => {
        expect(bannerUrlFor(withBanner, 'en')).toBe('https://ev-monitor.net/api/public/car/abc123xyz789/banner.png?lang=en')
        expect(bannerUrlFor(withBanner)).toBe(withBanner.bannerUrl)
        expect(buildSignature(withBanner, 'Tesla Model 3', 'bbcode', 'sv')).toContain('banner.png?lang=sv[/img]')
    })

    it('baut HTML mit escapten Attributen und festen Massen', () => {
        const html = buildSignature(withBanner, 'Tesla "Model" 3', 'html')
        expect(html).toBe(
            '<a href="https://ev-monitor.net/fahrzeug/abc123xyz789?ref=MAX&amp;x=1">'
            + '<img src="https://ev-monitor.net/api/public/car/abc123xyz789/banner.png" alt="Tesla &quot;Model&quot; 3" width="468" height="60"></a>',
        )
    })

    it('kopiert die Signatur in die Zwischenablage', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
        vi.mocked(carShareService.create).mockResolvedValue(withBanner)
        const s = useCarShare()
        await s.enable('car-1')

        expect(await s.copySignature('Tesla Model 3', 'bbcode')).toBe('copied')
        expect(writeText).toHaveBeenCalledWith(buildSignature(withBanner, 'Tesla Model 3', 'bbcode'))
    })

    it('meldet failed ohne Zwischenablage', async () => {
        Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })
        vi.mocked(carShareService.create).mockResolvedValue(withBanner)
        const s = useCarShare()
        await s.enable('car-1')

        expect(await s.copySignature('Tesla Model 3', 'html')).toBe('failed')
    })
})
