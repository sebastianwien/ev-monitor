import { ref } from 'vue'
import { carShareService, type CarShare } from '../api/carShareService'

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

/**
 * Schnipsel fuer eine Forum-Signatur: das Banner, verlinkt auf die Fahrzeugseite.
 * BBCode fuer phpBB, XenForo und Co., HTML fuer Foren und Blogs, die das erlauben.
 */
export function buildSignature(share: CarShare, title: string, kind: SignatureKind): string {
    if (kind === 'bbcode') {
        return `[url=${share.url}][img]${share.bannerUrl}[/img][/url]`
    }
    return `<a href="${escapeAttr(share.url)}">`
        + `<img src="${escapeAttr(share.bannerUrl)}" alt="${escapeAttr(title)}" width="${BANNER_WIDTH}" height="${BANNER_HEIGHT}"></a>`
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

    async function enable(carId: string): Promise<CarShare | null> {
        busy.value = true
        error.value = false
        try {
            share.value = await carShareService.create(carId)
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
    async function shareLink(url: string, title: string): Promise<CarShareOutcome> {
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

    /** Legt den Signatur-Schnipsel in die Zwischenablage. */
    async function copySignature(title: string, kind: SignatureKind): Promise<CarShareOutcome> {
        if (!share.value) return 'failed'
        try {
            await navigator.clipboard.writeText(buildSignature(share.value, title, kind))
            return 'copied'
        } catch {
            return 'failed'
        }
    }

    return { share, busy, error, load, enable, revoke, shareLink, copySignature }
}
