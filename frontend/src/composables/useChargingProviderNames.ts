import { ref, onMounted } from 'vue'
import api from '../api/axios'

/**
 * Namen der eigenen Ladekarten je id, einmal beim Mounten geladen. Log-Feed und Logbuch zeigen damit an
 * einer Ladung die Karte, mit der bezahlt wurde. Ohne Antwort bleibt die Map leer, die Zeile zeigt dann nur den Ort.
 */
export function useChargingProviderNames() {
  const names = ref<Map<string, string>>(new Map())
  onMounted(async () => {
    try {
      const res = await api.get<{ id: string; providerName: string; label: string | null }[]>('/users/me/charging-providers')
      names.value = new Map(res.data.map((p) => [p.id, p.label || p.providerName]))
    } catch { /* ohne Kartennamen */ }
  })
  return names
}
