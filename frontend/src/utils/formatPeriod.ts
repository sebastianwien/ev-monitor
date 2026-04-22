export function formatPeriod(
  availableFrom: string | null,
  availableTo: string | null,
  sinceLabel = 'seit',
): string | null {
  if (!availableFrom) return null
  const fmt = (d: string) => {
    const [year, month] = d.split('-').map(Number)
    return `${String(month).padStart(2, '0')}/${String(year).slice(2)}`
  }
  if (!availableTo) return `${sinceLabel} ${fmt(availableFrom)}`
  return `${fmt(availableFrom)}–${fmt(availableTo)}`
}
