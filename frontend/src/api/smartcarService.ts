import api from './axios'

/** Status einer einzelnen Smartcar-Verbindung (ein Fahrzeug). */
export interface SmartcarCarConnection {
  connected: boolean
  vehicleName: string | null
  carId: string | null
  vin: string | null
  vehicleState: string | null
  lastCheckedAt: string | null
  lastSoc: number | null
  sessionActive: boolean
  sessionStartedAt: string | null
  sessionEnergyAdded: number | null
}

/**
 * Antwort von GET /smartcar/status. Die Top-Level-Felder spiegeln die erste Verbindung
 * (Kompatibilität), `connections` trägt alle Fahrzeuge (AutoSync-Slots: ein Abo je Fahrzeug).
 */
export interface SmartcarConnectionStatus extends SmartcarCarConnection {
  connections?: SmartcarCarConnection[]
}

const DISCONNECTED_CONNECTION: SmartcarCarConnection = {
  connected: false, vehicleName: null, carId: null, vin: null, vehicleState: null,
  lastCheckedAt: null, lastSoc: null, sessionActive: false, sessionStartedAt: null, sessionEnergyAdded: null,
}

export const DISCONNECTED_STATUS: SmartcarConnectionStatus = { ...DISCONNECTED_CONNECTION, connections: [] }

/** Alle Verbindungen, mit Fallback auf die Top-Level-Felder für ältere Backends. */
export function connectionsOf(status: SmartcarConnectionStatus | null | undefined): SmartcarCarConnection[] {
  if (!status) return []
  if (status.connections) return status.connections.filter(c => c.connected)
  return status.connected ? [status] : []
}

/** Verbindung eines bestimmten Autos oder ein "nicht verbunden"-Status. */
export function connectionForCar(status: SmartcarConnectionStatus | null | undefined, carId: string): SmartcarCarConnection {
  return connectionsOf(status).find(c => c.carId === carId) ?? DISCONNECTED_CONNECTION
}

export default {
  async getStatus(): Promise<SmartcarConnectionStatus> {
    const resp = await api.get('/smartcar/status')
    return resp.data
  },

  async getAuthStartUrl(carId: string): Promise<{ authUrl: string; available: boolean }> {
    const resp = await api.get('/smartcar/auth/start', { params: { carId } })
    return resp.data
  },

  async disconnect(carId: string): Promise<void> {
    await api.delete('/smartcar/disconnect', { params: { carId } })
  },
}
