/**
 * Erkennt, welche Express-Wallet Stripe Checkout dem Nutzer auf diesem Gerät
 * voraussichtlich anbietet. Dient nur dem Hinweis auf der Preisseite, nicht
 * der Zahlungsabwicklung - die entscheidet Stripe selbst.
 */
export type ExpressWallet = 'apple' | 'google' | null

export function detectExpressWallet(
  win: { ApplePaySession?: unknown } = typeof window === 'undefined' ? {} : (window as unknown as { ApplePaySession?: unknown }),
  userAgent: string = typeof navigator === 'undefined' ? '' : navigator.userAgent,
): ExpressWallet {
  // ApplePaySession existiert nur in Safari auf Apple-Geräten mit Wallet-Unterstützung.
  if (typeof win.ApplePaySession !== 'undefined') return 'apple'
  if (/android/i.test(userAgent)) return 'google'
  return null
}
