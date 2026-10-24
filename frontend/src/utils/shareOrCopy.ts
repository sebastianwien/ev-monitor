export type ShareOutcome = 'shared' | 'copied' | 'failed'

/**
 * Reicht Link und optional Text an das System weiter. Ohne Web-Share-API - also auf den
 * meisten Desktops - landen sie in der Zwischenablage. Abbrechen im Teilen-Dialog zaehlt als geteilt.
 */
export async function shareOrCopy(input: { title: string; url: string; text?: string }): Promise<ShareOutcome> {
    if (typeof navigator !== 'undefined' && navigator.share) {
        try {
            await navigator.share(input.text ? { title: input.title, text: input.text, url: input.url } : { title: input.title, url: input.url })
            return 'shared'
        } catch (e: unknown) {
            if (e instanceof Error && e.name === 'AbortError') return 'shared'
        }
    }
    try {
        await navigator.clipboard.writeText(input.text ? `${input.text} ${input.url}` : input.url)
        return 'copied'
    } catch {
        return 'failed'
    }
}
