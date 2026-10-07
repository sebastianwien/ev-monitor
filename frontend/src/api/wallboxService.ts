import api from './axios'

// Die userId kommt serverseitig aus dem JWT. Der Client schickt sie nicht mehr mit.
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
  ocppChargePointId: string
  carId: string | null
  displayName: string | null
}

export interface UpdateWallboxSettingsRequest {
  geohash: string | null
  tariffCentsPerKwh: number
}

async function getConnections(): Promise<WallboxConnection[]> {
  const res = await api.get('/wallbox/connections')
  return res.data
}

async function registerConnection(request: RegisterWallboxRequest): Promise<WallboxConnection> {
  const res = await api.post('/wallbox/connections', request)
  return res.data
}

async function updateSettings(id: string, request: UpdateWallboxSettingsRequest): Promise<WallboxConnection> {
  const res = await api.patch(`/wallbox/connections/${id}`, request)
  return res.data
}

async function deleteConnection(id: string): Promise<void> {
  await api.delete(`/wallbox/connections/${id}`)
}

export const wallboxService = { getConnections, registerConnection, updateSettings, deleteConnection }
