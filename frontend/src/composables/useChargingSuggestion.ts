import { ref } from 'vue'
import api from '../api/axios'
import type { ChargingSuggestion } from '../components/log-wizard/wizardLogic'

/**
 * "Du stehst an einem Ort, an dem du schon geladen hast" - die Schnittmenge aus Position und
 * eigenen Logs, berechnet im Backend. 204 heißt kein Treffer, ein Fehler ebenso: der Wizard
 * zeigt dann die Liste wie bisher.
 */
export function useChargingSuggestion() {
  const suggestion = ref<ChargingSuggestion | null>(null)
  const loading = ref(false)
  let seq = 0

  const load = async (lat: number, lon: number) => {
    const mine = ++seq
    loading.value = true
    try {
      const res = await api.get('/charging-sites/suggestion', { params: { lat, lon } })
      if (mine !== seq) return
      suggestion.value = res.status === 200 && res.data?.kind ? res.data : null
    } catch {
      if (mine === seq) suggestion.value = null
    } finally {
      if (mine === seq) loading.value = false
    }
  }
  const clear = () => { seq++; suggestion.value = null; loading.value = false }

  return { suggestion, loading, load, clear }
}
