import { watch, onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import { Capacitor } from '@capacitor/core'

/**
 * Zieht die Farbe der nativen Statusbar-Icons bei einem Theme-Wechsel zur Laufzeit nach.
 *
 * Den deterministischen Start-Wert setzt die SystemBars-Config (style: 'LIGHT' = dunkle
 * Icons fuer den hellen Light-Mode-Hintergrund) in capacitor.config.ts - das vermeidet das
 * Startup-Race, das ein reines Runtime-setStyle flaky machte. Dieses Composable kuemmert
 * sich nur noch um den Wechsel auf Dark-Mode (helle Icons) und zurueck.
 *
 * Style.Light = dunkle Icons (heller Hintergrund), Style.Dark = helle Icons (dunkler Hintergrund).
 * Nur nativ relevant; im Browser/PWA ein No-op.
 */
export function useStatusBarTheme(isDark: Ref<boolean>) {
  if (!Capacitor.isNativePlatform()) return

  const apply = async () => {
    try {
      const { StatusBar, Style } = await import('@capacitor/status-bar')
      await StatusBar.setStyle({ style: isDark.value ? Style.Dark : Style.Light })
    } catch {
      /* Plugin nicht verfuegbar - still ignorieren */
    }
  }

  // Nur eingreifen, wenn vom (per Config gesetzten) Light-Default abgewichen wird.
  onMounted(() => { if (isDark.value) apply() })
  watch(isDark, apply)

  // Android setzt den Stil nach dem Backgrounden zurueck - im Dark-Mode neu anwenden.
  const onVisible = () => { if (document.visibilityState === 'visible' && isDark.value) apply() }
  document.addEventListener('visibilitychange', onVisible)
  onUnmounted(() => document.removeEventListener('visibilitychange', onVisible))
}
