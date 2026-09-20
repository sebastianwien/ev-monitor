import { computed, ref } from 'vue'
import { Capacitor } from '@capacitor/core'

/** Chrome-eigenes Event, das die TS-DOM-Lib nicht kennt. */
export interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

/**
 * Welchen Weg auf den Homescreen dieses Geraet anbietet.
 * `prompt` - ein Klick genuegt, Chrome/Edge/Samsung.
 * `ios`    - Safari hat keine Install-API, es bleibt die Anleitung ueber das Teilen-Menue.
 * `other`  - Browser ohne Prompt und ohne iOS-Weg, generischer Hinweis.
 * `native` - Capacitor-App, die Frage stellt sich nicht.
 */
export type InstallPlatform = 'prompt' | 'ios' | 'other' | 'native'

const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null)
const wasInstalled = ref(false)

/**
 * Der Listener haengt bewusst auf Modul-Ebene, nicht im setup() einer Komponente.
 *
 * Chrome feuert `beforeinstallprompt` genau einmal, kurz nach dem Page-Load. Eine
 * Komponente, die erst nach dem Login gemountet wird, kommt dafuer zu spaet: das Event
 * ist durch, der Install-Button bleibt aus und der User sieht nur die Anleitung. Deshalb
 * importiert main.ts dieses Modul vor `app.mount()`. Modul-Ebene heisst ausserdem: genau
 * einmal registriert, also nichts abzuraeumen und kein Leak ueber Logout/Login-Zyklen.
 */
if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault()
    deferredPrompt.value = e as BeforeInstallPromptEvent
  })
  window.addEventListener('appinstalled', () => {
    wasInstalled.value = true
    deferredPrompt.value = null
  })
}

const isIOS = () => /iPad|iPhone|iPod/.test(navigator.userAgent)

/** iOS Safari meldet keinen display-mode, sondern setzt navigator.standalone. */
const isDisplayedStandalone = () =>
  (navigator as Navigator & { standalone?: boolean }).standalone === true
  || window.matchMedia?.('(display-mode: standalone)').matches === true

export function usePwaInstall() {
  const platform = computed<InstallPlatform>(() => {
    if (Capacitor.isNativePlatform()) return 'native'
    if (deferredPrompt.value) return 'prompt'
    return isIOS() ? 'ios' : 'other'
  })

  const canPrompt = computed(() => platform.value === 'prompt')

  const isStandalone = computed(() =>
    Capacitor.isNativePlatform() || wasInstalled.value || isDisplayedStandalone())

  /**
   * Zeigt den nativen Installations-Dialog und gibt die Entscheidung des Users zurueck.
   * null heisst: auf diesem Geraet gibt es keinen Prompt (iOS, anderer Browser, native App).
   */
  const promptInstall = async (): Promise<'accepted' | 'dismissed' | null> => {
    const event = deferredPrompt.value
    if (!event || !canPrompt.value) return null
    // Chrome laesst jedes Event nur einmal anzeigen, unabhaengig von der Entscheidung.
    deferredPrompt.value = null
    await event.prompt()
    const { outcome } = await event.userChoice
    return outcome
  }

  return { canPrompt, isStandalone, platform, promptInstall }
}
