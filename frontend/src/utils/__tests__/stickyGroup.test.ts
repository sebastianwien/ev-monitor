import { describe, it, expect } from 'vitest'
import { isGroupScrolledPast, collapseAnchorScrollY } from '../stickyGroup'

describe('isGroupScrolledPast', () => {
  it('ist false, solange der Gruppenanfang unter der Klebelinie liegt', () => {
    expect(isGroupScrolledPast(300, 52)).toBe(false)
  })

  it('ist false, wenn der Gruppenanfang genau auf der Klebelinie steht', () => {
    expect(isGroupScrolledPast(52, 52)).toBe(false)
    expect(isGroupScrolledPast(51.7, 52)).toBe(false)
  })

  it('ist true, sobald der Gruppenanfang ueber die Klebelinie gescrollt ist', () => {
    expect(isGroupScrolledPast(-400, 52)).toBe(true)
    expect(isGroupScrolledPast(50, 52)).toBe(true)
  })
})

describe('collapseAnchorScrollY', () => {
  it('liefert null, wenn der Kopf nicht klebt - Zuklappen verschiebt dann nichts', () => {
    expect(collapseAnchorScrollY(1200, 300, 52)).toBeNull()
  })

  it('scrollt so, dass der Gruppenanfang auf der Klebelinie landet', () => {
    // Gruppe beginnt 2000 px ueber dem Viewport, Kopf klebt bei 52 px.
    expect(collapseAnchorScrollY(5000, -2000, 52)).toBe(2948)
  })

  it('rundet auf ganze Pixel', () => {
    expect(collapseAnchorScrollY(1000.4, -100.3, 52)).toBe(848)
  })
})
