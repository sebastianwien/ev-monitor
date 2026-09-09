/**
 * Der Heimtarif des Users: die als privat markierte Ladekarte, die heute gilt.
 *
 * Bewusst nur bei GENAU einem Treffer: mehrere gleichzeitig gueltige Heimtarife sind
 * mehrdeutig, und der Server bepreist dann ebenfalls nichts. Die UI darf deshalb auch
 * keinen Sammel-Nachtrag anbieten, der nichts tun wuerde.
 */
export interface HomeTariffCard {
  id: string
  providerName: string
  label: string | null
  acPricePerKwh: number | null
  activeFrom: string
  activeUntil: string | null
  isPrivate: boolean
}

export function activeHomeTariff<T extends HomeTariffCard>(
  cards: T[] | null | undefined,
  today: string = new Date().toISOString().split('T')[0],
): T | null {
  const active = (cards ?? []).filter(
    (c) => c.isPrivate && c.activeFrom <= today && (c.activeUntil == null || c.activeUntil >= today),
  )
  return active.length === 1 ? active[0] : null
}

/**
 * Was passiert, wenn der User diese Karte als Heimtarif speichert. Zu einem Zeitpunkt gilt
 * genau ein Heimtarif - der Server beendet den bisherigen deshalb automatisch bzw. lehnt eine
 * Ueberschneidung ab. Die UI sagt das vorher, statt den User in den Fehler laufen zu lassen.
 *
 * - `ends`:    der bisherige Heimtarif endet am Tag vor dem neuen
 * - `overlap`: der neue beginnt vor oder am selben Tag wie ein bestehender - das lehnt der
 *              Server ab, weil unentscheidbar waere, welcher gilt
 */
export type HomeTariffConflict<T> = { type: 'ends' | 'overlap'; card: T; endsOn: string } | null

export function homeTariffConflict<T extends HomeTariffCard>(
  cards: T[] | null | undefined,
  form: { isPrivate: boolean; activeFrom: string },
  editingId: string | null = null,
): HomeTariffConflict<T> {
  if (!form.isPrivate || !form.activeFrom) return null
  const others = (cards ?? []).filter(
    (c) => c.isPrivate && c.id !== editingId && !(c.activeUntil != null && c.activeUntil < form.activeFrom),
  )
  const clash = others.find((c) => c.activeFrom >= form.activeFrom)
  if (clash) return { type: 'overlap', card: clash, endsOn: form.activeFrom }
  const previous = others[0]
  return previous ? { type: 'ends', card: previous, endsOn: dayBefore(form.activeFrom) } : null
}

/**
 * Der Vortag als ISO-Datum. Bewusst in UTC gerechnet: mit lokaler Mitternacht verschiebt
 * toISOString() das Datum in jeder Zeitzone oestlich von Greenwich um einen Tag zurueck.
 */
function dayBefore(iso: string): string {
  const d = new Date(iso + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() - 1)
  return d.toISOString().split('T')[0]
}
