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
