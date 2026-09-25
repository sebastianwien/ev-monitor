import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../../api/teslaFleetService', () => ({ default: { getStatus: vi.fn().mockRejectedValue(new Error('no tesla')) } }))
vi.mock('../../api/euDataActSyncService', async (orig) => {
    const actual = await orig<typeof import('../../api/euDataActSyncService')>()
    return { ...actual, default: { ...actual.default, getStatus: vi.fn().mockResolvedValue([]) } }
})
vi.mock('../../api/carService', () => ({ carService: { getCars: vi.fn() } }))

const carsOf = (...brands: string[]) => brands.map((brand, i) => ({ id: `car-${i}`, brand, model: 'X' }))

async function setup(brands: string[]) {
    vi.resetModules()
    setActivePinia(createPinia())
    const { carService } = await import('../../api/carService')
    vi.mocked(carService.getCars).mockResolvedValue(carsOf(...brands) as never)
    const eudaService = (await import('../../api/euDataActSyncService')).default
    const authStore = (await import('../../stores/auth')).useAuthStore()
    vi.spyOn(authStore, 'isAuthenticated').mockReturnValue(true)
    const { useFeatureAnnouncements } = await import('../useFeatureAnnouncements')
    const api = useFeatureAnnouncements()
    await vi.waitFor(() => expect(carService.getCars).toHaveBeenCalled())
    await new Promise(r => setTimeout(r, 0))
    return { api, eudaService }
}

beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
})

describe('useFeatureAnnouncements - EU-Data-Act-Kontext', () => {
    it('fragt den Portal-Status nur ab, wenn ueberhaupt ein VW-Group-Auto existiert', async () => {
        const { eudaService } = await setup(['TESLA'])
        expect(eudaService.getStatus).not.toHaveBeenCalled()
    })

    it('fragt den Portal-Status ab, sobald ein VW-Group-Auto dabei ist', async () => {
        const { eudaService } = await setup(['TESLA', 'SKODA'])
        expect(eudaService.getStatus).toHaveBeenCalledTimes(1)
    })
})

describe('useFeatureAnnouncements - Markenname in der Ankuendigung', () => {
    it('reicht die Marke des VW-Group-Autos als Parameter durch', async () => {
        const { api } = await setup(['SKODA'])
        expect(api.announcementParams.value).toEqual({ brand: 'Skoda' })
    })

    it('nennt Volkswagen kurz VW, so wie die Nutzer selbst', async () => {
        const { api } = await setup(['VOLKSWAGEN'])
        expect(api.announcementParams.value).toEqual({ brand: 'VW' })
    })

    it('liefert ohne VW-Group-Auto keine Parameter', async () => {
        const { api } = await setup(['TESLA'])
        expect(api.announcementParams.value).toEqual({})
    })
})

describe('useFeatureAnnouncements - kein Aufblitzen vor dem Portal-Status', () => {
    it('meldet erst dann ein VW-Group-Auto, wenn auch der Verbindungsstatus da ist', async () => {
        vi.resetModules()
        setActivePinia(createPinia())
        const { carService } = await import('../../api/carService')
        vi.mocked(carService.getCars).mockResolvedValue(carsOf('SKODA') as never)
        const eudaService = (await import('../../api/euDataActSyncService')).default
        let resolveStatus: (v: unknown[]) => void = () => {}
        vi.mocked(eudaService.getStatus).mockReturnValue(
            new Promise(resolve => { resolveStatus = resolve as (v: unknown[]) => void }) as never)
        const authStore = (await import('../../stores/auth')).useAuthStore()
        vi.spyOn(authStore, 'isAuthenticated').mockReturnValue(true)
        const { useFeatureAnnouncements } = await import('../useFeatureAnnouncements')
        const { total } = useFeatureAnnouncements()

        // Status steht noch aus: bis dahin darf die Ankuendigung nicht auftauchen, sonst sieht
        // ein laengst verbundener Nutzer sie kurz aufblitzen - und ein Klick darauf verbraucht sie.
        await vi.waitFor(() => expect(eudaService.getStatus).toHaveBeenCalled())
        const before = total.value

        resolveStatus([])
        await vi.waitFor(() => expect(total.value).toBe(before + 1))
    })
})
