export type PurchasedTier = 'AUTOSYNC' | 'AUTOSYNC_LIVE' | 'SUPPORTER'

/**
 * Gekaufter Tarif aus der Stripe-success_url (StripeService.appendTargetTier). Die Erfolgsseite
 * wartet, bis /subscription/status genau diesen Tarif meldet. Alte Links ohne Wert: AutoSync.
 */
export function targetTierFrom(raw: unknown): PurchasedTier {
  return raw === 'AUTOSYNC_LIVE' || raw === 'SUPPORTER' ? raw : 'AUTOSYNC'
}
