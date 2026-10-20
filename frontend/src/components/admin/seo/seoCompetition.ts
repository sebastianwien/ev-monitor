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

/** Verständlicher Satz zu einer DataForSEO-Fehlermeldung; die Originalmeldung bleibt daneben sichtbar. */
export function competitionErrorHint(message: string | null | undefined): string | null {
  if (!message) return null
  if (message.includes('40104')) return 'Das DataForSEO-Konto ist noch nicht verifiziert. Im Kundenbereich auf app.dataforseo.com bestätigen, dann erneut abrufen.'
  if (/HTTP 401|40100|40101/.test(message)) return 'Login oder API-Passwort stimmen nicht. Die Werte auf der Seite "API Access" bei DataForSEO prüfen.'
  if (/40200|40210|Payment|balance/i.test(message)) return 'Das Guthaben bei DataForSEO ist aufgebraucht. Im Kundenbereich aufladen.'
  if (message.includes('Monatsbudget')) return 'Das Monatsbudget ist erreicht. Der Rest kommt im nächsten Monat, oder das Budget erhöhen (DATAFORSEO_MONTHLY_BUDGET_USD).'
  return null
}
