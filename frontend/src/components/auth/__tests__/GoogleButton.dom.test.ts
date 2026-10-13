// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { createApp, type App } from 'vue'
import GoogleButton from '../GoogleButton.vue'

let app: App | null = null
afterEach(() => { app?.unmount(); app = null; document.body.innerHTML = '' })

function mount(props: Record<string, unknown>) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(GoogleButton, props)
  app.mount(host)
  return host
}

describe('GoogleButton', () => {
  it('links to the Google login with the coloured G and the given label', () => {
    const host = mount({ label: 'Mit Google fortfahren' })
    const link = host.querySelector('a') as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/oauth2/authorization/google')
    expect(link.textContent?.trim()).toBe('Mit Google fortfahren')
    // the four brand colours of the G, as in Google's sign-in guidelines
    const fills = [...host.querySelectorAll('svg path')].map(p => p.getAttribute('fill'))
    expect(fills).toEqual(['#4285F4', '#34A853', '#FBBC05', '#EA4335'])
    expect(host.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true')
  })

  it('reports the click before the browser leaves the page', () => {
    const onClick = vi.fn()
    const host = mount({ label: 'Weiter', onClick })
    const link = host.querySelector('a') as HTMLAnchorElement
    link.addEventListener('click', e => e.preventDefault())
    link.click()
    expect(onClick).toHaveBeenCalledOnce()
  })
})
