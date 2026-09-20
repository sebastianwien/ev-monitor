// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import { i18n } from '../../../i18n'

const api = vi.hoisted(() => ({
  cars: [] as unknown[],
  failCars: false,
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}))
vi.mock('../../../api/axios', () => ({
  default: {
    get: (url: string) => {
      api.get(url)
      if (url === '/cars' && api.failCars) return Promise.reject(new Error('offline'))
      if (url === '/cars') return Promise.resolve({ data: api.cars })
      return Promise.resolve({ data: [] })
    },
    post: api.post,
    put: api.put,
  },
}))

const auth = vi.hoisted(() => ({ sub: 'user-1' as string | null }))
vi.mock('../../../stores/auth', () => ({
  useAuthStore: () => ({ user: auth.sub ? { sub: auth.sub } : null, isAuthenticated: () => true }),
}))

vi.mock('../../../services/analytics', () => ({
  analytics: {
    trackOnboardingStarted: vi.fn(),
    trackOnboardingStepViewed: vi.fn(),
    trackOnboardingCompleted: vi.fn(),
    trackOnboardingSkipped: vi.fn(),
  },
}))

const carApi = vi.hoisted(() => ({ createCar: vi.fn() }))
vi.mock('../../../api/carService', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  carService: carApi,
}))

const cars = vi.hoisted(() => ({ brandsFail: false }))
vi.mock('../../../stores/car', () => ({
  useCarStore: () => ({
    getBrands: () => cars.brandsFail
      ? Promise.reject(new Error('offline'))
      : Promise.resolve([{ value: 'TESLA', label: 'Tesla' }]),
    getModelsForBrand: () => Promise.resolve([
      { value: 'MODEL_3', label: 'Model 3', capacities: [{ kWh: 60, variantName: null, vehicleSpecificationId: 's1', trimLevel: null, availableFrom: null, availableTo: null }] },
    ]),
    invalidateCars: vi.fn(),
  }),
}))

const pwa = vi.hoisted(() => ({
  standalone: null as unknown as { value: boolean },
  platform: null as unknown as { value: string },
  promptInstall: vi.fn(),
}))
vi.mock('../../../composables/usePwaInstall', async () => {
  const { ref, computed } = await import('vue')
  pwa.standalone = ref(false)
  pwa.platform = ref('prompt')
  return {
    usePwaInstall: () => ({
      isStandalone: pwa.standalone,
      platform: pwa.platform,
      canPrompt: computed(() => pwa.platform.value === 'prompt'),
      promptInstall: pwa.promptInstall,
    }),
  }
})

import OnboardingWizard from '../OnboardingWizard.vue'

// jsdom kennt matchMedia nicht - useIsMobile fragt es beim Setup ab.
window.matchMedia = ((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
})) as unknown as typeof window.matchMedia

let app: App | null = null
let router: Router

/**
 * Der Schrittwechsel laeuft ueber eine Transition mit mode="out-in": das Verlassen muss
 * durch sein, bevor der naechste Schritt einzieht. Vue loest das ueber requestAnimationFrame,
 * das in jsdom an einem 16ms-Timer haengt - deshalb echte Wartezeit statt nur Ticks.
 */
async function flush() {
  for (let round = 0; round < 3; round++) {
    for (let i = 0; i < 4; i++) await nextTick()
    await new Promise(r => setTimeout(r, 25))
  }
  await nextTick()
}

async function mount() {
  const host = document.createElement('div')
  document.body.appendChild(host)
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:p*', component: { render: () => null } }],
  })
  app = createApp(defineComponent({ render: () => h(OnboardingWizard) }))
  app.use(createPinia()).use(i18n).use(router)
  app.mount(host)
  await flush()
}

const dialog = () => document.body.querySelector<HTMLElement>('[data-testid="onboarding-wizard"]')
const byId = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
const title = () => document.getElementById('onboarding-title')?.textContent?.trim() ?? ''

async function click(id: string) {
  byId(id)!.click()
  await flush()
}

