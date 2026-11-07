import { describe, it, expect } from 'vitest'
import { placeLabel, paymentLabel } from '../stationText'

describe('placeLabel', () => {
  it('Name und Adresse, ohne Land', () => {
    expect(placeLabel('Hauptbahnhof, Ernst-August-Platz 1, 30159 Hannover, Deutschland', 'Deutschland'))
      .toBe('Hauptbahnhof, Ernst-August-Platz 1, 30159 Hannover')
  })

  it('ohne Land bleibt der Ort', () => {
    expect(placeLabel('Kröpcke, 30159 Hannover')).toBe('Kröpcke, 30159 Hannover')
    expect(placeLabel('Kröpcke, 30159 Hannover', 'Deutschland')).toBe('Kröpcke, 30159 Hannover')
  })
})

describe('paymentLabel', () => {
  it('Semikolon-Liste aus dem Register als Aufzählung, ohne Leere und Doppelte', () => {
    expect(paymentLabel('RFID-Karte;Kreditkarte (NFC);;RFID-Karte;')).toBe('RFID-Karte, Kreditkarte (NFC)')
    expect(paymentLabel(';')).toBeNull()
    expect(paymentLabel(null)).toBeNull()
  })
})
