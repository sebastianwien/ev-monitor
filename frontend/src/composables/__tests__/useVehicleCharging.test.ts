import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref } from 'vue'
import type { SmartcarConnectionStatus } from '../../api/smartcarService'
import type { VwGroupConnectionStatus } from '../../api/vwGroupService'
import { useVehicleCharging, type ChargingCar } from '../useVehicleCharging'

// Nur Store-Abhaengigkeit ist der Wallbox-Store - via Mock steuerbar.
const { wallboxMock } = vi.hoisted(() => ({ wallboxMock: { isCharging: false } }))
vi.mock('../../stores/wallbox', () => ({ useWallboxStore: () => wallboxMock }))

const smartcarBase: SmartcarConnectionStatus = {
  connected: true, vehicleName: 'BMW i3', carId: 'car-1', vin: 'VIN1',
  vehicleState: 'CHARGING', lastCheckedAt: null, lastSoc: null,
  sessionActive: true, sessionStartedAt: null, sessionEnergyAdded: null,
}
const vwBase: VwGroupConnectionStatus = {
  connected: true, brand: 'VW', vin: 'VIN2', make: null, model: null, year: null,
  mqttActive: true, lastSoc: null, lastRangeKm: null, vehicleState: 'charging', lastCheckedAt: null,
}

const carTesla: ChargingCar = { id: 'car-1', brand: 'Tesla' }
const carVw: ChargingCar = { id: 'car-2', brand: 'VW' }

function setup(opts: {
  cars: ChargingCar[]
  smartcar?: SmartcarConnectionStatus | null
  vw?: VwGroupConnectionStatus | null
}) {
  return useVehicleCharging(
    ref(opts.cars),
    ref(opts.smartcar ?? null),
    ref(opts.vw ?? null),
  )
}

beforeEach(() => {
  wallboxMock.isCharging = false
})

describe('isSmartcarCharging', () => {
  it('true wenn verbunden, CHARGING und carId passt', () => {
    const { isSmartcarCharging } = setup({ cars: [carTesla, carVw], smartcar: smartcarBase })
    expect(isSmartcarCharging(carTesla)).toBe(true)
  })

  it('false fuer anderes Auto (carId passt nicht)', () => {
    const { isSmartcarCharging } = setup({ cars: [carTesla, carVw], smartcar: smartcarBase })
    expect(isSmartcarCharging(carVw)).toBe(false)
  })

  it('carId null -> nur bei Single-Car zuordenbar', () => {
    const nullCarId = { ...smartcarBase, carId: null }
    expect(setup({ cars: [carTesla], smartcar: nullCarId }).isSmartcarCharging(carTesla)).toBe(true)
    expect(setup({ cars: [carTesla, carVw], smartcar: nullCarId }).isSmartcarCharging(carTesla)).toBe(false)
  })

  it('false wenn nicht verbunden oder nicht CHARGING', () => {
    expect(setup({ cars: [carTesla], smartcar: { ...smartcarBase, connected: false } }).isSmartcarCharging(carTesla)).toBe(false)
    expect(setup({ cars: [carTesla], smartcar: { ...smartcarBase, vehicleState: 'NOT_CHARGING' } }).isSmartcarCharging(carTesla)).toBe(false)
    expect(setup({ cars: [carTesla], smartcar: null }).isSmartcarCharging(carTesla)).toBe(false)
  })
})

describe('isVwGroupCharging', () => {
  it('true fuer VW-Group-Brand, verbunden und charging', () => {
    expect(setup({ cars: [carVw], vw: vwBase }).isVwGroupCharging(carVw)).toBe(true)
  })

  it('false fuer Nicht-VW-Group-Brand', () => {
    expect(setup({ cars: [carTesla], vw: vwBase }).isVwGroupCharging(carTesla)).toBe(false)
  })

  it('false wenn nicht charging oder nicht verbunden', () => {
    expect(setup({ cars: [carVw], vw: { ...vwBase, vehicleState: 'not_charging' } }).isVwGroupCharging(carVw)).toBe(false)
    expect(setup({ cars: [carVw], vw: { ...vwBase, connected: false } }).isVwGroupCharging(carVw)).toBe(false)
    expect(setup({ cars: [carVw], vw: null }).isVwGroupCharging(carVw)).toBe(false)
  })
})

describe('isWallboxCharging', () => {
  it('true nur bei Single-Car (Wallbox kennt keine carId)', () => {
    wallboxMock.isCharging = true
    expect(setup({ cars: [carTesla] }).isWallboxCharging()).toBe(true)
    expect(setup({ cars: [carTesla, carVw] }).isWallboxCharging()).toBe(false)
  })

  it('false wenn Wallbox nicht laedt', () => {
    wallboxMock.isCharging = false
    expect(setup({ cars: [carTesla] }).isWallboxCharging()).toBe(false)
  })
})

describe('isVehicleCharging', () => {
  it('ist ODER aus Smartcar/VW/Wallbox', () => {
    // keiner laedt
    expect(setup({ cars: [carTesla, carVw] }).isVehicleCharging(carTesla)).toBe(false)
    // Smartcar laedt car-1
    expect(setup({ cars: [carTesla, carVw], smartcar: smartcarBase }).isVehicleCharging(carTesla)).toBe(true)
    // VW laedt car-2
    expect(setup({ cars: [carTesla, carVw], vw: vwBase }).isVehicleCharging(carVw)).toBe(true)
    // Wallbox laedt (Single-Car)
    wallboxMock.isCharging = true
    expect(setup({ cars: [carTesla] }).isVehicleCharging(carTesla)).toBe(true)
  })
})
