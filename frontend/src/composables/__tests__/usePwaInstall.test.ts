// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const capacitor = vi.hoisted(() => ({ native: false }))
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => capacitor.native },
}))

/** Baut das Event nach, das Chrome feuert - inklusive prompt() und userChoice. */
function makeInstallEvent(outcome: 'accepted' | 'dismissed' = 'accepted') {
  const e = new Event('beforeinstallprompt', { cancelable: true }) as Event & {
    prompt: () => Promise<void>
    userChoice: Promise<{ outcome: string }>
  }
  e.prompt = vi.fn(() => Promise.resolve())
  e.userChoice = Promise.resolve({ outcome })
  return e
}

/**
 * Laedt das Composable frisch. Die Listener sitzen auf Modul-Ebene, deshalb muss jeder Test
 * mit einem neuen Modul-State starten - sonst faerbt ein geparktes Event auf den naechsten ab.
 */
async function loadFresh() {
  vi.resetModules()
  return await import('../usePwaInstall')
}

function setDisplayMode(standalone: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: standalone && query === '(display-mode: standalone)',
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

function setUserAgent(ua: string) {
  Object.defineProperty(window.navigator, 'userAgent', { value: ua, configurable: true })
}

const CHROME_ANDROID = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36'
const SAFARI_IOS = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile Safari/604.1'
const FIREFOX_DESKTOP = 'Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0'

describe('usePwaInstall', () => {
  beforeEach(() => {
    capacitor.native = false
    setDisplayMode(false)
    setUserAgent(CHROME_ANDROID)
    delete (window.navigator as { standalone?: boolean }).standalone
  })

  it('parkt ein beforeinstallprompt, das vor dem ersten Composable-Aufruf feuert', async () => {
    // Der eigentliche Regressionsfall: Chrome feuert das Event kurz nach dem Page-Load,
    // also lange bevor der eingeloggte Bereich und damit der Wizard gemountet ist.
    const mod = await loadFresh()
    window.dispatchEvent(makeInstallEvent())

    const { canPrompt, platform } = mod.usePwaInstall()
    expect(canPrompt.value).toBe(true)
    expect(platform.value).toBe('prompt')
  })

  it('verhindert den Standard-Banner, damit der Prompt uns gehoert', async () => {
    const mod = await loadFresh()
    const event = makeInstallEvent()
    window.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    expect(mod.usePwaInstall().canPrompt.value).toBe(true)
  })

  it('zeigt den nativen Prompt und verbraucht das geparkte Event', async () => {
    const mod = await loadFresh()
    const event = makeInstallEvent('accepted')
    window.dispatchEvent(event)

    const { canPrompt, promptInstall } = mod.usePwaInstall()
    const outcome = await promptInstall()

    expect(event.prompt).toHaveBeenCalledOnce()
    expect(outcome).toBe('accepted')
    // Ein Event laesst sich nur einmal anzeigen - danach darf der Button nicht mehr da sein.
    expect(canPrompt.value).toBe(false)
  })

  it('meldet dismissed zurueck, wenn der User abbricht', async () => {
    const mod = await loadFresh()
    window.dispatchEvent(makeInstallEvent('dismissed'))

    expect(await mod.usePwaInstall().promptInstall()).toBe('dismissed')
  })

  it('gibt null zurueck, wenn gar kein Prompt geparkt ist', async () => {
    const mod = await loadFresh()

    expect(await mod.usePwaInstall().promptInstall()).toBe(null)
  })

  it('schaltet nach appinstalled auf installiert um', async () => {
    const mod = await loadFresh()
    window.dispatchEvent(makeInstallEvent())
    const { canPrompt, isStandalone } = mod.usePwaInstall()
    expect(isStandalone.value).toBe(false)

    window.dispatchEvent(new Event('appinstalled'))

    expect(isStandalone.value).toBe(true)
    expect(canPrompt.value).toBe(false)
  })

  it('erkennt die laufende PWA am display-mode', async () => {
    setDisplayMode(true)
    const mod = await loadFresh()

    expect(mod.usePwaInstall().isStandalone.value).toBe(true)
  })

  it('erkennt die iOS-Variante ueber navigator.standalone', async () => {
    // iOS Safari meldet keinen display-mode, sondern setzt navigator.standalone.
    setUserAgent(SAFARI_IOS)
    ;(window.navigator as { standalone?: boolean }).standalone = true
    const mod = await loadFresh()

    expect(mod.usePwaInstall().isStandalone.value).toBe(true)
  })

  it('faellt auf iOS auf die Anleitung zurueck - Safari hat keine Install-API', async () => {
    setUserAgent(SAFARI_IOS)
    const mod = await loadFresh()

    const { platform, canPrompt } = mod.usePwaInstall()
    expect(platform.value).toBe('ios')
    expect(canPrompt.value).toBe(false)
  })

  it('kennzeichnet Browser ohne Prompt und ohne iOS als other', async () => {
    setUserAgent(FIREFOX_DESKTOP)
    const mod = await loadFresh()

    expect(mod.usePwaInstall().platform.value).toBe('other')
  })

  it('meldet in der nativen App native und installiert', async () => {
    // Capacitor laeuft im WebView ohne display-mode standalone - ohne diese Abfrage
    // wuerde die installierte App den Homescreen-Schritt anbieten.
    capacitor.native = true
    const mod = await loadFresh()

    const { platform, canPrompt, isStandalone } = mod.usePwaInstall()
    expect(platform.value).toBe('native')
    expect(canPrompt.value).toBe(false)
    expect(isStandalone.value).toBe(true)
  })

  it('ignoriert ein beforeinstallprompt in der nativen App', async () => {
    capacitor.native = true
    const mod = await loadFresh()
    window.dispatchEvent(makeInstallEvent())

    expect(mod.usePwaInstall().canPrompt.value).toBe(false)
  })
})
