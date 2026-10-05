// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import type { VoiceUsageInsights } from '../../../api/voiceUsageService'

const getAdminVoiceUsage = vi.fn()
vi.mock('../../../api/voiceUsageService', () => ({ getAdminVoiceUsage: (d: number) => getAdminVoiceUsage(d) }))
// Chart.js braucht Canvas; in jsdom reicht ein Platzhalter
vi.mock('vue-chartjs', () => ({ Line: { template: '<div class="chart" />' }, Bar: { template: '<div class="chart" />' } }))

import AdminVoiceUsageTab from '../AdminVoiceUsageTab.vue'

const sample: VoiceUsageInsights = {
    periodDays: 30,
    totals: { calls: 12, successCalls: 10, failedCalls: 2, distinctUsers: 3, audioSeconds: 120, transcribeTokens: 400,
        extractPromptTokens: 9000, extractCompletionTokens: 1000, costUsd: 0.0123, avgLatencyMs: 1500,
        avgFieldsFilled: 5.2, avgUncertain: 0.4, costPerSuccessUsd: 0.00123 },
    days: [{ date: '2026-10-04', calls: 12, failedCalls: 2, audioSeconds: 120, transcribeTokens: 400, extractPromptTokens: 9000,
        extractCompletionTokens: 1000, costUsd: 0.0123, avgLatencyMs: 1500 }],
    models: [{ name: 'voxtral-mini-latest', kind: 'transcribe', calls: 12, tokens: 400, audioSeconds: 120 },
        { name: 'voxtral-small-latest', kind: 'extract', calls: 10, tokens: 10000, audioSeconds: 0 }],
    errors: [{ code: 'RATE_LIMITED', count: 2 }],
    topUsers: [{ userId: 'abcdef12-0000-0000-0000-000000000000', calls: 8, failedCalls: 1, costUsd: 0.009 }],
    allTimeCostUsd: 1.5,
}

let app: App | null = null
afterEach(() => {
    app?.unmount()
    app = null
    document.body.innerHTML = ''
    getAdminVoiceUsage.mockReset()
})

async function mount(): Promise<HTMLElement> {
    const host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(AdminVoiceUsageTab)
    app.mount(host)
    await nextTick()
    await nextTick()
    return host
}

describe('AdminVoiceUsageTab', () => {
    it('laedt 30 Tage, zeigt Kacheln, Modelle, Fehler und Top-Nutzer ohne volle User-ID', async () => {
        getAdminVoiceUsage.mockResolvedValue(sample)
        const host = await mount()
        const text = host.textContent ?? ''

        expect(getAdminVoiceUsage).toHaveBeenCalledWith(30)
        expect(text).toContain('12')
        expect(text).toContain('$0.0123')
        expect(text).toContain('$1.50')
        expect(text).toContain('10.000')
        expect(text).toContain('voxtral-small-latest')
        expect(text).toContain('RATE_LIMITED')
        expect(text).toContain('abcdef12')
        expect(text).not.toContain('abcdef12-0000')
        expect(host.querySelectorAll('.chart').length).toBe(4)
    })

    it('wechselt den Zeitraum per Preset und laedt neu', async () => {
        getAdminVoiceUsage.mockResolvedValue(sample)
        const host = await mount()
        const btn = [...host.querySelectorAll('button')].find(b => b.textContent?.trim() === '90 Tage')!
        btn.click()
        await nextTick()
        expect(getAdminVoiceUsage).toHaveBeenLastCalledWith(90)
    })

    it('zeigt eine Fehlermeldung, wenn die API scheitert', async () => {
        getAdminVoiceUsage.mockRejectedValue(new Error('500'))
        const host = await mount()
        await nextTick()
        expect(host.textContent).toContain('konnte nicht geladen werden')
    })
})
