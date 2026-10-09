// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createApp, h, ref, type App } from 'vue'
import { createPinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: null })), post: vi.fn() } }))
vi.mock('../useVoiceQuota', async () => {
  const { ref } = await import('vue')
  return { useVoiceQuota: () => ({ notice: ref(null), load: vi.fn(), set: vi.fn() }) }
})
vi.mock('../../../composables/useVoiceRecorder', async () => {
  const { ref } = await import('vue')
  return { pickMimeType: () => 'audio/webm',
    useVoiceRecorder: () => ({ state: ref('idle'), elapsedMs: ref(0), level: ref(0), press: vi.fn(), release: vi.fn(), cancel: vi.fn() }) }
})
vi.mock('../../../composables/useKeyboardOpen', () => ({ useKeyboardOpen: () => ref(false) }))
vi.mock('../../../services/analytics', () => ({ analytics: { trackVoice: vi.fn() } }))
import { analytics } from '../../../services/analytics'
import VoiceCapture from '../VoiceCapture.vue'

let app: App | null = null
const mount = (variant: 'footer' | 'inline') => {
  // Der Footer-Einstieg teleportiert Hinweis und Knopf in die WizardShell
  document.body.innerHTML = '<div id="wizard-footer-note"></div><div id="wizard-footer-lead"></div>'
  const el = document.createElement('div'); document.body.appendChild(el)
  app = createApp({ render: () => h(VoiceCapture, { carId: 'car-1', variant, entry: 'create' }) })
  app.use(createPinia()).use(i18n).mount(el)
}
beforeEach(() => { localStorage.clear(); vi.mocked(analytics.trackVoice).mockClear() })
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

// Nenner für den Trichter: wie oft wurde das Mikrofon überhaupt gezeigt, bevor es jemand antippt
describe('VoiceCapture: Messung "shown"', () => {
  it('meldet im Footer beim Erscheinen "shown" mit der Stufe', () => {
    mount('footer')
    expect(analytics.trackVoice).toHaveBeenCalledWith('shown', { entry: 'create', stage: 'new' })
  })
  it('meldet "familiar", wenn schon zwei Aufnahmen gemacht wurden', () => {
    localStorage.setItem('voicelog_uses', '2')
    mount('footer')
    expect(analytics.trackVoice).toHaveBeenCalledWith('shown', { entry: 'create', stage: 'familiar' })
  })
  it('meldet inline nichts - das ist kein eigener Einstieg', () => {
    mount('inline')
    expect(analytics.trackVoice).not.toHaveBeenCalledWith('shown', expect.anything())
  })
})
