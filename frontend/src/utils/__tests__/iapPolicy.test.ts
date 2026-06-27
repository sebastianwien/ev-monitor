import { describe, it, expect, vi, beforeEach } from 'vitest'

const platform = vi.hoisted(() => ({ native: false }))
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => platform.native },
}))

import { purchasesAvailable } from '../iapPolicy'

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
