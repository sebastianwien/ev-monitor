import { describe, it, expect } from 'vitest'
import { autoSyncTileState } from '../useAutoSyncTiles'

const id7 = { id: 'car-id7', brand: 'VW' }
const id3 = { id: 'car-id3', brand: 'VW' }
const xpeng = { id: 'car-xp', brand: 'XPENG' }

describe('autoSyncTileState (AutoSync-Slots: ein Abo je Fahrzeug)', () => {
  it('ein Auto, ein Abo, nichts verbunden: verfügbar', () => {
    expect(autoSyncTileState(id7, [], 1)).toBe('available')
  })

  it('zwei Autos, ein Abo, erstes verbunden: zweites gesperrt', () => {
    expect(autoSyncTileState(id7, ['car-id7'], 1)).toBe('active')
    expect(autoSyncTileState(id3, ['car-id7'], 1)).toBe('locked')
  })

  it('zwei Autos, zwei Abos: zweites verfügbar, beide verbunden aktiv', () => {
    expect(autoSyncTileState(id3, ['car-id7'], 2)).toBe('available')
    expect(autoSyncTileState(id3, ['car-id7', 'car-id3'], 2)).toBe('active')
  })

  it('verbundenes Auto bleibt aktiv, auch wenn Plätze überzogen sind', () => {
    expect(autoSyncTileState(id3, ['car-id7', 'car-id3'], 1)).toBe('active')
  })

  it('Slots unter 1 zählen als 1', () => {
    expect(autoSyncTileState(id3, ['car-id7'], 0)).toBe('locked')
  })

  it('Marke ohne AutoSync-Weg: nicht verfügbar, unabhängig von Slots', () => {
    expect(autoSyncTileState(xpeng, [], 3)).toBe('unavailable')
  })
})
