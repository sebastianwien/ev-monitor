import api from './axios'

/** Bundesweite Spritpreise je Liter (Tankerkoenig). updatedAt null = Fallback-Werte des Backends. */
export interface FuelPrices {
  petrolEurPerLitre: number
  dieselEurPerLitre: number
  updatedAt: string | null
}

export const fuelPriceService = {
  async get(): Promise<FuelPrices> {
    const { data } = await api.get<FuelPrices>('/public/fuel-prices')
    return data
  },
}
