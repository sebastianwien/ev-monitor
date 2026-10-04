import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useLocationSearch, nominatimSearchUrl, shortAddress } from '../useLocationSearch'

describe('useLocationSearch', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue({ json: async () => [{ place_id: 1, lat: '52.5', lon: '13.4', display_name: 'Berlin' }] }) as any
  })

  /** Nominatim verbietet Autocomplete: genau ein Aufruf je ausdrücklicher Suche, kein Watcher. */
  it('search ruft Nominatim genau einmal auf, ohne Länderfilter', async () => {
    const s = useLocationSearch()
    s.query.value = 'Ber'
    s.query.value = 'Berlin'
    expect(fetch).not.toHaveBeenCalled()
    await s.search('Berlin')
    expect(fetch).toHaveBeenCalledTimes(1)
    const url = String(vi.mocked(fetch).mock.calls[0][0])
    expect(url).toContain('q=Berlin')
    expect(url).not.toContain('countrycodes')
    expect(s.suggestions.value).toHaveLength(1)
    expect(s.noResults.value).toBe(false)
  })

  it('meldet "kein Treffer" nach einer leeren Antwort', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ json: async () => [] } as any)
    const s = useLocationSearch()
    await s.search('EnBW Lichtenau')
    expect(s.suggestions.value).toEqual([])
    expect(s.noResults.value).toBe(true)
  })

  it('select liefert Koordinaten als Zahlen und übernimmt den Namen', () => {
    const s = useLocationSearch()
    const picked = s.select({ place_id: 1, lat: '52.5', lon: '13.4', display_name: 'Berlin' })
    expect(picked).toEqual({ latitude: 52.5, longitude: 13.4, name: 'Berlin' })
    expect(s.query.value).toBe('Berlin')
    expect(s.selectedName.value).toBe('Berlin')
    expect(s.suggestions.value).toEqual([])
  })

  it('ein Netzfehler leert die Vorschläge statt zu werfen', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('offline'))
    const s = useLocationSearch()
    await s.search('Rasthof')
    expect(s.suggestions.value).toEqual([])
    expect(s.noResults.value).toBe(false)
  })

  it('URL-Builder ist für alle Aufrufstellen derselbe', () => {
    expect(nominatimSearchUrl('a b')).toBe('https://nominatim.openstreetmap.org/search?q=a%20b&format=json&addressdetails=1&limit=5')
  })

  /** Der Name ist nur Beschriftung (Untertitel, Suchfeld, letzte Adressen) - der Ort sind die Koordinaten */
  describe('shortAddress', () => {
    const base = { place_id: 1, lat: '52.5', lon: '13.4' }
    it('Straße und Hausnummer, dazu der Ort', () => {
      expect(shortAddress({ ...base, display_name: '140, Storkower Straße, Prenzlauer Berg, Pankow, Berlin, 10407, Deutschland',
        address: { house_number: '140', road: 'Storkower Straße', suburb: 'Prenzlauer Berg', city: 'Berlin', postcode: '10407', country: 'Deutschland' } }))
        .toBe('Storkower Straße 140, Berlin')
    })
    it('Kleinstadt und Dorf statt Stadt', () => {
      expect(shortAddress({ ...base, display_name: 'x', address: { road: 'Linzer Straße', town: 'Freistadt' } })).toBe('Linzer Straße, Freistadt')
      expect(shortAddress({ ...base, display_name: 'x', address: { road: 'Dorfstraße', village: 'Lichtenau' } })).toBe('Dorfstraße, Lichtenau')
    })
    it('ohne Straße: die ersten zwei Teile des langen Namens', () => {
      expect(shortAddress({ ...base, display_name: 'Lichtenau, Landkreis Ansbach, Bayern, Deutschland' })).toBe('Lichtenau, Landkreis Ansbach')
    })
    it('select übernimmt den kurzen Namen', () => {
      const s = useLocationSearch()
      const picked = s.select({ ...base, display_name: 'lang, lang, lang', address: { road: 'Storkower Straße', house_number: '140', city: 'Berlin' } })
      expect(picked.name).toBe('Storkower Straße 140, Berlin')
      expect(s.query.value).toBe('Storkower Straße 140, Berlin')
    })
  })

  it('fragt die Adressteile mit ab', () => {
    expect(nominatimSearchUrl('Berlin')).toContain('addressdetails=1')
  })
})
