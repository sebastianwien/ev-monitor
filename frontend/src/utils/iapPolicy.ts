import { Capacitor } from '@capacitor/core'

/**
 * Ob Kauf-/Upgrade-Wege angezeigt werden duerfen.
 *
 * Apple Guideline 3.1.1 verbietet den Verkauf digitaler Inhalte in der App ueber
 * andere Wege als In-App-Purchase. Da der Premium-Kauf ueber Stripe (externer Browser)
 * laeuft, werden alle Kauf-Einstiegspunkte in der nativen App ausgeblendet. Im
 * mobilen Browser / PWA bleibt der Stripe-Checkout unveraendert verfuegbar.
 *
 * Bestehende Premium-User behalten ihre Features (Multiplattform, Guideline 3.1.3(b)) -
 * ausgeblendet wird nur der Kauf-/Upgrade-Weg, nicht die Nutzung.
 */
export function purchasesAvailable(): boolean {
  return !Capacitor.isNativePlatform()
}

/**
 * Ob Spenden-Links (Ko-fi, PayPal) angezeigt werden duerfen.
 *
 * Apple wertet Entwickler-Spenden als digitale Inhalte (Guideline 3.1.1) und hat
 * die "Unterstuetzen"-Links im Review bereits beanstandet. Google Play verlangt
 * fuer solche Tips ebenfalls Play Billing - daher auf allen nativen Plattformen
 * ausblenden, im Web/PWA unveraendert anzeigen.
 */
export function donationsAvailable(): boolean {
  return !Capacitor.isNativePlatform()
}
