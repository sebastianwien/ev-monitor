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
