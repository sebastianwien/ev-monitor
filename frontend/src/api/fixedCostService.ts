import api from './axios'

export type FixedCostCategory = 'INSURANCE' | 'TAX' | 'TOLL' | 'CLEANING' | 'MAINTENANCE' | 'LEASING' | 'FINANCING' | 'TIRES' | 'TUNING' | 'INCOME' | 'COMPENSATION' | 'OTHER'
export type FixedCostRecurrence = 'ONE_TIME' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'

export interface FixedCost {
  id: string
  carId: string
  description: string
  amount: number
  category: FixedCostCategory
  recurrence: FixedCostRecurrence
  date: string | null
  startDate: string | null
  endDate: string | null
  createdAt: string
}

export interface FixedCostRequest {
  description: string
  amount: number
  category: FixedCostCategory
  recurrence: FixedCostRecurrence
  date: string | null
  startDate: string | null
  endDate: string | null
}

export const fixedCostService = {
  async list(carId: string): Promise<FixedCost[]> {
    const response = await api.get('/fixed-costs', { params: { carId } })
    return response.data
  },

  async create(carId: string, request: FixedCostRequest): Promise<FixedCost> {
    const response = await api.post('/fixed-costs', request, { params: { carId } })
    return response.data
  },

  async update(id: string, request: FixedCostRequest): Promise<FixedCost> {
    const response = await api.put(`/fixed-costs/${id}`, request)
    return response.data
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/fixed-costs/${id}`)
  },
}
