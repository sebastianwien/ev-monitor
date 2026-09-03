import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

vi.mock('../../api/axios', () => ({
  default: { get: vi.fn() },
}))
import api from '../../api/axios'
import { useCpoOptions } from '../useCpoOptions'

const mockGet = (byUrl: Record<string, unknown>) => {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (!(url in byUrl)) return Promise.reject(new Error(`unerwartete URL ${url}`))
    const value = byUrl[url]
    if (value instanceof Error) return Promise.reject(value)
    return Promise.resolve({ data: value } as never)
  })
}

describe('useCpoOptions', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
  })

  it('laedt die vollstaendige Anbieterliste des Landes', async () => {
    mockGet({ '/charging-provider-tariffs/cpos': ['Allego', 'EnBW', 'IONITY'] })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadAll()

    expect(api.get).toHaveBeenCalledWith('/charging-provider-tariffs/cpos', { params: { country: 'DE' } })
    expect(cpo.allCpos.value).toEqual(['Allego', 'EnBW', 'IONITY'])
  })

  it('holt die Vorschlaege im Umkreis fuer eine Position', async () => {
    mockGet({ '/charging-provider-tariffs/cpos/nearby': ['EnBW', 'Allego'] })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadNearby(52.52, 13.4)

    expect(api.get).toHaveBeenCalledWith('/charging-provider-tariffs/cpos/nearby',
      { params: { lat: 52.52, lon: 13.4 } })
    expect(cpo.nearbyCpos.value).toEqual(['EnBW', 'Allego'])
  })

  it('trennt Vorschlaege und Rest ohne Doppelte', async () => {
    mockGet({
      '/charging-provider-tariffs/cpos': ['Allego', 'EnBW', 'IONITY', 'Mer'],
      '/charging-provider-tariffs/cpos/nearby': ['EnBW', 'Allego'],
    })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadAll()
    await cpo.loadNearby(52.52, 13.4)

    expect(cpo.nearbyCpos.value).toEqual(['EnBW', 'Allego'])
    expect(cpo.otherCpos.value).toEqual(['IONITY', 'Mer'])
  })

  /** Ohne Vorschlag bleibt die vollstaendige Liste die einzige Gruppe. */
  it('ohne Umkreistreffer stehen alle Anbieter in der Restgruppe', async () => {
    mockGet({
      '/charging-provider-tariffs/cpos': ['Allego', 'EnBW'],
      '/charging-provider-tariffs/cpos/nearby': [],
    })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadAll()
    await cpo.loadNearby(52.52, 13.4)

    expect(cpo.nearbyCpos.value).toEqual([])
    expect(cpo.otherCpos.value).toEqual(['Allego', 'EnBW'])
  })

  it('ein Fehler beim Umkreis laesst die vollstaendige Liste unberuehrt', async () => {
    mockGet({
      '/charging-provider-tariffs/cpos': ['Allego', 'EnBW'],
      '/charging-provider-tariffs/cpos/nearby': new Error('429'),
    })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadAll()
    await cpo.loadNearby(52.52, 13.4)

    expect(cpo.nearbyCpos.value).toEqual([])
    expect(cpo.otherCpos.value).toEqual(['Allego', 'EnBW'])
  })

  it('ein Fehler bei der Gesamtliste blendet das Feld aus statt zu blockieren', async () => {
    mockGet({ '/charging-provider-tariffs/cpos': new Error('offline') })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadAll()

    expect(cpo.allCpos.value).toEqual([])
    expect(cpo.hasOptions.value).toBe(false)
  })

  it('fragt ohne Position gar nicht erst an', async () => {
    mockGet({})
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadNearby(null, null)

    expect(api.get).not.toHaveBeenCalled()
    expect(cpo.nearbyCpos.value).toEqual([])
  })

  /** Ein Bestandslog kann einen Anbieter tragen, den die Landesliste nicht mehr fuehrt. */
  it('behaelt den bereits gespeicherten Anbieter in der Auswahl', async () => {
    mockGet({ '/charging-provider-tariffs/cpos': ['Allego', 'EnBW'] })
    const cpo = useCpoOptions(ref('DE'))

    await cpo.loadAll()
    cpo.keepSelected('Altes Netz')

    expect(cpo.otherCpos.value).toContain('Altes Netz')
  })
})
