export function convertRow(row: Record<string, unknown>): object | null {
  const dateRaw = row['Start Datum']
  const kwh = row['Geladen (kWh)']

  if (typeof dateRaw !== 'string' || typeof kwh !== 'number') return null

  const date = convertDate(dateRaw)
  if (!date) return null

  const entry: Record<string, unknown> = { date, kwh }

  const rawJson = JSON.stringify(row)
  if (rawJson.length <= 2000) entry.raw_import_data = rawJson

  const odometer = row['Kilometer (km)']
  if (typeof odometer === 'number') entry.odometer_km = Math.round(odometer)

  const socBefore = row['Start Level']
  if (typeof socBefore === 'number') entry.soc_before = Math.round(socBefore)

  const socAfter = row['Ende Level']
  if (typeof socAfter === 'number') entry.soc_after = Math.round(socAfter)

  const cost = row['Kosten (EUR)']
  if (typeof cost === 'number') entry.cost_eur = cost

  const durationRaw = row['Dauer']
  if (typeof durationRaw === 'string') {
    const mins = convertDuration(durationRaw)
    if (mins !== null) entry.duration_min = mins
  }

  const lat = row['Breitengrad']
  const lon = row['Längengrad']
  if (typeof lat === 'number' && typeof lon === 'number') {
    entry.location = `${lat},${lon}`
  }

  const isAc = row['AC']
  if (typeof isAc === 'boolean') entry.charging_type = isAc ? 'AC' : 'DC'

  const maxKw = row['Max (kW)']
  if (typeof maxKw === 'number') entry.max_charging_power_kw = maxKw

  entry.is_public_charging = resolveIsPublicCharging(row['Typ'], isAc, maxKw)

  return entry
}

export function resolveIsPublicCharging(
  typ: unknown,
  isAc: unknown,
  maxKw: unknown,
): boolean | undefined {
  // Primary: explicit "Typ" field from Tronity export
  if (typeof typ === 'string') {
    const normalized = typ.trim().toLowerCase()
    if (normalized === 'öffentlich' || normalized === 'public' || normalized === 'supercharger') return true
    if (normalized === 'privat' || normalized === 'zuhause' || normalized === 'private' || normalized === 'home') return false
  }

  // Fallback: DC or high-power implies public
  const isDc = typeof isAc === 'boolean' && !isAc
  const isHighPower = typeof maxKw === 'number' && maxKw > 22
  if (isDc || isHighPower) return true

  return undefined
}

// "15.03.2026 20:10" → "2026-03-15 20:10"
export function convertDate(raw: string): string | null {
  const m = raw.match(/^(\d{2})\.(\d{2})\.(\d{4})\s+(\d{2}:\d{2})$/)
  if (!m) return null
  return `${m[3]}-${m[2]}-${m[1]} ${m[4]}`
}

// "14:00" → 840, "00:54" → 54
export function convertDuration(raw: string): number | null {
  const m = raw.match(/^(\d+):(\d{2})$/)
  if (!m) return null
  return parseInt(m[1]) * 60 + parseInt(m[2])
}
