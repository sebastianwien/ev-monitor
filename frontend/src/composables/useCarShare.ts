import { ref } from 'vue'
import { carShareService, type CarShare } from '../api/carShareService'
import { analytics } from '../services/analytics'
import type { CarShareSource } from './useCarShareSheet'

export type CarShareOutcome = 'shared' | 'copied' | 'failed'
export type SignatureKind = 'bbcode' | 'html'

export const BANNER_WIDTH = 468
export const BANNER_HEIGHT = 60

function escapeAttr(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
}

/** Banner-URL mit Sprache der Bildtexte; ohne Sprache rendert der Server Deutsch. */
export function bannerUrlFor(share: CarShare, lang?: string): string {
    return lang ? `${share.bannerUrl}?lang=${encodeURIComponent(lang)}` : share.bannerUrl
}

/**
 * Schnipsel fuer eine Forum-Signatur: das Banner, verlinkt auf die Fahrzeugseite.
 * BBCode fuer phpBB, XenForo und Co., HTML fuer Foren und Blogs, die das erlauben.
 */
export function buildSignature(share: CarShare, title: string, kind: SignatureKind, lang?: string): string {
    const banner = bannerUrlFor(share, lang)
    if (kind === 'bbcode') {
        return `[url=${share.url}][img]${banner}[/img][/url]`
    }
    return `<a href="${escapeAttr(share.url)}">`
        + `<img src="${escapeAttr(banner)}" alt="${escapeAttr(title)}" width="${BANNER_WIDTH}" height="${BANNER_HEIGHT}"></a>`
}

/**
 * Freigabe einer Fahrzeugseite per Link. Ein Composable pro Fahrzeug-Karte.
 */
export function useCarShare() {
    const share = ref<CarShare | null>(null)
    const busy = ref(false)
    const error = ref(false)

    async function load(carId: string): Promise<void> {
        try {
            share.value = await carShareService.get(carId)
        } catch {
            share.value = null
        }
    }

    async function enable(carId: string, source: CarShareSource = 'car_management'): Promise<CarShare | null> {
        busy.value = true
        error.value = false
        try {
            share.value = await carShareService.create(carId)
            analytics.track('car_share_created', { source })
            return share.value
        } catch {
            error.value = true
            return null
        } finally {
            busy.value = false
        }
    }

    async function revoke(carId: string): Promise<void> {
        busy.value = true
        error.value = false
        try {
            await carShareService.revoke(carId)
            share.value = null
            analytics.track('car_share_revoked')
        } catch {
            error.value = true
        } finally {
            busy.value = false
        }
    }

    /**
     * Reicht den Link an das System weiter. Ohne Web-Share-API - also auf den
     * meisten Desktops - landet er in der Zwischenablage.
     */
    async function shareLink(url: string, title: string, source: CarShareSource = 'car_management'): Promise<CarShareOutcome> {
        const outcome = await shareOrCopy(url, title)
        if (outcome !== 'failed') analytics.track('car_share_link_shared', { method: outcome === 'shared' ? 'share_sheet' : 'clipboard', source })
        return outcome
    }

    async function shareOrCopy(url: string, title: string): Promise<CarShareOutcome> {
        if (typeof navigator !== 'undefined' && navigator.share) {
            try {
                await navigator.share({ title, url })
                return 'shared'
            } catch (e: unknown) {
                if (e instanceof Error && e.name === 'AbortError') return 'shared'
            }
        }
        try {
            await navigator.clipboard.writeText(url)
            return 'copied'
        } catch {
            return 'failed'
        }
    }

    /**
     * Legt den Signatur-Schnipsel in die Zwischenablage. HTML zusaetzlich als text/html,
     * damit WYSIWYG-Editoren ohne Code-Eingabe (WoltLab, Discourse) das verlinkte Banner
     * direkt einfuegen; reine Textfelder erhalten weiter den Quelltext.
     */
    async function copySignature(title: string, kind: SignatureKind, lang?: string): Promise<CarShareOutcome> {
        if (!share.value) return 'failed'
        try {
            const snippet = buildSignature(share.value, title, kind, lang)
            if (kind === 'html' && typeof ClipboardItem !== 'undefined' && navigator.clipboard.write) {
                await navigator.clipboard.write([new ClipboardItem({
                    'text/html': new Blob([snippet], { type: 'text/html' }),
                    'text/plain': new Blob([snippet], { type: 'text/plain' }),
                })])
            } else {
                await navigator.clipboard.writeText(snippet)
            }
            analytics.track('car_share_signature_copied', { kind })
            return 'copied'
        } catch {
            return 'failed'
        }
    }

    return { share, busy, error, load, enable, revoke, shareLink, copySignature }
}
