import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useLocationSearch, nominatimSearchUrl, photonSearchUrl, fromPhoton, shortAddress } from '../useLocationSearch'

const feature = (props: Record<string, unknown>, lon = 13.4516, lat = 52.5342) =>
  ({ type: 'Feature', geometry: { type: 'Point', coordinates: [lon, lat] as [number, number] }, properties: { osm_id: 7, osm_type: 'N', ...props } })
const photon = (...features: unknown[]) => ({ ok: true, json: async () => ({ type: 'FeatureCollection', features }) })

describe('useLocationSearch (Photon)', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue(photon(feature({ name: 'Berlin', city: 'Berlin', country: 'Deutschland', type: 'city' }))) as any
  })
  afterEach(() => { vi.useRealTimers() })

  it('search fragt Photon einmal ab und liefert Vorschläge im gewohnten Format', async () => {
    const s = useLocationSearch()
    await s.search('Berlin')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(String(vi.mocked(fetch).mock.calls[0][0])).toMatch(/^https:\/\/photon\.komoot\.io\/api\/\?q=Berlin/)
    expect(s.suggestions.value).toHaveLength(1)
    expect(s.suggestions.value[0]).toMatchObject({ lat: '52.5342', lon: '13.4516' })
    expect(s.noResults.value).toBe(false)
  })

  it('beim Tippen: eine Anfrage nach der Pause, nicht je Zeichen, erst ab drei Zeichen', async () => {
    vi.useFakeTimers()
    const s = useLocationSearch()
    s.autocomplete('Li')
    await vi.advanceTimersByTimeAsync(1000)
    expect(fetch).not.toHaveBeenCalled()
    s.autocomplete('Lin'); s.autocomplete('Lind'); s.autocomplete('Linde')
    await vi.advanceTimersByTimeAsync(200)
    expect(fetch).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(300)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(String(vi.mocked(fetch).mock.calls[0][0])).toContain('q=Linde')
  })

  it('eine überholte Antwort überschreibt die neuere nicht', async () => {
    let first: (v: unknown) => void = () => {}
    vi.mocked(fetch)
      .mockImplementationOnce(() => new Promise(r => { first = r }) as any)
      .mockResolvedValueOnce(photon(feature({ name: 'Neu', type: 'city' })) as any)
    const s = useLocationSearch()
    const a = s.search('Alt')
    await s.search('Neu')
    first(photon(feature({ name: 'Alt', type: 'city' })))
    await a
    expect(s.suggestions.value.map(x => x.display_name)).toEqual(['Neu'])
  })

  it('bevorzugt Treffer nahe der eigenen Position, schickt sie aber nur auf rund 10 km gerundet an komoot', () => {
    expect(photonSearchUrl('Hauptstraße 1', { lat: 52.5342, lon: 13.4716 })).toContain('&lat=52.5&lon=13.5')
    expect(photonSearchUrl('a b')).toBe('https://photon.komoot.io/api/?q=a%20b&limit=5')
  })

  it('meldet "kein Treffer" nach einer leeren Antwort', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(photon() as any)
    const s = useLocationSearch()
    await s.search('EnBW Lichtenau')
    expect(s.suggestions.value).toEqual([])
    expect(s.noResults.value).toBe(true)
  })

  it('Drossel oder Serverfehler ist kein "kein Treffer"', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ ok: false, status: 429, json: async () => ({ message: 'rate limit' }) } as any)
    const s = useLocationSearch()
    await s.search('Lindenweg 3')
    expect(s.suggestions.value).toEqual([])
    expect(s.noResults.value).toBe(false)
  })

  it('pending gilt von der ersten Taste bis zur Antwort - so lange ist der Treffer veraltet', async () => {
    vi.useFakeTimers()
    const s = useLocationSearch()
    expect(s.pending.value).toBe(false)
    s.autocomplete('Lindenweg 3')
    expect(s.pending.value).toBe(true)
    await vi.advanceTimersByTimeAsync(500)
    expect(s.pending.value).toBe(false)
    s.autocomplete('Li')
    expect(s.pending.value).toBe(false)
  })

  it('ein Netzfehler leert die Vorschläge statt zu werfen', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new Error('offline'))
    const s = useLocationSearch()
    await s.search('Rasthof')
    expect(s.suggestions.value).toEqual([])
    expect(s.noResults.value).toBe(false)
  })

  describe('fromPhoton', () => {
    it('Hausadresse: Straße und Hausnummer, Ort, kurzer Name wie bei Nominatim', () => {
      const s = fromPhoton(feature({ type: 'house', street: 'Storkower Straße', housenumber: '140', postcode: '10407', city: 'Berlin', country: 'Deutschland' }))
      expect(shortAddress(s)).toBe('Storkower Straße 140, Berlin')
      expect(s.display_name).toBe('Storkower Straße 140, 10407 Berlin, Deutschland')
    })
    it('Straße ohne Hausnummer: der Name ist die Straße', () => {
      expect(shortAddress(fromPhoton(feature({ type: 'street', name: 'Lindenweg', city: 'Potsdam' })))).toBe('Lindenweg, Potsdam')
    })
    it('Ort mit Namen (Hotel): Name vorn im langen Namen', () => {
      const s = fromPhoton(feature({ type: 'house', name: 'Hotel Adler', street: 'Marktplatz', housenumber: '1', city: 'Freistadt' }))
      expect(s.display_name).toBe('Hotel Adler, Marktplatz 1, Freistadt')
    })
    it('Knoten, Weg und Relation mit gleicher OSM-Nummer bekommen verschiedene IDs', () => {
      const ids = ['N', 'W', 'R'].map(t => fromPhoton(feature({ osm_type: t, name: 'x' })).place_id)
      expect(new Set(ids).size).toBe(3)
    })
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

  it('Nominatim-URL bleibt für die Stellen mit ausdrücklicher Suche (altes Formular, Preis-Nachtrag, Sprache)', () => {
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
