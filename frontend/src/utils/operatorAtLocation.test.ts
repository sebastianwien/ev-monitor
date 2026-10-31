import { describe, it, expect } from 'vitest'
import { operatorGapQuery } from './operatorAtLocation'

const log = { id: 'l1', geohash: 'u1mc1v8', cpoName: null as string | null }

describe('operatorGapQuery', () => {
  it('öffentlich mit neuem Betreiber: fragt am gespeicherten Ort', () => {
    expect(operatorGapQuery(log, { isPublicCharging: true, cpoName: ' IONITY ' }))
      .toEqual({ geohash: 'u1mc1v8', cpoName: 'IONITY', excludeLogId: 'l1' })
  })

  it('geänderter Betreiber zählt auch', () => {
    expect(operatorGapQuery({ ...log, cpoName: 'EnBW' }, { isPublicCharging: true, cpoName: 'IONITY' })).not.toBeNull()
  })

  it('unveränderter Betreiber, privat, leer oder ohne Ort: keine Abfrage', () => {
    expect(operatorGapQuery({ ...log, cpoName: 'IONITY' }, { isPublicCharging: true, cpoName: 'IONITY' })).toBeNull()
    expect(operatorGapQuery(log, { isPublicCharging: false, cpoName: 'IONITY' })).toBeNull()
    expect(operatorGapQuery(log, { isPublicCharging: true, cpoName: '  ' })).toBeNull()
    expect(operatorGapQuery({ ...log, geohash: null }, { isPublicCharging: true, cpoName: 'IONITY' })).toBeNull()
  })
})
