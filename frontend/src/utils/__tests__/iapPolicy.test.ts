import { describe, it, expect, vi, beforeEach } from 'vitest'

const platform = vi.hoisted(() => ({ native: false }))
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => platform.native },
}))

import { purchasesAvailable, donationsAvailable } from '../iapPolicy'

describe('iapPolicy.purchasesAvailable', () => {
  beforeEach(() => { platform.native = false })

  it('erlaubt Kaeufe im Web/PWA (Stripe-Checkout)', () => {
    platform.native = false
    expect(purchasesAvailable()).toBe(true)
  })

  it('verbietet In-App-Kaeufe in der nativen App (Apple Guideline 3.1.1)', () => {
    platform.native = true
    expect(purchasesAvailable()).toBe(false)
  })
})

describe('iapPolicy.donationsAvailable', () => {
  beforeEach(() => { platform.native = false })

  it('erlaubt Spenden-Links (Ko-fi/PayPal) im Web/PWA', () => {
    platform.native = false
    expect(donationsAvailable()).toBe(true)
  })

  it('verbietet Spenden-Links in der nativen App (Apple-Reject zu Unterstuetzen-Links)', () => {
    platform.native = true
    expect(donationsAvailable()).toBe(false)
  })
})
