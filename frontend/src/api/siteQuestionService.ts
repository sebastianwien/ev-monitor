import api from './axios'

/** Eine Säule, die an diesem Ort in Frage kommt - öffentliche Registerdaten, ohne Position. */
export interface SiteCandidate {
  name: string
  maxAcKw: number | null
  maxDcKw: number | null
  chargePoints: number
  address: string | null
}

/** Frage nach der Säule: an einem Ort mit einer Ladeart kommen mehrere Säulen in Frage. */
export interface SiteQuestion {
  id: string
  /** Zelle (7 Stellen) der Ladungen, um die es geht */
  geohash: string | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
  /** Ladungen dort, die noch keine Säule haben */
  logCount: number
  lastLoggedAt: string
  candidates: SiteCandidate[]
}

export interface SiteAnswer {
  /** Ladungen, die die Säule bekommen haben */
  assigned: number
  watt: number
}

export const siteQuestionService = {
  async list(): Promise<SiteQuestion[]> {
    return (await api.get<SiteQuestion[]>('/charging-sites/questions')).data
  },
  async answer(id: string, name: string): Promise<SiteAnswer> {
    return (await api.post<SiteAnswer>(`/charging-sites/questions/${id}/answer`, { name })).data
  },
  async dismiss(id: string): Promise<void> {
    await api.post(`/charging-sites/questions/${id}/dismiss`)
  },
}