describe('OnboardingWizard: wer ihn zu sehen bekommt', () => {
  beforeEach(() => {
    localStorage.clear()
    auth.sub = 'user-1'
    api.cars = []
    api.failCars = false
    pwa.standalone.value = false
    pwa.platform.value = 'prompt'
    cars.brandsFail = false
    vi.clearAllMocks()
  })

  afterEach(() => {
    app?.unmount()
    document.body.innerHTML = ''
    document.body.style.overflow = ''
  })

  it('oeffnet sich fuer einen User ohne Fahrzeug', async () => {
    await mount()
    expect(dialog()).not.toBeNull()
  })

  it('bleibt zu, wenn der User schon ein Fahrzeug hat, und merkt sich das', async () => {
    api.cars = [{ id: 'car-1' }]
    await mount()

    expect(dialog()).toBeNull()
    expect(localStorage.getItem('onboarding-completed-user-1')).toBe('true')
  })

  it('bleibt zu, wenn der User ihn schon gesehen hat', async () => {
    localStorage.setItem('onboarding-completed-user-1', 'true')
    await mount()

    expect(dialog()).toBeNull()
  })

  it('merkt sich nichts, wenn die Fahrzeug-Abfrage scheitert', async () => {
    // Sonst verliert ein User mit Netzproblem die Einfuehrung dauerhaft.
    api.failCars = true
    await mount()

    expect(dialog()).toBeNull()
    expect(localStorage.getItem('onboarding-completed-user-1')).toBe(null)
  })

  it('oeffnet sich auf Wunsch aus den Einstellungen erneut', async () => {
    localStorage.setItem('onboarding-completed-user-1', 'true')
    localStorage.setItem('onboarding-force', 'true')
    await mount()

    expect(dialog()).not.toBeNull()
    expect(localStorage.getItem('onboarding-force')).toBe(null)
  })

  it('zeigt ohne User-ID nichts an', async () => {
    auth.sub = null
    await mount()

    expect(dialog()).toBeNull()
  })

  it('fragt die Marken erst beim Oeffnen ab, nicht bei jedem App-Start', async () => {
    localStorage.setItem('onboarding-completed-user-1', 'true')
    await mount()

    expect(byId('onboarding-wizard')).toBeNull()
    // Nur die Fahrzeugpruefung lief, sonst nichts.
    expect(api.get).toHaveBeenCalledTimes(0)
  })
})

