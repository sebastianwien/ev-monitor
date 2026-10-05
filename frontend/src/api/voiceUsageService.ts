import apiClient from './axios'

export interface VoiceUsageTotals {
    calls: number
    successCalls: number
    failedCalls: number
    distinctUsers: number
    audioSeconds: number
    transcribeTokens: number
    extractPromptTokens: number
    extractCompletionTokens: number
    costUsd: number
    avgLatencyMs: number
    avgFieldsFilled: number
    avgUncertain: number
    costPerSuccessUsd: number
}

export interface VoiceUsageDay {
    date: string
    calls: number
    failedCalls: number
    audioSeconds: number
    transcribeTokens: number
    extractPromptTokens: number
    extractCompletionTokens: number
    costUsd: number
    avgLatencyMs: number
}

export interface VoiceUsageModel {
    name: string
    kind: 'transcribe' | 'extract'
    calls: number
    tokens: number
    audioSeconds: number
}

export interface VoiceUsageInsights {
    periodDays: number
    totals: VoiceUsageTotals
    days: VoiceUsageDay[]
    models: VoiceUsageModel[]
    errors: { code: string; count: number }[]
    topUsers: { userId: string; calls: number; failedCalls: number; costUsd: number }[]
    allTimeCostUsd: number
}

export async function getAdminVoiceUsage(days: number): Promise<VoiceUsageInsights> {
    const res = await apiClient.get('/admin/voice-usage', { params: { days } })
    return res.data
}
