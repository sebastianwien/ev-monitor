import api from './axios'

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
  // EU-Data-Act-Format: ZIP mit CSV-Clustern (Portal-Download). Die VIN wird
  // serverseitig aus der Datei gelesen und mit dem Fahrzeug abgeglichen.
  async uploadZip(carId: string, file: File): Promise<XpengJobDto> {
    const form = new FormData()
    form.append('carId', carId)
    form.append('file', file)
    const resp = await api.post('/imports/xpeng/upload-zip', form, {
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

  async deleteAllImportedData(): Promise<{ chargingLogs: number; trips: number; importJobs: number }> {
    const resp = await api.delete('/imports/xpeng/imported-data')
    return resp.data
  },
}

export default xpengService
