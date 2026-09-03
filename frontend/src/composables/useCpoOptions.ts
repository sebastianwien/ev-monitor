import { ref, computed, type Ref } from 'vue'
import api from '../api/axios'

/**
 * Auswahl des Ladenetzes (CPO) fuer eine oeffentliche Ladung.
 *
 * Zwei Quellen: die Anbieter des Landes als vollstaendige Liste, und die Anbieter,
 * die laut Ladesaeulenregister am Ort der Ladung wirklich stehen. Die Vorschlaege
 * stehen im Select oben, der Rest darunter.
 *
 * Bewusst kein Freitextfeld: ein beliebiger String laesst sich spaeter keinem Tarif
 * zuordnen und hat frueher Werte wie "Zuhause" in das Feld getragen.
 *
 * Jeder Fehler ist still. Ohne Vorschlaege bleibt die vollstaendige Liste, ohne
 * Gesamtliste verschwindet das Feld - eine Ladung darf daran nie scheitern.
 */
export function useCpoOptions(country: Ref<string>) {
  const allCpos = ref<string[]>([])
  const nearbyCpos = ref<string[]>([])

  /** Alles aus der Gesamtliste, was nicht schon als Vorschlag oben steht. */
  const otherCpos = computed(() =>
    allCpos.value.filter((cpo) => !nearbyCpos.value.includes(cpo)))

  const hasOptions = computed(() => allCpos.value.length > 0 || nearbyCpos.value.length > 0)

  const loadAll = async () => {
    try {
      const res = await api.get('/charging-provider-tariffs/cpos', { params: { country: country.value } })
      allCpos.value = Array.isArray(res.data) ? res.data : []
    } catch {
      allCpos.value = []
    }
  }

  const loadNearby = async (lat: number | null, lon: number | null) => {
    if (lat == null || lon == null) {
      nearbyCpos.value = []
      return
    }
    try {
      const res = await api.get('/charging-provider-tariffs/cpos/nearby', { params: { lat, lon } })
      nearbyCpos.value = Array.isArray(res.data) ? res.data : []
    } catch {
      nearbyCpos.value = []
    }
  }

  /**
   * Haelt einen bereits gespeicherten Anbieter in der Auswahl, auch wenn die Landesliste
   * ihn nicht mehr fuehrt. Sonst wuerde das Bearbeiten eines Bestandslogs den Wert stillschweigend
   * verwerfen.
   */
  const keepSelected = (cpoName: string | null) => {
    if (!cpoName) return
    if (!allCpos.value.includes(cpoName) && !nearbyCpos.value.includes(cpoName)) {
      allCpos.value = [...allCpos.value, cpoName]
    }
  }

  return { allCpos, nearbyCpos, otherCpos, hasOptions, loadAll, loadNearby, keepSelected }
}
