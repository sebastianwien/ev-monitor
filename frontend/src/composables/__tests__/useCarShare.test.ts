// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useCarShare, buildSignature, bannerUrlFor } from '../useCarShare'
import { analytics } from '../../services/analytics'
import { carShareService } from '../../api/carShareService'

vi.mock('../../api/carShareService', () => ({
    carShareService: {
        create: vi.fn(),
        get: vi.fn(),
        revoke: vi.fn(),
        getPublic: vi.fn(),
    },
}))

vi.mock('../../services/analytics', () => ({ analytics: { track: vi.fn() } }))

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

    it('legt HTML zusaetzlich gerendert ab, damit WYSIWYG-Editoren das Banner einfuegen', async () => {
        const write = vi.fn().mockResolvedValue(undefined)
        const writeText = vi.fn().mockResolvedValue(undefined)
        class FakeClipboardItem {
            constructor(public items: Record<string, Blob>) {}
        }
        vi.stubGlobal('ClipboardItem', FakeClipboardItem)
        Object.defineProperty(navigator, 'clipboard', { value: { write, writeText }, configurable: true })
        vi.mocked(carShareService.create).mockResolvedValue(withBanner)
        const s = useCarShare()
        await s.enable('car-1')

        expect(await s.copySignature('Tesla Model 3', 'html')).toBe('copied')
        expect(writeText).not.toHaveBeenCalled()
        const item = write.mock.calls[0][0][0] as FakeClipboardItem
        const html = buildSignature(withBanner, 'Tesla Model 3', 'html')
        expect(item.items['text/html'].type).toBe('text/html')
        expect(await item.items['text/html'].text()).toBe(html)
        expect(await item.items['text/plain'].text()).toBe(html)
        vi.unstubAllGlobals()
    })

    it('faellt ohne ClipboardItem auf reinen Text zurueck', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        vi.stubGlobal('ClipboardItem', undefined)
        Object.defineProperty(navigator, 'clipboard', { value: { write: vi.fn(), writeText }, configurable: true })
        vi.mocked(carShareService.create).mockResolvedValue(withBanner)
        const s = useCarShare()
        await s.enable('car-1')

        expect(await s.copySignature('Tesla Model 3', 'html')).toBe('copied')
        expect(writeText).toHaveBeenCalledWith(buildSignature(withBanner, 'Tesla Model 3', 'html'))
        vi.unstubAllGlobals()
    })

    it('BBCode bleibt reiner Text, auch wenn reiche Zwischenablage verfuegbar ist', async () => {
        const write = vi.fn().mockResolvedValue(undefined)
        const writeText = vi.fn().mockResolvedValue(undefined)
        vi.stubGlobal('ClipboardItem', class { constructor(public items: Record<string, Blob>) {} })
        Object.defineProperty(navigator, 'clipboard', { value: { write, writeText }, configurable: true })
        vi.mocked(carShareService.create).mockResolvedValue(withBanner)
        const s = useCarShare()
        await s.enable('car-1')

        expect(await s.copySignature('Tesla Model 3', 'bbcode')).toBe('copied')
        expect(write).not.toHaveBeenCalled()
        expect(writeText).toHaveBeenCalledWith(buildSignature(withBanner, 'Tesla Model 3', 'bbcode'))
        vi.unstubAllGlobals()
    })

    it('meldet failed ohne Zwischenablage', async () => {
        Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })
        vi.mocked(carShareService.create).mockResolvedValue(withBanner)
        const s = useCarShare()
        await s.enable('car-1')

        expect(await s.copySignature('Tesla Model 3', 'html')).toBe('failed')
    })
})

describe('useCarShare - Plausible-Goals', () => {
    beforeEach(() => vi.mocked(analytics.track).mockClear())

    it('meldet Erstellen und Deaktivieren', async () => {
        vi.mocked(carShareService.create).mockResolvedValue(SHARE)
        vi.mocked(carShareService.revoke).mockResolvedValue(undefined)
        const s = useCarShare()
        await s.enable('car-1')
        expect(analytics.track).toHaveBeenCalledWith('car_share_created', { source: 'car_management' })
        await s.revoke('car-1')
        expect(analytics.track).toHaveBeenCalledWith('car_share_revoked')
    })

    it('meldet das Teilen mit dem Weg, nicht aber einen Fehlschlag', async () => {
        Object.defineProperty(navigator, 'share', { value: undefined, configurable: true })
        Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockResolvedValue(undefined) }, configurable: true })
        const s = useCarShare()
        await s.shareLink(SHARE.url, 'Titel')
        expect(analytics.track).toHaveBeenCalledWith('car_share_link_shared', { method: 'clipboard', source: 'car_management' })

        vi.mocked(analytics.track).mockClear()
        Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockRejectedValue(new Error('nope')) }, configurable: true })
        await s.shareLink(SHARE.url, 'Titel')
        expect(analytics.track).not.toHaveBeenCalled()
    })

    it('traegt den Einstieg als source mit', async () => {
        vi.mocked(carShareService.create).mockResolvedValue(SHARE)
        const s = useCarShare()
        await s.enable('car-1', 'peer_card')
        expect(analytics.track).toHaveBeenCalledWith('car_share_created', { source: 'peer_card' })
    })

    it('meldet die kopierte Signatur mit Format', async () => {
        vi.mocked(carShareService.create).mockResolvedValue(SHARE)
        Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockResolvedValue(undefined) }, configurable: true })
        const s = useCarShare()
        await s.enable('car-1')
        await s.copySignature('Tesla', 'html')
        expect(analytics.track).toHaveBeenCalledWith('car_share_signature_copied', { kind: 'html' })
    })
})
