// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
vi.mock('../analytics', () => ({ analytics: { trackUpsellViewed: vi.fn() } }))
import { analytics } from '../analytics'
import { upsellSourceOf, onUpsellNavigation, currentUpsellSource } from '../upsellSource'

const route = (path: string, query: Record<string, unknown> = {}, name?: string) => ({ path, query, name }) as any

describe('upsellSourceOf', () => {
  it('nimmt ?from, sonst die vorige Seite, sonst direct', () => {
    expect(upsellSourceOf(route('/supporter', { from: 'voice' }), route('/erfassen', {}, 'log-wizard'))).toBe('voice')
    expect(upsellSourceOf(route('/supporter'), route('/logs', {}, 'logs'))).toBe('logs')
    expect(upsellSourceOf(route('/upgrade'), route('/', {}, undefined))).toBe('direct')
  })

  it('kürzt und säubert freie Werte aus der URL', () => {
    expect(upsellSourceOf(route('/supporter', { from: '<script>x' }), route('/'))).toBe('scriptx')
    expect(upsellSourceOf(route('/supporter', { from: 'a'.repeat(80) }), route('/'))).toHaveLength(40)
  })
})

describe('onUpsellNavigation', () => {
  beforeEach(() => { sessionStorage.clear(); vi.mocked(analytics.trackUpsellViewed).mockClear() })

  it('misst Supporter und Upgrade mit Herkunft und merkt sie für den Checkout', () => {
    onUpsellNavigation(route('/supporter', { from: 'voice' }), route('/erfassen', {}, 'log-wizard'))
    expect(analytics.trackUpsellViewed).toHaveBeenCalledWith('supporter', 'voice')
    expect(currentUpsellSource()).toBe('voice')
  })

  it('ignoriert andere Seiten und reine Query-Wechsel auf derselben Seite', () => {
    onUpsellNavigation(route('/logs'), route('/dashboard', {}, 'dashboard'))
    onUpsellNavigation(route('/upgrade', { plan: 'yearly' }), route('/upgrade', {}, 'upgrade'))
    expect(analytics.trackUpsellViewed).not.toHaveBeenCalled()
    expect(currentUpsellSource()).toBe('direct')
  })
})
