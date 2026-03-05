import api from './axios'

export interface WallboxConnection {
  id: string
  userId: string
  ocppChargePointId: string
  carId: string | null
  displayName: string | null
  active: boolean
}

export interface RegisterWallboxRequest {
  userId: string
  ocppChargePointId: string
  carId: string | null
  displayName: string | null
}

const WALLBOX_BASE_URL = import.meta.env.VITE_WALLBOX_SERVICE_URL || 'http://localhost:8090'

async function getConnections(userId: string): Promise<WallboxConnection[]> {
  const res = await api.get(`${WALLBOX_BASE_URL}/api/wallbox/connections`, { params: { userId } })
  return res.data
}

async function registerConnection(request: RegisterWallboxRequest): Promise<WallboxConnection> {
  const res = await api.post(`${WALLBOX_BASE_URL}/api/wallbox/connections`, request)
  return res.data
}

async function deleteConnection(id: string, userId: string): Promise<void> {
  await api.delete(`${WALLBOX_BASE_URL}/api/wallbox/connections/${id}`, { params: { userId } })
}

export const wallboxService = { getConnections, registerConnection, deleteConnection }
