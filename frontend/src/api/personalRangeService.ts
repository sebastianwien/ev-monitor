import api from './axios'

/** The four calculator inputs, distances in km. Also the user's stored range profile. */
export interface PersonalRangeInputs {
  commuteKmPerDay: number
  highwaySharePercent: number
  homeCharging: boolean
  longTripKm: number
}

export type PersonalRangeDataLevel = 'MEASURED' | 'PARTIAL' | 'WLTP_FACTOR'

export type AssumptionKey = 'BATTERY' | 'SUMMER_CONSUMPTION' | 'WINTER_CONSUMPTION' | 'HIGHWAY_CONSUMPTION'
  | 'BASE_CONSUMPTION' | 'CHARGING_POWER' | 'PRICE'

export interface PersonalRangeAssumption {
  key: AssumptionKey
  measured: boolean
  value: number
  /** share of community data in value; between 0 and 100 for a blend (highway while few cars report it) */
  measuredPercent?: number
}

export interface PersonalRangeResult {
  winterRangeKm: number
  summerRangeKm: number
  longTripStops: number
  longTripChargingMinutes: number
  chargesPerWeek: number
  monthlyCostEur: number
  dataLevel: PersonalRangeDataLevel
  assumptions: PersonalRangeAssumption[]
}

export const personalRangeService = {
  /** @param email true sends the result to the user's address as well (once, right after the gate) */
  async calculate(brand: string, model: string, inputs: PersonalRangeInputs, email = false): Promise<PersonalRangeResult> {
    const url = `/personal-range/${encodeURIComponent(brand)}/${encodeURIComponent(model)}`
    const { data } = email
      ? await api.post<PersonalRangeResult>(url, inputs, { params: { email: true } })
      : await api.post<PersonalRangeResult>(url, inputs)
    return data
  },

  /** Stored inputs of the logged-in user, null before the first calculation. */
  async getProfile(): Promise<PersonalRangeInputs | null> {
    const response = await api.get<PersonalRangeInputs>('/personal-range/profile')
    return response.status === 200 ? response.data : null
  },
}
