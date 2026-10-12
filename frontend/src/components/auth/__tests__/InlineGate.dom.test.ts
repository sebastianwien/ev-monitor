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
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = ''; vi.unstubAllEnvs(); sessionStorage.clear() })

function mount(props: Record<string, unknown> = {}) {
  setActivePinia(createPinia())
  const host = document.createElement('div')
  document.body.appendChild(host)
  const authenticated = vi.fn()
  app = createApp(InlineGate, { lead: 'Dein Ergebnis ist fertig.', onAuthenticated: authenticated, ...props })
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

  async function verifyWith(host: HTMLElement) {
    await fill(host, 'input[type="email"]', 'neu@example.com')
    await submit(host)
    await fill(host, 'input[autocomplete="one-time-code"]', '123456')
    await submit(host)
    const calls = vi.mocked(api.post).mock.calls
    return calls[calls.length - 1][1] as Record<string, unknown>
  }

  it('tags a new account with the gate and its placement when the visitor has no campaign', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
    const { host } = mount({ placement: 'modell:tesla/model_3' })

    const body = await verifyWith(host)

    expect(body.utmMedium).toBe('gate')
    expect(body.utmCampaign).toBe('modell:tesla/model_3')
    expect(body.utmSource).toBeUndefined()
    expect(analytics.track).toHaveBeenCalledWith('gate_code_verified', { placement: 'modell:tesla/model_3' })
  })

  it('keeps the campaign the visitor arrived with and only adds the gate', async () => {
    window.history.replaceState({}, '', '/modelle/tesla/model_3?utm_source=firstev')
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
    const { host } = mount({ placement: 'modell:tesla/model_3' })

    const body = await verifyWith(host)

    expect(body.utmSource).toBe('firstev')
    expect(body.utmMedium).toBe('gate')
    expect(body.utmCampaign).toBe('modell:tesla/model_3')
    window.history.replaceState({}, '', '/')
  })

  it('adds no gate tag without a placement (plain login)', async () => {
    vi.mocked(api.post)
      .mockResolvedValueOnce({ data: { status: 'SENT' } } as never)
      .mockResolvedValueOnce({ data: { token: TOKEN, isPremium: false } } as never)
    const { host } = mount()

    const body = await verifyWith(host)

    expect(body.utmMedium).toBeUndefined()
    expect(body.utmCampaign).toBeUndefined()
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

  it('offers the Google redirect when One Tap is not available and comes back to this page', async () => {
    vi.stubEnv('VITE_GOOGLE_OAUTH_ENABLED', 'true')
    const { host } = mount()

    const link = host.querySelector('a[href="/oauth2/authorization/google"]') as HTMLAnchorElement
    expect(link).not.toBeNull()
    link.addEventListener('click', e => e.preventDefault())
    link.click()
    expect(sessionStorage.getItem('postLoginRedirect')).toBe(window.location.pathname + window.location.search)
  })
})
