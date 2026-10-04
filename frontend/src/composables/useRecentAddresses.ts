import { ref } from 'vue'
import type { PickedPlace } from './useLocationSearch'

const MAX = 3
const key = (userId: string) => `recent-addresses:${userId}`

function read(userId: string): PickedPlace[] {
  try {
    const data = JSON.parse(localStorage.getItem(key(userId)) ?? '[]')
    return Array.isArray(data) ? data.filter(p => typeof p?.name === 'string' && typeof p?.latitude === 'number' && typeof p?.longitude === 'number').slice(0, MAX) : []
  } catch { return [] }
}

/**
 * Die zuletzt gewählten Adressen, damit niemand ohne GPS die Heimadresse jedes Mal neu tippt.
 * Bleibt nur in diesem Browser und je Nutzer getrennt; geht nie an den Server. Ohne Nutzer: nichts merken.
 */
export function useRecentAddresses(userId: string | null | undefined) {
  const list = ref<PickedPlace[]>(userId ? read(userId) : [])

  const remember = (p: PickedPlace) => {
    if (!userId) return
    list.value = [{ latitude: p.latitude, longitude: p.longitude, name: p.name }, ...list.value.filter(x => x.name !== p.name)].slice(0, MAX)
    try { localStorage.setItem(key(userId), JSON.stringify(list.value)) } catch { /* Speicher gesperrt: nur für diese Sitzung */ }
  }

  return { list, remember }
}
