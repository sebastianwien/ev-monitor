import { describe, it, expect } from 'vitest'
import { rescaleTotalToNewEnergy, rescaleCostForKwhChange } from '../costRescale'

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

/**
 * Der Log-Feed erlaubt es, eine fehlende Menge direkt in der Kachel nachzutragen. Dabei wechselt
 * die Bezugsmenge des gespeicherten Betrags (costBasisKwh: brutto vor netto) - ohne Mitskalieren
 * sank der angezeigte ct/kWh-Preis stillschweigend um die Ladeverluste.
 */
describe('rescaleCostForKwhChange', () => {
  const nettoOnly = { costEur: 5.23, kwhCharged: null, kwhAtVehicle: 11.88 }

  it('zieht den Betrag mit, wenn Brutto zu einem Netto-Log nachgetragen wird', () => {
    // 5.23 EUR auf 11.88 kWh = 0.44 EUR/kWh, brutto 13.65 kWh -> 6.01 EUR (Preis bleibt 0.44)
    expect(rescaleCostForKwhChange(nettoOnly, 'kwhCharged', 13.65)).toBe(6.01)
  })

  it('laesst den Betrag unveraendert, wenn Netto zu einem Brutto-Log nachgetragen wird', () => {
    // Abgerechnet wurde brutto - die Netto-Angabe aendert die Bezugsmenge nicht.
    const bruttoOnly = { costEur: 6.01, kwhCharged: 13.65, kwhAtVehicle: null }
    expect(rescaleCostForKwhChange(bruttoOnly, 'kwhAtVehicle', 11.88)).toBe(6.01)
  })

  it('korrigiert den Betrag, wenn ein bestehender Brutto-Wert geaendert wird', () => {
    const both = { costEur: 6.01, kwhCharged: 13.65, kwhAtVehicle: 11.88 }
    expect(rescaleCostForKwhChange(both, 'kwhCharged', 27.3)).toBe(12.02)
  })

  it('gibt null zurueck, wenn das Log keine Kosten hat', () => {
    expect(rescaleCostForKwhChange({ costEur: null, kwhCharged: null, kwhAtVehicle: 11.88 }, 'kwhCharged', 13.65)).toBeNull()
  })

  it('laesst den Betrag stehen, wenn vorher keine Bezugsmenge existierte', () => {
    expect(rescaleCostForKwhChange({ costEur: 5.23, kwhCharged: null, kwhAtVehicle: null }, 'kwhCharged', 13.65)).toBe(5.23)
  })
})
