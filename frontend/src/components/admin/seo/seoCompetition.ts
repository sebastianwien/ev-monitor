/**
 * Sichtbarkeitsanteil: unsere Impressionen hochgerechnet auf 30 Tage geteilt durch das monatliche Suchvolumen,
 * höchstens 100 %. null ohne Volumen.
 */
export function visibilityShare(impressions: number, days: number, volume: number | null): number | null {
  if (!volume || volume <= 0 || days <= 0) return null
  return Math.min(1, (impressions * 30) / days / volume)
}

/** Google Ads liefert CPC in US-Dollar; ohne Umrechnung, damit kein falscher Kurs drinsteckt. */
export function usd(v: number | null | undefined): string {
  if (v == null) return '-'
  return `${v.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} US$`
}

/** Nur http(s)-Links aus fremden Suchergebnissen übernehmen, nie javascript: oder data:. */
export function safeHref(url: string | null | undefined): string | undefined {
  return url && /^https?:\/\//i.test(url) ? url : undefined
}
