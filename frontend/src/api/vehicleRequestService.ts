import api from './axios'

/** Meldung "Fehlt etwas?" aus dem Auto-Anlegen: Variante, Modell oder Hersteller fehlt. */
export interface MissingVehicleRequest {
  freeText: string
  contactEmail: string
  brand: string | null
  model: string | null
  netKwh: number | null
  buildYear: number | null
}

export const vehicleRequestService = {
  /** 200 ohne Body; 429 bei zu vielen Meldungen (3 je Tag). */
  async submit(request: MissingVehicleRequest): Promise<void> {
    await api.post('/vehicle-requests', request)
  },
}
