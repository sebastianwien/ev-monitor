/** Zahlen- und Datumsformate des SEO-Tabs, deutsch. */
export const num = (v: number | null | undefined) => Number(v ?? 0).toLocaleString('de-DE')
export const pct = (v: number | null | undefined, digits = 1) => `${(Number(v ?? 0) * 100).toFixed(digits).replace('.', ',')} %`
export const pos = (v: number | null | undefined) => (v ? Number(v).toFixed(1).replace('.', ',') : '-')
export const dec = (v: number, digits = 1) => v.toFixed(digits).replace('.', ',')
export const date = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleDateString('de-DE') : '-')
export const dateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'
export const euro = (v: number | null | undefined) =>
  v == null ? '-' : v.toLocaleString('de-DE', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2 })
