// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import { createI18n } from 'vue-i18n'
import de from '../../../locales/de.yaml'

const submit = vi.fn()
vi.mock('../../../api/vehicleRequestService', () => ({
    vehicleRequestService: { submit: (r: unknown) => submit(r) },
}))
vi.mock('../../../stores/auth', () => ({
    useAuthStore: () => ({ user: { sub: 'max@example.com' } }),
}))

import MissingVehicleDialog from '../MissingVehicleDialog.vue'

/** Dialog "Fehlt etwas?": Pflichtfeld Freitext, 150 Zeichen, Mail aus dem Konto, Marke/Modell aus der Auswahl. */
describe('MissingVehicleDialog', () => {
    let app: App | null = null
    let host: HTMLElement | null = null

    afterEach(() => {
        app?.unmount()
        host?.remove()
        document.body.innerHTML = ''
        submit.mockReset()
    })

    async function mount(props: { brand: string | null; model: string | null }) {
        host = document.createElement('div')
        document.body.appendChild(host)
        const i18n = createI18n({ legacy: false, locale: 'de', messages: { de }, missingWarn: false, fallbackWarn: false })
        const emitted: string[] = []
        app = createApp(MissingVehicleDialog, { ...props, onClose: () => emitted.push('close') })
        app.use(i18n)
        app.mount(host)
        await nextTick()
        return emitted
    }

    const dialog = () => document.querySelector('[data-testid="missing-vehicle-dialog"]') as HTMLElement
    const textarea = () => dialog().querySelector('textarea') as HTMLTextAreaElement
    const email = () => dialog().querySelector('input[type="email"]') as HTMLInputElement
    const form = () => dialog().querySelector('form') as HTMLFormElement

    it('prefills the mail from the account and limits the text to 150 characters', async () => {
        await mount({ brand: null, model: null })
        expect(email().value).toBe('max@example.com')
        expect(textarea().maxLength).toBe(150)
        expect(textarea().required).toBe(true)
    })

    it('sends text, mail, optional numbers and the current brand/model', async () => {
        submit.mockResolvedValue(undefined)
        await mount({ brand: 'POLESTAR', model: 'POLESTAR_2' })
        textarea().value = 'Polestar 2 Long Range Dual Motor 2022'
        textarea().dispatchEvent(new Event('input'))
        const kwh = dialog().querySelector('input[name="netKwh"]') as HTMLInputElement
        kwh.value = '78'
        kwh.dispatchEvent(new Event('input'))
        await nextTick()

        form().dispatchEvent(new Event('submit', { cancelable: true }))
        await nextTick()
        await nextTick()

        expect(submit).toHaveBeenCalledWith({
            freeText: 'Polestar 2 Long Range Dual Motor 2022',
            contactEmail: 'max@example.com',
            brand: 'POLESTAR',
            model: 'POLESTAR_2',
            netKwh: 78,
            buildYear: null,
        })
        expect(dialog().textContent).toContain('Danke! Ich melde mich')
    })

    it('does not send an empty text', async () => {
        await mount({ brand: null, model: null })
        form().dispatchEvent(new Event('submit', { cancelable: true }))
        await nextTick()
        expect(submit).not.toHaveBeenCalled()
    })

    it('explains a 429 instead of a generic error', async () => {
        submit.mockRejectedValue({ response: { status: 429 } })
        await mount({ brand: null, model: null })
        textarea().value = 'Lucid Air'
        textarea().dispatchEvent(new Event('input'))
        await nextTick()
        form().dispatchEvent(new Event('submit', { cancelable: true }))
        await nextTick()
        await nextTick()
        expect(dialog().textContent).toContain('schon drei Meldungen')
    })
})
