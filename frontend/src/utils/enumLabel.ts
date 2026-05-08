export function enumToLabel(value: string | undefined | null): string {
  return (value ?? '').replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map((w: string) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

/**
 * Combines brand and model into a display name, avoiding duplication.
 * Some brands include the brand name in the model (e.g. "Polestar 2"),
 * so "Polestar" + "Polestar 2" would produce "Polestar Polestar 2" without this check.
 */
export function carDisplayName(brand: string | undefined | null, model: string | undefined | null): string {
  const brandLabel = enumToLabel(brand)
  const modelLabel = enumToLabel(model)
  if (modelLabel.toLowerCase().startsWith(brandLabel.toLowerCase())) return modelLabel
  return `${brandLabel} ${modelLabel}`.trim()
}
