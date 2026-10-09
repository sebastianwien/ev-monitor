import { ref } from 'vue'
import { Capacitor } from '@capacitor/core'

/** Minimal surface of Google Identity Services we use. */
interface GoogleAccountsId {
  initialize(config: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: 'popup'; auto_select?: boolean; use_fedcm_for_prompt?: boolean }): void
  renderButton(parent: HTMLElement, options: Record<string, string | number>): void
  prompt(): void
}

declare global {
  interface Window { google?: { accounts: { id: GoogleAccountsId } } }
}

const SCRIPT_URL = 'https://accounts.google.com/gsi/client'
const LOAD_TIMEOUT_MS = 4000
const clientId = (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined) ?? ''
let loading: Promise<GoogleAccountsId | null> | null = null

/** Loads the GIS script once; null when blocked (ad blocker, ITP), offline or too slow. */
function loadGis(): Promise<GoogleAccountsId | null> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id)
  loading ??= new Promise(resolve => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    const timer = window.setTimeout(() => resolve(null), LOAD_TIMEOUT_MS)
    script.onload = () => { window.clearTimeout(timer); resolve(window.google?.accounts?.id ?? null) }
    script.onerror = () => { window.clearTimeout(timer); loading = null; resolve(null) }
    document.head.appendChild(script)
  })
  return loading
}

/**
 * Google sign-in for the gate: One Tap plus Google's own button. Loaded only when the gate opens,
 * never in the native app. `unavailable` turns true when the script cannot load, so the caller
 * can offer the redirect login instead.
 */
export function useGoogleIdentity() {
  const enabled = clientId !== '' && !Capacitor.isNativePlatform()
  const unavailable = ref(!enabled)

  async function mount(button: HTMLElement, onCredential: (idToken: string) => void) {
    if (!enabled) return
    const gis = await loadGis()
    if (!gis) {
      unavailable.value = true
      return
    }
    gis.initialize({ client_id: clientId, callback: r => onCredential(r.credential), ux_mode: 'popup', auto_select: false, use_fedcm_for_prompt: true })
    gis.renderButton(button, { type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', shape: 'rectangular', width: button.clientWidth || 320 })
    gis.prompt()
  }

  return { enabled, unavailable, mount }
}
