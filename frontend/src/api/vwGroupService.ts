import api from './axios'

export interface VwGroupConnectionStatus {
  connected: boolean
  brand: string | null
  vin: string | null
  make: string | null
  model: string | null
  year: number | null
  mqttActive: boolean
  lastSoc: number | null
  lastRangeKm: number | null
  vehicleState: 'charging' | 'not_charging' | null
  lastCheckedAt: string | null
}

export interface VwGroupAuthStart {
  userCode: string
  verificationUri: string
  expiresIn: number
}

const VW_GROUP_BRANDS = ['skoda', 'vw', 'audi', 'seat', 'cupra']

// Brands that use credential (email+password) flow instead of Device Code Flow
const CREDENTIAL_BRANDS = ['vw', 'seat', 'cupra']

export function isVwGroupBrand(brand: string | null | undefined): boolean {
  return VW_GROUP_BRANDS.includes(brand?.toLowerCase() ?? '')
}

export function isCredentialBrand(brand: string | null | undefined): boolean {
  return CREDENTIAL_BRANDS.includes(brand?.toLowerCase() ?? '')
}

export default {
  async getStatus(brand: string): Promise<VwGroupConnectionStatus> {
    const resp = await api.get('/vwgroup/connection', { params: { brand } })
    return resp.data
  },

  async startAuth(brand: string, carId?: string): Promise<VwGroupAuthStart> {
    const resp = await api.post('/vwgroup/auth/start', null, { params: { brand, carId } })
    return resp.data
  },

  async startCredentialAuth(brand: string, carId: string | undefined, email: string, password: string): Promise<void> {
    await api.post('/vwgroup/auth/credentials', { email, password }, { params: { brand, carId } })
  },

  async pollAuthStatus(brand: string): Promise<{ status: string }> {
    const resp = await api.get('/vwgroup/auth/status', { params: { brand } })
    return resp.data
  },

  async disconnect(brand: string): Promise<void> {
    await api.delete('/vwgroup/connection', { params: { brand } })
  },
}
