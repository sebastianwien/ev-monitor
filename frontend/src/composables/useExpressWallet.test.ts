import { describe, it, expect } from 'vitest'
import { detectExpressWallet } from './useExpressWallet'

const IOS_SAFARI = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'
const ANDROID_CHROME = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36'
const DESKTOP_FIREFOX = 'Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0'

describe('detectExpressWallet', () => {
  it('erkennt Apple Pay über ApplePaySession, unabhängig vom User-Agent', () => {
    expect(detectExpressWallet({ ApplePaySession: class {} }, IOS_SAFARI)).toBe('apple')
  })
  it('erkennt Google Pay auf Android', () => {
    expect(detectExpressWallet({}, ANDROID_CHROME)).toBe('google')
  })
  it('gibt null zurück, wenn keine Wallet erkennbar ist', () => {
    expect(detectExpressWallet({}, DESKTOP_FIREFOX)).toBeNull()
  })
  it('bevorzugt Apple Pay, wenn ApplePaySession auf einem Android-UA vorhanden wäre', () => {
    expect(detectExpressWallet({ ApplePaySession: class {} }, ANDROID_CHROME)).toBe('apple')
  })
})
