import apiClient from './axios'

export async function getSurveyStatus(slug: string): Promise<{ responded: boolean }> {
    const res = await apiClient.get(`/surveys/${slug}/status`)
    return res.data
}

export async function submitSurvey(slug: string, answers: Record<string, string | string[]>): Promise<void> {
    await apiClient.post(`/surveys/${slug}/respond`, answers)
}

export interface AdminSurveySummary {
    slug: string
    responses: number
}

export interface AdminSurveyResponse {
    createdAt: string
    answers: Record<string, string | string[]>
}

export async function getAdminSurveys(): Promise<AdminSurveySummary[]> {
    const res = await apiClient.get('/admin/surveys')
    return res.data
}

export async function getAdminSurveyResponses(slug: string): Promise<AdminSurveyResponse[]> {
    const res = await apiClient.get(`/admin/surveys/${encodeURIComponent(slug)}`)
    return res.data
}
