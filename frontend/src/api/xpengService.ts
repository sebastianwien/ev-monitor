import api from './axios'

export interface XpengConnectionDto {
  id: string
  carId: string
  vin: string
  vinMasked: string
  consentGrantedAt: string
  consentVersion: string
  lastSuccessfulImportAt: string | null
  totalImportsCount: number
}

export interface XpengJobDto {
  id: string
  carId: string
  status: 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED'
  importedTrips: number
  importedSessions: number
  skippedDuplicates: number
  dataRangeStart: string | null
  dataRangeEnd: string | null
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export const xpengService = {
  async listConnections(): Promise<XpengConnectionDto[]> {
    const resp = await api.get('/imports/xpeng/connections')
    return resp.data
  },

  async grantConsent(carId: string, vin: string): Promise<XpengConnectionDto> {
    const resp = await api.post('/imports/xpeng/connections', {
      carId, vin, consentAccepted: true,
    })
    return resp.data
  },

  async revoke(connectionId: string): Promise<void> {
    await api.delete(`/imports/xpeng/connections/${connectionId}`)
  },

  async upload(carId: string, file: File, password?: string): Promise<XpengJobDto> {
    const form = new FormData()
    form.append('carId', carId)
    form.append('file', file)
    if (password) form.append('password', password)
    const resp = await api.post('/imports/xpeng/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return resp.data
  },

  async getJob(jobId: string): Promise<XpengJobDto> {
    const resp = await api.get(`/imports/xpeng/jobs/${jobId}`)
    return resp.data
  },

  async listJobs(): Promise<XpengJobDto[]> {
    const resp = await api.get('/imports/xpeng/jobs')
    return resp.data
  },
}

export default xpengService
