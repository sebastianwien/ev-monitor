import apiClient from './axios'

export async function getSurveyStatus(slug: string): Promise<{ responded: boolean }> {
    const res = await apiClient.get(`/surveys/${slug}/status`)
    return res.data
}

export async function submitSurvey(slug: string, answers: Record<string, string | string[]>): Promise<void> {
    await apiClient.post(`/surveys/${slug}/respond`, answers)
}
