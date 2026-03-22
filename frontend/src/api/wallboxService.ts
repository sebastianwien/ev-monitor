import api from './axios'

export interface WallboxConnection {
  id: string
  userId: string
  ocppChargePointId: string
  carId: string | null
  displayName: string | null
  geohash: string | null
  tariffCentsPerKwh: number
  ocppPassword: string
  active: boolean
}

export interface RegisterWallboxRequest {
  userId: string
  ocppChargePointId: string
  carId: string | null
  displayName: string | null
}

export interface UpdateWallboxSettingsRequest {
  geohash: string | null
  tariffCentsPerKwh: number
}

async function getConnections(userId: string): Promise<WallboxConnection[]> {
  const res = await api.get('/wallbox/connections', { params: { userId } })
  return res.data
}

async function registerConnection(request: RegisterWallboxRequest): Promise<WallboxConnection> {
  const res = await api.post('/wallbox/connections', request)
  return res.data
}

async function updateSettings(id: string, userId: string, request: UpdateWallboxSettingsRequest): Promise<WallboxConnection> {
  const res = await api.patch(`/wallbox/connections/${id}`, request, { params: { userId } })
  return res.data
}

async function deleteConnection(id: string, userId: string): Promise<void> {
  await api.delete(`/wallbox/connections/${id}`, { params: { userId } })
}

export const wallboxService = { getConnections, registerConnection, updateSettings, deleteConnection }
