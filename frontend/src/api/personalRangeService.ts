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
  async calculate(brand: string, model: string, inputs: PersonalRangeInputs): Promise<PersonalRangeResult> {
    const { data } = await api.post<PersonalRangeResult>(
      `/personal-range/${encodeURIComponent(brand)}/${encodeURIComponent(model)}`, inputs)
    return data
  },

  /** Stored inputs of the logged-in user, null before the first calculation. */
  async getProfile(): Promise<PersonalRangeInputs | null> {
    const response = await api.get<PersonalRangeInputs>('/personal-range/profile')
    return response.status === 200 ? response.data : null
  },
}
