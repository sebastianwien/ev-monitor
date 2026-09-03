import { describe, it, expect } from 'vitest'
import { locationSegments } from '../chargingSplitSegments'

/**
 * Seit V166 kennt der Ladeort drei Zustaende. Logs ohne Angabe - vor allem aus der
 * Telemetrie, die fuer AC bewusst nichts meldet - duerfen nicht still bei den
 * Heimladungen landen, sonst zeigt die Kachel eine zu hohe Heimquote.
 */
describe('locationSegments', () => {
  const labels = { public: 'Öffentlich', private: 'Zuhause', unknown: 'Ohne Angabe' }

  it('rechnet Prozente ueber alle drei Toepfe', () => {
    const segs = locationSegments({ publicKwh: 25, privateKwh: 50, unknownKwh: 25 }, labels)

    expect(segs.map(s => s.pct)).toEqual([25, 50, 25])
  })

  it('blendet den Unbekannt-Topf aus, wenn er leer ist', () => {
    const segs = locationSegments({ publicKwh: 30, privateKwh: 70, unknownKwh: 0 }, labels)

    expect(segs).toHaveLength(2)
    expect(segs.map(s => s.label)).toEqual([labels.public, labels.private])
  })

  it('verwaessert die Heimquote nicht mehr', () => {
    // Ohne den dritten Topf waeren 50 von 75 kWh = 67 % Heimanteil gemeldet worden.
    const segs = locationSegments({ publicKwh: 25, privateKwh: 50, unknownKwh: 25 }, labels)

    expect(segs.find(s => s.label === labels.private)?.pct).toBe(50)
  })

  it('liefert nichts bei leerer Datenlage', () => {
    expect(locationSegments({ publicKwh: 0, privateKwh: 0, unknownKwh: 0 }, labels)).toEqual([])
  })

  it('vertraegt fehlendes unknownKwh aus aelteren Antworten', () => {
    const segs = locationSegments({ publicKwh: 30, privateKwh: 70 } as never, labels)

    expect(segs).toHaveLength(2)
    expect(segs.map(s => s.pct)).toEqual([30, 70])
  })
})
