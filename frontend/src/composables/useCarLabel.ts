import type { Car } from '@/api/carService'

function enumToLabel(value: string | null | undefined): string {
  if (!value) return ''
  return value
    .replace(/_/g, ' ')
    .toLowerCase()
    .split(' ')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

export function carDisplayName(brand: string, model: string): string {
  const b = enumToLabel(brand)
  const m = enumToLabel(model)
  return m.toLowerCase().startsWith(b.toLowerCase()) ? m : `${b} ${m}`.trim()
}

export function carLabel(car: Pick<Car, 'brand' | 'model' | 'trim' | 'licensePlate'>): string {
  const base = carDisplayName(car.brand, car.model)
  const name = car.trim ? `${base} ${car.trim}` : base
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}
