// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { i18n } from '../../../i18n'

vi.mock('../../../api/axios', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('../../../services/analytics', () => ({ analytics: { track: vi.fn() } }))
import api from '../../../api/axios'
import { analytics } from '../../../services/analytics'
import InlineGate from '../InlineGate.vue'

// Gültiges JWT-Format (Payload: {"userId":"u1","sub":"neu@example.com"}), Signatur egal
const TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJ1MSIsInN1YiI6Im5ldUBleGFtcGxlLmNvbSJ9.x'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount() {
  setActivePinia(createPinia())
  const host = document.createElement('div')
  document.body.appendChild(host)
  const authenticated = vi.fn()
  app = createApp(InlineGate, { lead: 'Dein Ergebnis ist fertig.', onAuthenticated: authenticated })
  app.use(i18n)
  app.mount(host)
  return { host, authenticated }
}

async function flush() { for (let i = 0; i < 4; i++) { await nextTick(); await Promise.resolve() } }

async function fill(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector(selector) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await nextTick()
}

async function submit(host: HTMLElement) {
  host.querySelector('form')!.dispatchEvent(new Event('submit'))
  await flush()
}

describe('InlineGate', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
    vi.mocked(analytics.track).mockReset()
  })

  it('names what comes before asking for the address', () => {
    const { host } = mount()
    expect(host.textContent).toContain('Dein Ergebnis ist fertig.')
    expect(host.querySelector('input[type="email"]')).not.toBeNull()
  })

  it('requests a code, then logs in with it', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
    const { host, authenticated } = mount()

    await fill(host, 'input[type="email"]', 'neu@example.com')
    await submit(host)

    expect(api.post).toHaveBeenCalledWith('/auth/code/request', expect.objectContaining({ email: 'neu@example.com' }))
    expect(analytics.track).toHaveBeenCalledWith('gate_code_requested')
    const codeInput = host.querySelector('input[autocomplete="one-time-code"]') as HTMLInputElement
    expect(codeInput).not.toBeNull()
    expect(codeInput.getAttribute('inputmode')).toBe('numeric')
    expect(host.textContent).toContain('neu@example.com')

    await fill(host, 'input[autocomplete="one-time-code"]', '123456')
    await submit(host)

    expect(api.post).toHaveBeenLastCalledWith('/auth/code/verify', expect.objectContaining({ email: 'neu@example.com', code: '123456' }))
    expect(analytics.track).toHaveBeenCalledWith('gate_code_verified')
    expect(authenticated).toHaveBeenCalledTimes(1)
  })

  it('shows a clear error for a wrong code and stays open', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockRejectedValueOnce({ response: { status: 400, data: { code: 'INVALID_CODE' } } })
    const { host, authenticated } = mount()

    await fill(host, 'input[type="email"]', 'neu@example.com')
    await submit(host)
    await fill(host, 'input[autocomplete="one-time-code"]', '000000')
    await submit(host)

    expect(host.querySelector('[role="alert"]')?.textContent).toBeTruthy()
    expect(authenticated).not.toHaveBeenCalled()
  })
})
