import { describe, it, expect } from 'vitest'
import { resolveCapacityForCar } from '../useCarForm'
import type { Car, CapacityOption } from '../../api/carService'

const cap = (kWh: number, specId: string | null = null, trimLevel: string | null = null): CapacityOption => ({
  kWh, variantName: null, vehicleSpecificationId: specId, trimLevel, availableFrom: null, availableTo: null,
})

const car = (batteryKwh: number, specId: string | null = null): Car => ({
  id: 'car-1', userId: 'u-1', brand: 'SKODA', model: 'ENYAQ',
  year: 2022, licensePlate: 'S-EV-1', trim: null,
  batteryCapacityKwh: batteryKwh, powerKw: null,
  registrationDate: '2022-01-01', deregistrationDate: null,
  status: 'ACTIVE', createdAt: '', updatedAt: '',
  imageUrl: null, imagePublic: false, isPrimary: true,
  batteryDegradationPercent: null, effectiveBatteryCapacityKwh: null,
  isBusinessCar: false, hasHeatPump: false,
  vehicleSpecificationId: specId,
})

describe('resolveCapacityForCar', () => {
  const capacities = [
    cap(62, 'spec-62'),
    cap(77, 'spec-77'),
    cap(85, null),
  ]

  it('matcht per vehicleSpecificationId wenn kWh vom User abweicht (Brutto-Eingabe)', () => {
    // User hat 82 (Brutto) eingetragen, vehicleSpecificationId zeigt auf 77er-Spec
    const result = resolveCapacityForCar(car(82, 'spec-77'), capacities)
    expect(result.selectedCapacity).toBe(77)
    expect(result.useCustom).toBe(false)
    expect(result.customCapacity).toBeNull()
    expect(result.kwhCorrected).toBe(true)
  })

  it('kwhCorrected ist false wenn specId-Match und kWh identisch', () => {
    const result = resolveCapacityForCar(car(62, 'spec-62'), capacities)
    expect(result.selectedCapacity).toBe(62)
    expect(result.kwhCorrected).toBe(false)
  })

  it('matcht per kWh wenn vehicleSpecificationId null ist und kWh passt', () => {
    const result = resolveCapacityForCar(car(62, null), capacities)
    expect(result.selectedCapacity).toBe(62)
    expect(result.useCustom).toBe(false)
    expect(result.kwhCorrected).toBe(false)
  })

  it('matcht per kWh wenn vehicleSpecificationId gesetzt aber kein Treffer in Capacities', () => {
    const result = resolveCapacityForCar(car(85, 'spec-unknown'), capacities)
    expect(result.selectedCapacity).toBe(85)
    expect(result.useCustom).toBe(false)
    expect(result.kwhCorrected).toBe(false)
  })

  it('fällt auf Custom zurück wenn weder specId noch kWh matchen', () => {
    const result = resolveCapacityForCar(car(100, null), capacities)
    expect(result.selectedCapacity).toBeNull()
    expect(result.useCustom).toBe(true)
    expect(result.customCapacity).toBe(100)
    expect(result.kwhCorrected).toBe(false)
  })

  it('bevorzugt specId-Match über kWh-Match und meldet kwhCorrected korrekt', () => {
    // Car hat batteryCapacityKwh=62 UND vehicleSpecificationId=spec-77 -> kWh wechselt von 62 auf 77
    const result = resolveCapacityForCar(car(62, 'spec-77'), capacities)
    expect(result.selectedCapacity).toBe(77)
    expect(result.kwhCorrected).toBe(true)
  })
})

describe('resolveCapacityForCar - selectedSpecId', () => {
  const capacities = [
    cap(62, 'spec-62'),
    cap(77, 'spec-77'),
    cap(85, null),
  ]

  it('gibt specId zurück wenn specId-Match gefunden', () => {
    const result = resolveCapacityForCar(car(77, 'spec-77'), capacities)
    expect(result.selectedSpecId).toBe('spec-77')
  })

  it('gibt specId zurück auch wenn kWh abweicht (Brutto-Eingabe)', () => {
    const result = resolveCapacityForCar(car(82, 'spec-77'), capacities)
    expect(result.selectedSpecId).toBe('spec-77')
  })

  it('gibt null zurück wenn specId nicht in Capacities gefunden (kWh-Fallback)', () => {
    const result = resolveCapacityForCar(car(85, 'spec-unknown'), capacities)
    expect(result.selectedSpecId).toBeNull()
  })

  it('gibt null zurück wenn Car keine specId hat (nur kWh-Match)', () => {
    const result = resolveCapacityForCar(car(62, null), capacities)
    expect(result.selectedSpecId).toBeNull()
  })

  it('gibt null zurück bei Custom-Fallback', () => {
    const result = resolveCapacityForCar(car(100, null), capacities)
    expect(result.selectedSpecId).toBeNull()
  })
})

describe('resolveCapacityForCar - selectedTrimLevel', () => {
  const capacities = [
    cap(75, 'spec-lr', 'Long Range AWD'),
    cap(79, 'spec-perf', 'Performance'),
    cap(57, 'spec-std', null),
  ]

  it('gibt trimLevel der gematchten Spec zurück', () => {
    const result = resolveCapacityForCar(car(75, 'spec-lr'), capacities)
    expect(result.selectedTrimLevel).toBe('Long Range AWD')
  })

  it('gibt null zurück wenn Spec kein trimLevel hat', () => {
    const result = resolveCapacityForCar(car(57, 'spec-std'), capacities)
    expect(result.selectedTrimLevel).toBeNull()
  })

  it('gibt null zurück bei kWh-Fallback ohne specId', () => {
    const result = resolveCapacityForCar(car(75, null), capacities)
    expect(result.selectedTrimLevel).toBeNull()
  })

  it('gibt null zurück bei Custom-Fallback', () => {
    const result = resolveCapacityForCar(car(100, null), capacities)
    expect(result.selectedTrimLevel).toBeNull()
  })
})
