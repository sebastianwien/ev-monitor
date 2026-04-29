const pad = (n: number) => String(n).padStart(2, '0')

// Converts a datetime-local input value (local time, e.g. "2026-04-29T22:14") to a UTC string
// for backend storage ("2026-04-29T20:14:00" in CEST = UTC+2).
// Uses the Date(year, month, day, h, m) constructor which always treats inputs as local time.
export function datetimeLocalToUtcIso(value: string): string {
  const [datePart, timePart] = value.split('T')
  const [year, month, day] = datePart.split('-').map(Number)
  const [hours, minutes] = timePart.split(':').map(Number)
  const d = new Date(year, month - 1, day, hours, minutes, 0, 0)
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:00`
}
