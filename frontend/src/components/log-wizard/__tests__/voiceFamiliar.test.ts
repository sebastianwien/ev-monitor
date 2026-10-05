import { describe, it, expect, beforeEach } from 'vitest'
import { recordVoiceUse, voiceFamiliar } from '../voiceFamiliar'

describe('voiceFamiliar', () => {
  beforeEach(() => localStorage.clear())

  it('keeps the explaining card for the first two recordings', () => {
    expect(voiceFamiliar()).toBe(false)
    recordVoiceUse()
    expect(voiceFamiliar()).toBe(false)
    recordVoiceUse()
    expect(voiceFamiliar()).toBe(true)
  })

  it('treats a broken counter as new', () => {
    localStorage.setItem('voicelog_uses', 'kaputt')
    expect(voiceFamiliar()).toBe(false)
    recordVoiceUse()
    expect(localStorage.getItem('voicelog_uses')).toBe('1')
  })
})
