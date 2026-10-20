export interface UtmInput {
    url: string
    source: string
    medium: string
    campaign: string
}

/** Eine Schreibweise für alle: klein, Umlaute ausgeschrieben, alles außer a-z, 0-9, _ und - wird zu "-". */
export function slugUtm(value: string): string {
    return value.trim().toLowerCase()
        .replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
        .replace(/[^a-z0-9_-]+/g, '-')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '')
}

/** Link mit UTM-Parametern auf die eigene Website; null ohne Quelle oder bei fremdem Ziel. */
export function buildUtmLink(input: UtmInput, host = 'ev-monitor.net'): string | null {
    const source = slugUtm(input.source)
    if (!source) return null
    let url: URL
    try {
        url = new URL(input.url.trim())
    } catch {
        return null
    }
    if (url.hostname !== host && url.hostname !== `www.${host}`) return null
    url.searchParams.set('utm_source', source)
    const medium = slugUtm(input.medium)
    if (medium) url.searchParams.set('utm_medium', medium)
    const campaign = slugUtm(input.campaign)
    if (campaign) url.searchParams.set('utm_campaign', campaign)
    return url.toString()
}
