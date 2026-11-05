import { describe, it, expect } from 'vitest'
import { eventRows } from '../seoEvents'

const stat = (name: string, goal = true) => ({ name, visitors: 10, events: 12, goal })

describe('eventRows', () => {
  it('Katalog-Event mit Goal und Daten ist ok und trägt Titel und Bereich', () => {
    const row = eventRows([stat('login')]).find(r => r.name === 'login')!
    expect(row).toMatchObject({ status: 'ok', visitors: 10, events: 12, area: 'anmeldung', title: 'Angemeldet' })
  })

  it('kommt an, ist aber kein Goal', () => {
    expect(eventRows([stat('login', false)]).find(r => r.name === 'login')!.status).toBe('no_goal')
  })

  it('Katalog-Event ohne Daten ist still, mit 0 statt fehlender Zahl', () => {
    const row = eventRows([]).find(r => r.name === 'login')!
    expect(row).toMatchObject({ status: 'silent', visitors: 0, events: 0 })
  })

  it('Name aus Plausible, den der Katalog nicht kennt, ist unbekannt', () => {
    const row = eventRows([stat('altes_event')]).find(r => r.name === 'altes_event')!
    expect(row).toMatchObject({ status: 'unknown', title: 'altes_event', area: null })
  })

  it('Goal ohne Code wird mit seinem Text gezeigt', () => {
    expect(eventRows([stat('Outbound Link: Click')]).find(r => r.name === 'Outbound Link: Click')).toMatchObject({ status: 'ok', area: 'plausible' })
  })

  it('jeder Katalog-Eintrag erscheint genau einmal', () => {
    const names = eventRows([stat('login'), stat('login')]).map(r => r.name)
    expect(new Set(names).size).toBe(names.length)
  })
})
