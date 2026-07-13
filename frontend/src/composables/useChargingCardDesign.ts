/**
 * Optik einer Ladekarte: aus der Karten-ID deterministisch abgeleitet, damit dieselbe
 * Karte im Log-Formular und in der Ladekarten-Verwaltung identisch aussieht und der
 * User sie wiedererkennt, ohne den Namen zu lesen.
 */
export type CardDesign = 'stripe' | 'circles' | 'solid' | 'pastel'

const CARD_DESIGNS: CardDesign[] = ['stripe', 'circles', 'solid', 'pastel']

function hashId(id: string): number {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  return h
}

export function cardDesign(id: string): CardDesign {
  return CARD_DESIGNS[hashId(id) % CARD_DESIGNS.length]
}

// Kraeftige Volltoene mit leichtem Glanz von oben
const SOLID_COLORS = [
  { bg: 'linear-gradient(180deg, #4f46e5 0%, #3730a3 100%)', shadow: '#312e81' }, // indigo
  { bg: 'linear-gradient(180deg, #0f766e 0%, #0d5e57 100%)', shadow: '#134e4a' }, // teal
  { bg: 'linear-gradient(180deg, #be123c 0%, #9f1239 100%)', shadow: '#881337' }, // rose
  { bg: 'linear-gradient(180deg, #b45309 0%, #92400e 100%)', shadow: '#78350f' }, // amber
  { bg: 'linear-gradient(180deg, #1d4ed8 0%, #1e40af 100%)', shadow: '#1e3a8a' }, // blue
  { bg: 'linear-gradient(180deg, #7e22ce 0%, #6b21a8 100%)', shadow: '#581c87' }, // purple
]

function solidColor(id: string) {
  return SOLID_COLORS[(hashId(id) >>> 3) % SOLID_COLORS.length]
}

export function cardContainerStyle(id: string): Record<string, string> {
  const d = cardDesign(id)
  if (d === 'stripe')  return { background: '#f1f5f9', '--btn-shadow-color': '#94a3b8' }
  if (d === 'circles') return { background: '#1e1b4b', '--btn-shadow-color': '#0c0a2e' }
  if (d === 'solid')   return { background: solidColor(id).bg, '--btn-shadow-color': solidColor(id).shadow }
  /* pastel */         return { background: 'linear-gradient(135deg, #fde68a 0%, #fbcfe8 100%)', '--btn-shadow-color': '#d97706' }
}

export function cardChipStyle(id: string): Record<string, string> {
  const d = cardDesign(id)
  if (d === 'stripe')  return { background: 'rgba(251,191,36,0.9)', border: '1px solid rgba(180,83,9,0.3)' }
  if (d === 'solid' || d === 'circles') return { background: 'rgba(253,224,71,0.75)', border: '1px solid rgba(253,224,71,0.4)' }
  /* pastel */         return { background: 'rgba(180,83,9,0.35)', border: '1px solid rgba(180,83,9,0.2)' }
}

export function cardTextColor(id: string): string {
  const d = cardDesign(id)
  if (d === 'stripe')  return '#1f2937'
  if (d === 'solid' || d === 'circles') return 'rgba(255,255,255,0.95)'
  /* pastel */         return '#78350f'
}

export function cardSubTextColor(id: string): string {
  const d = cardDesign(id)
  if (d === 'stripe')  return '#6b7280'
  if (d === 'solid' || d === 'circles') return 'rgba(255,255,255,0.55)'
  /* pastel */         return 'rgba(120,53,15,0.6)'
}