describe('OnboardingWizard: Ablauf', () => {
  beforeEach(() => {
    localStorage.clear()
    auth.sub = 'user-1'
    api.cars = []
    api.failCars = false
    pwa.standalone.value = false
    pwa.platform.value = 'prompt'
    cars.brandsFail = false
    vi.clearAllMocks()
  })

  afterEach(() => {
    app?.unmount()
    document.body.innerHTML = ''
    document.body.style.overflow = ''
  })

  it('fuehrt in vier Schritten vom Willkommen bis zum Homescreen', async () => {
    await mount()
    expect(title()).toContain('Willkommen')
    expect(byId('onboarding-back')).toBeNull()

    await click('onboarding-primary')
    expect(title()).toContain('E-Auto')

    await click('onboarding-skip')
    expect(title()).toContain('Ladekarte')

    await click('onboarding-skip')
    expect(title()).toContain('Handy')
  })

  it('laesst den letzten Schritt weg, wenn die App schon auf dem Homescreen liegt', async () => {
    pwa.standalone.value = true
    await mount()

    await click('onboarding-primary')
    await click('onboarding-skip')
    expect(title()).toContain('Ladekarte')

    // Statt eines vierten Schritts folgt der Abschluss.
    await click('onboarding-skip')
    expect(dialog()).toBeNull()
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('bietet im Fahrzeug-Schritt das Anlegen an, nicht das Weitergehen', async () => {
    await mount()
    await click('onboarding-primary')

    expect(byId('onboarding-primary')!.textContent).toContain('Auto anlegen')
    expect(byId('onboarding-primary')!.hasAttribute('disabled')).toBe(true)
    expect(byId('onboarding-skip')!.textContent).toContain('Später')
  })

  it('geht im Fahrzeug-Schritt erst durch dessen eigene Phasen zurueck', async () => {
    await mount()
    await click('onboarding-primary')

    document.body.querySelector<HTMLElement>('button.rounded-full')!.click()
    await flush()
    expect(title()).toContain('Modell')

    await click('onboarding-back')
    expect(title()).toContain('E-Auto')

    await click('onboarding-back')
    expect(title()).toContain('Willkommen')
  })

  it('legt das Fahrzeug an und schaltet den Knopf auf Weiter', async () => {
    carApi.createCar.mockResolvedValue({ id: 'car-1' })
    await mount()
    await click('onboarding-primary')

    document.body.querySelector<HTMLElement>('button.rounded-full')!.click()
    await flush()
    document.body.querySelectorAll<HTMLElement>('.grid button')[0].click()
    await flush()
    expect(title()).toContain('Baujahr')

    await click('onboarding-primary')

    expect(carApi.createCar).toHaveBeenCalledOnce()
    expect(title()).toContain('Model 3')
    expect(byId('onboarding-primary')!.textContent).toContain('Weiter')
    expect(byId('onboarding-skip')).toBeNull()
  })

  it('zeigt einen Ladefehler samt Wiederholen statt eines falschen Leer-Hinweises', async () => {
    cars.brandsFail = true
    await mount()
    await click('onboarding-primary')

    expect(byId('car-retry-brands')).not.toBeNull()
    expect(document.body.textContent).not.toContain('Keine Marke gefunden')

    cars.brandsFail = false
    await click('car-retry-brands')

    expect(byId('car-retry-brands')).toBeNull()
    expect(document.body.textContent).toContain('Tesla')
  })

  it('bietet den Homescreen-Knopf als Hauptaktion an', async () => {
    pwa.promptInstall.mockResolvedValue('accepted')
    await mount()
    await click('onboarding-primary')
    await click('onboarding-skip')
    await click('onboarding-skip')

    expect(byId('onboarding-primary')!.textContent).toContain('Homescreen')
    await click('onboarding-primary')

    expect(pwa.promptInstall).toHaveBeenCalledOnce()
  })

  it('zeigt auf iOS die Anleitung statt eines Knopfes', async () => {
    pwa.platform.value = 'ios'
    await mount()
    await click('onboarding-primary')
    await click('onboarding-skip')
    await click('onboarding-skip')

    expect(byId('onboarding-primary')!.textContent).toContain('Dashboard')
    expect(document.body.textContent).toContain('Safari')
  })
})

describe('OnboardingWizard: Schliessen und Bedienbarkeit', () => {
  beforeEach(() => {
    localStorage.clear()
    auth.sub = 'user-1'
    api.cars = []
    api.failCars = false
    pwa.standalone.value = false
    pwa.platform.value = 'prompt'
    cars.brandsFail = false
    vi.clearAllMocks()
  })

  afterEach(() => {
    app?.unmount()
    document.body.innerHTML = ''
    document.body.style.overflow = ''
  })

  it('schliesst per Escape und merkt sich das', async () => {
    await mount()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flush()

    expect(dialog()).toBeNull()
    expect(localStorage.getItem('onboarding-completed-user-1')).toBe('true')
  })

  it('schliesst ueber das Kreuz', async () => {
    await mount()
    await click('onboarding-close')

    expect(dialog()).toBeNull()
  })

  it('sperrt den Seiten-Scroll, solange er offen ist, und gibt ihn wieder frei', async () => {
    await mount()
    expect(document.body.style.overflow).toBe('hidden')

    await click('onboarding-close')

    expect(document.body.style.overflow).not.toBe('hidden')
  })

  it('meldet sich als Dialog mit Fortschritt an', async () => {
    await mount()

    expect(dialog()!.getAttribute('role')).toBe('dialog')
    expect(dialog()!.getAttribute('aria-modal')).toBe('true')
    expect(dialog()!.getAttribute('aria-labelledby')).toBe('onboarding-title')
    const bar = document.body.querySelector('[role="progressbar"]')!
    expect(bar.getAttribute('aria-valuenow')).toBe('1')
    expect(bar.getAttribute('aria-valuemax')).toBe('4')
  })

  it('gibt dem Kreuz eine Beschriftung fuer Screenreader', async () => {
    await mount()

    expect(byId('onboarding-close')!.getAttribute('aria-label')).toBeTruthy()
  })
})
