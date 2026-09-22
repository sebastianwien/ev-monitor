import { describe, it, expect, beforeEach } from 'vitest'
import { useCarShareSheet } from '../useCarShareSheet'

describe('useCarShareSheet', () => {
    beforeEach(() => useCarShareSheet().close())

    it('ist zu Beginn geschlossen', () => {
        expect(useCarShareSheet().sheet.value).toBeNull()
    })

    it('oeffnet fuer ein Auto mit Einstiegsquelle und teilt den Zustand ueber Aufrufer hinweg', () => {
        const a = useCarShareSheet()
        const b = useCarShareSheet()
        a.open('car-1', 'Tesla Model 3', 'peer_card')
        expect(b.sheet.value).toEqual({ carId: 'car-1', title: 'Tesla Model 3', source: 'peer_card' })
    })

    it('schliesst wieder', () => {
        const s = useCarShareSheet()
        s.open('car-1', 'Tesla Model 3', 'car_header')
        s.close()
        expect(s.sheet.value).toBeNull()
    })
})
