// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createApp } from 'vue'
import { useNavCar, NAV_CAR_PAUSE_MS } from '../useNavCar'

vi.mock('../prefersReducedEffects', () => ({ prefersReducedEffects: () => reduced }))
let reduced = false

const mount = (random = () => 0) => {
  let car!: ReturnType<typeof useNavCar>
  const app = createApp({ setup() { car = useNavCar(random); return () => null } })
  app.mount(document.createElement('div'))
  return { car, app }
}
const setHidden = (hidden: boolean) =>
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })
const [MIN, MAX] = NAV_CAR_PAUSE_MS

describe('useNavCar', () => {
  beforeEach(() => { vi.useFakeTimers(); reduced = false; setHidden(false) })
  afterEach(() => vi.useRealTimers())

  it('wartet zwischen 10 und 30 Sekunden', () => {
    expect(NAV_CAR_PAUSE_MS).toEqual([10_000, 30_000])
    const { car } = mount(() => 1)
    vi.advanceTimersByTime(MAX - 1)
    expect(car.driving.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(car.driving.value).toBe(true)
  })

  it('fährt nach rechts, nach der Pause zurück nach links, dann wieder nach rechts', () => {
    const { car } = mount()
    vi.advanceTimersByTime(MIN - 1)
    expect(car.driving.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(car.driving.value).toBe(true)
    expect(car.direction.value).toBe('right')

    car.arrived()
    expect(car.driving.value).toBe(false)
    vi.advanceTimersByTime(MIN)
    expect(car.driving.value).toBe(true)
    expect(car.direction.value).toBe('left')

    car.arrived()
    vi.advanceTimersByTime(MIN)
    expect(car.direction.value).toBe('right')
  })

  it('fährt nicht, wenn der Nutzer weniger Bewegung wünscht', () => {
    reduced = true
    const { car } = mount()
    vi.advanceTimersByTime(MAX * 3)
    expect(car.driving.value).toBe(false)
  })

  it('in einem Hintergrund-Tab fällt die Fahrt aus und kommt nach der nächsten Pause in derselben Richtung', () => {
    setHidden(true)
    const { car } = mount()
    vi.advanceTimersByTime(MIN)
    expect(car.driving.value).toBe(false)
    setHidden(false)
    vi.advanceTimersByTime(MIN)
    expect(car.driving.value).toBe(true)
    expect(car.direction.value).toBe('right')
  })

  it('nach dem Unmount fährt nichts mehr', () => {
    const { car, app } = mount()
    app.unmount()
    vi.advanceTimersByTime(MAX)
    expect(car.driving.value).toBe(false)
  })
})
