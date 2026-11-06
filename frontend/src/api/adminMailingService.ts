import apiClient from './axios'

export interface AdminMailing {
  key: string
  kind: 'PFLICHT' | 'LEBENSZYKLUS'
  title: string
  purpose: string
  trigger: string
  audience: string
  goal: string | null
  sent: number
  failed: number
  lastSentAt: string | null
  visits: number
  goalVisitors: number
}

export interface AdminMailingSend {
  sentAt: string
  sent: boolean
  userId: string | null
  email: string | null
}

export async function getAdminMailings(days: number): Promise<AdminMailing[]> {
  const res = await apiClient.get('/admin/mailings', { params: { days } })
  return res.data
}

export async function getAdminMailingRecent(key: string): Promise<AdminMailingSend[]> {
  const res = await apiClient.get(`/admin/mailings/${encodeURIComponent(key)}/recent`)
  return res.data
}

export async function getAdminMailingPreview(key: string): Promise<string> {
  const res = await apiClient.get(`/admin/mailings/${encodeURIComponent(key)}/preview`)
  return res.data.html
}

/**
 * Näherung für den Anteil der Mail-Besuche mit Ziel-Goal, in ganzen Prozent; null ohne Besuche.
 * Zähler (Goal-Besucher) und Nenner (Besuche) sind Tagessummen aus Plausible und nicht exakt dieselbe Einheit.
 */
export function goalRate(goalVisitors: number, visits: number): number | null {
  if (visits <= 0) return null
  return Math.min(100, Math.round((goalVisitors / visits) * 100))
}
