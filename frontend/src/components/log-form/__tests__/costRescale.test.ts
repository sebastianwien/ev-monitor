import { describe, it, expect } from 'vitest'
import { rescaleTotalToNewEnergy } from '../costRescale'

/**
 * Wenn sich die Energiemenge eines Logs aendert, bleibt der ct/kWh-Preis die feste Groesse und
 * der Gesamtbetrag waechst mit. Grund: der Ladepunkt rechnet brutto ab. Wer nachtraeglich die
 * Bruttomenge ergaenzt, sah bisher denselben Betrag auf mehr kWh verteilt - der ct/kWh-Preis
 * sank stillschweigend, obwohl der Tarif unveraendert galt.
 */
describe('rescaleTotalToNewEnergy', () => {
  it('haelt den ct/kWh-Preis konstant, wenn Brutto nachgetragen wird', () => {
    // 25.27 EUR auf 57.44 kWh = 0.44 EUR/kWh, danach 62.26 kWh brutto
    expect(rescaleTotalToNewEnergy(25.27, 57.44, 62.26)).toBe(27.39)
  })

  it('rechnet auch nach unten, wenn die Menge korrigiert wird', () => {
    expect(rescaleTotalToNewEnergy(27.39, 62.26, 57.44)).toBe(25.27)
  })

  it('laesst den Betrag unveraendert, wenn die Menge gleich bleibt', () => {
    expect(rescaleTotalToNewEnergy(25.27, 57.44, 57.44)).toBe(25.27)
  })

  it('gibt null zurueck, wenn es nichts zu skalieren gibt', () => {
    expect(rescaleTotalToNewEnergy(null, 57.44, 62.26)).toBeNull()
  })

  it('laesst den Betrag stehen, wenn die alte Menge unbekannt oder null war', () => {
    // Ohne vorherige Bezugsmenge ist kein ct/kWh-Preis ableitbar - der Betrag bleibt, wie er ist.
    expect(rescaleTotalToNewEnergy(25.27, null, 62.26)).toBe(25.27)
    expect(rescaleTotalToNewEnergy(25.27, 0, 62.26)).toBe(25.27)
  })

  it('laesst den Betrag stehen, wenn die neue Menge geleert wird', () => {
    expect(rescaleTotalToNewEnergy(25.27, 57.44, null)).toBe(25.27)
  })
})
