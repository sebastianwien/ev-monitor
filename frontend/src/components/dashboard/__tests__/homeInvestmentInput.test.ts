import { describe, it, expect } from 'vitest'
import { parseInvestmentInput, MAX_INVESTMENT } from '../homeInvestmentInput'

/**
 * v-model auf <input type="number"> liefert eine Zahl, sobald der Wert gueltig ist, und
 * einen String, solange er es nicht ist (leer, "1e", "-"). Die Pruefung muss deshalb mit
 * beidem umgehen - vorher lief sie in "input.value.trim is not a function".
 */
describe('parseInvestmentInput', () => {
  it('nimmt eine Zahl entgegen, wie v-model sie liefert', () => {
    expect(parseInvestmentInput(1400)).toEqual({ valid: true, value: 1400 })
  })

  it('nimmt einen String entgegen', () => {
    expect(parseInvestmentInput('1400')).toEqual({ valid: true, value: 1400 })
  })

  it('leer loescht den Wert', () => {
    expect(parseInvestmentInput('')).toEqual({ valid: true, value: null })
    expect(parseInvestmentInput('   ')).toEqual({ valid: true, value: null })
    expect(parseInvestmentInput(null)).toEqual({ valid: true, value: null })
    expect(parseInvestmentInput(undefined)).toEqual({ valid: true, value: null })
  })

  it('weist negative Betraege ab', () => {
    expect(parseInvestmentInput(-1).valid).toBe(false)
  })

  it('weist Betraege oberhalb der Obergrenze ab', () => {
    expect(parseInvestmentInput(MAX_INVESTMENT + 1).valid).toBe(false)
    expect(parseInvestmentInput(MAX_INVESTMENT).valid).toBe(true)
  })

  /** Bei ungueltiger Eingabe haelt der Browser den Rohstring fest. */
  it('weist unvollstaendige Eingaben ab', () => {
    expect(parseInvestmentInput('1e').valid).toBe(false)
    expect(parseInvestmentInput('-').valid).toBe(false)
    expect(parseInvestmentInput('abc').valid).toBe(false)
  })

  it('vertraegt Nachkommastellen', () => {
    expect(parseInvestmentInput('1399.99')).toEqual({ valid: true, value: 1399.99 })
  })

  /** Null ist ein zulaessiger Betrag - eine geschenkte Wallbox amortisiert sofort. */
  it('erlaubt null Euro', () => {
    expect(parseInvestmentInput(0)).toEqual({ valid: true, value: 0 })
  })
})
