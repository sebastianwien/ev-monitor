// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp } from 'vue'
import { useNavCar, NAV_CAR_FIRST_MS, NAV_CAR_GAP_MS } from '../useNavCar'

vi.mock('../prefersReducedEffects', () => ({ prefersReducedEffects: () => reduced }))
let reduced = false

// random 0 = jeweils kürzeste Wartezeit, so bleibt der Takt im Test exakt
const mount = () => {
  let car!: ReturnType<typeof useNavCar>
  const app = createApp({ setup() { car = useNavCar(() => 0); return () => null } })
  app.mount(document.createElement('div'))
  return { car, app }
}
const setHidden = (hidden: boolean) =>
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })

describe('useNavCar', () => {
  beforeEach(() => { vi.useFakeTimers(); reduced = false; setHidden(false) })
  afterEach(() => vi.useRealTimers())

  it('fährt erst nach der ersten Pause los und nach dem Durchfahren erst wieder nach der langen Pause', () => {
    const { car } = mount()
    vi.advanceTimersByTime(NAV_CAR_FIRST_MS[0] - 1)
    expect(car.driving.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(car.driving.value).toBe(true)

    car.arrived()
    expect(car.driving.value).toBe(false)
    vi.advanceTimersByTime(NAV_CAR_GAP_MS[0] - 1)
    expect(car.driving.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(car.driving.value).toBe(true)
  })

  it('fährt nicht, wenn der Nutzer weniger Bewegung wünscht', () => {
    reduced = true
    const { car } = mount()
    vi.advanceTimersByTime(NAV_CAR_FIRST_MS[1] + NAV_CAR_GAP_MS[1])
    expect(car.driving.value).toBe(false)
  })

  it('in einem Hintergrund-Tab fällt die Fahrt aus und kommt nach der nächsten Pause', () => {
    setHidden(true)
    const { car } = mount()
    vi.advanceTimersByTime(NAV_CAR_FIRST_MS[0])
    expect(car.driving.value).toBe(false)
    setHidden(false)
    vi.advanceTimersByTime(NAV_CAR_GAP_MS[0])
    expect(car.driving.value).toBe(true)
  })

  it('nach dem Unmount fährt nichts mehr', () => {
    const { car, app } = mount()
    app.unmount()
    vi.advanceTimersByTime(NAV_CAR_FIRST_MS[1])
    expect(car.driving.value).toBe(false)
  })
})
