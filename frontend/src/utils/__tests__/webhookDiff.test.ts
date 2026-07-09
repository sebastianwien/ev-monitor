import { describe, it, expect } from 'vitest'
import { changedCells, type WebhookRow } from '../webhookDiff'

const signal = (value: string | null, oem: number | null, status: string | null) => ({
  value,
  oemUpdatedAt: oem,
  status,
})

const row = (id: string, overrides: Partial<WebhookRow> = {}): WebhookRow => ({
  id,
  receivedAt: '2026-07-08T06:04:29Z',
  odometer: signal('2456', 1000, 'SUCCESS'),
  energyAdded: signal('0.5', 2000, 'SUCCESS'),
  soc: signal('14', 3000, 'SUCCESS'),
  isCharging: signal('true', 4000, 'SUCCESS'),
  ...overrides,
})

describe('changedCells', () => {
  it('marks nothing for the oldest row (no predecessor)', () => {
    const marks = changedCells([row('a')])
    expect(marks[0].energyAdded).toEqual({ value: false, oem: false, status: false })
  })

  it('compares each row against the chronologically previous (next in desc list)', () => {
    // rows are newest-first: rows[0] is newer than rows[1]
    const newer = row('new', { energyAdded: signal('1', 2500, 'SUCCESS'), soc: signal('15', 3500, 'SUCCESS') })
    const older = row('old')
    const marks = changedCells([newer, older])

    expect(marks[0].energyAdded).toEqual({ value: true, oem: true, status: false })
    expect(marks[0].soc).toEqual({ value: true, oem: true, status: false })
    expect(marks[0].odometer).toEqual({ value: false, oem: false, status: false })
    expect(marks[1].energyAdded).toEqual({ value: false, oem: false, status: false })
  })

  it('marks status flips independently of value', () => {
    const newer = row('new', { energyAdded: signal('39', 2000, 'ERROR') })
    const older = row('old', { energyAdded: signal('39', 2000, 'SUCCESS') })
    const marks = changedCells([newer, older])

    expect(marks[0].energyAdded).toEqual({ value: false, oem: false, status: true })
  })

  it('treats null-to-value transitions as change', () => {
    const newer = row('new', { soc: signal('74', 3000, 'SUCCESS') })
    const older = row('old', { soc: signal(null, null, null) })
    const marks = changedCells([newer, older])

    expect(marks[0].soc).toEqual({ value: true, oem: true, status: true })
  })
})
