import apiClient from './axios'

export async function getSurveyStatus(slug: string): Promise<{ responded: boolean }> {
    const res = await apiClient.get(`/api/surveys/${slug}/status`)
    return res.data
}

export async function submitSurvey(slug: string, answers: Record<string, string>): Promise<void> {
    await apiClient.post(`/api/surveys/${slug}/respond`, answers)
}
