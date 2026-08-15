import { ref } from 'vue'
import axiosInstance from '../api/axios'
import { curveShareService, type CurveShare } from '../api/curveShareService'

export type ShareOutcome = 'shared' | 'copied' | 'failed'

/** Warum das Teilen abgelehnt wurde - bestimmt, was das UI anzeigt. */
export type ShareError = 'forbidden' | 'no-curve' | 'failed'

/**
 * Freigabe einer einzelnen Ladekurve.
 *
 * Das Bild kommt bewusst vom Server (`/api/public/curve/{token}/og.png`) und wird
 * nicht im Browser aus dem SVG gerastert: das Chart lebt von Tailwind-Klassen und
 * `currentColor`, ein serialisiertes SVG saehe ausserhalb der Seite anders aus.
 * So ist das Bild im Forum ausserdem garantiert dasselbe wie in der Link-Vorschau.
 */
export function useCurveShare() {
    const share = ref<CurveShare | null>(null)
    const busy = ref(false)
    const error = ref<ShareError | null>(null)

    async function load(logId: string): Promise<void> {
        try {
            share.value = await curveShareService.get(logId)
        } catch {
            share.value = null
        }
    }

    async function enable(logId: string): Promise<CurveShare | null> {
        busy.value = true
        error.value = null
        try {
            share.value = await curveShareService.create(logId)
            return share.value
        } catch (e: unknown) {
            error.value = classify(e)
            return null
        } finally {
            busy.value = false
        }
    }

    async function revoke(logId: string): Promise<void> {
        busy.value = true
        error.value = null
        try {
            await curveShareService.revoke(logId)
            share.value = null
        } catch {
            error.value = 'failed'
        } finally {
            busy.value = false
        }
    }

    /**
     * Reicht den Link an das System weiter. Ohne Web-Share-API - also auf den
     * meisten Desktops - landet er in der Zwischenablage; das Ergebnis sagt dem
     * Aufrufer, welche Rueckmeldung er anzeigen muss.
     */
    async function shareLink(url: string, title: string): Promise<ShareOutcome> {
        if (typeof navigator !== 'undefined' && navigator.share) {
            try {
                await navigator.share({ title, url })
                return 'shared'
            } catch (e: unknown) {
                // Abbruch durch den Nutzer ist kein Fehler und darf keine
                // Fehlermeldung ausloesen.
                if (isAbort(e)) return 'shared'
            }
        }
        return (await copyToClipboard(url)) ? 'copied' : 'failed'
    }

    /** Laedt das Vorschaubild herunter bzw. reicht es an das Teilen-Menue weiter. */
    async function shareImage(token: string, filename: string, title: string): Promise<ShareOutcome> {
        try {
            // Bewusst ueber die Axios-Instanz statt per fetch(): in der nativen
            // App liegt das Backend nicht am WebView-Origin, ein relativer Pfad
            // ginge dort ins Leere. baseURL und die native HTTP-Schicht haengen
            // an der Instanz.
            const res = await axiosInstance.get(`/public/curve/${token}/og.png`, { responseType: 'blob' })
            const blob = res.data as Blob
            const file = new File([blob], filename, { type: 'image/png' })

            if (typeof navigator !== 'undefined' && navigator.canShare?.({ files: [file] }) && navigator.share) {
                try {
                    await navigator.share({ files: [file], title })
                    return 'shared'
                } catch (e: unknown) {
                    if (isAbort(e)) return 'shared'
                    return 'failed'
                }
            }

            triggerDownload(blob, filename)
            return 'copied'
        } catch {
            return 'failed'
        }
    }

    return { share, busy, error, load, enable, revoke, shareLink, shareImage }
}

function isAbort(e: unknown): boolean {
    return e instanceof Error && e.name === 'AbortError'
}

function classify(e: unknown): ShareError {
    const status = (e as { response?: { status?: number } })?.response?.status
    if (status === 403) return 'forbidden'
    if (status === 409) return 'no-curve'
    return 'failed'
}

async function copyToClipboard(text: string): Promise<boolean> {
    try {
        await navigator.clipboard.writeText(text)
        return true
    } catch {
        return false
    }
}

function triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    // Ohne revoke bleibt der Blob bis zum Reload im Speicher.
    URL.revokeObjectURL(url)
}
