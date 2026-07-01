import { describe, it, expect, beforeEach } from 'vitest'
import { useLightbox, type LightboxImage } from '../useLightbox'

const imgs: LightboxImage[] = [
    { src: '/a.jpg', alt: 'A' },
    { src: '/b.jpg', alt: 'B' },
    { src: '/c.jpg', alt: 'C' },
]

describe('useLightbox', () => {
    // Singleton-State zwischen Tests zuruecksetzen.
    beforeEach(() => {
        const { close } = useLightbox()
        close()
    })

    it('open() setzt State und klemmt den Startindex in den gueltigen Bereich', () => {
        const lb = useLightbox()
        lb.open(imgs, 1)
        expect(lb.isOpen.value).toBe(true)
        expect(lb.index.value).toBe(1)
        expect(lb.current.value).toEqual({ src: '/b.jpg', alt: 'B' })

        lb.open(imgs, 99)
        expect(lb.index.value).toBe(2)

        lb.open(imgs, -5)
        expect(lb.index.value).toBe(0)
    })

    it('open() mit leerem Set ist ein No-op', () => {
        const lb = useLightbox()
        lb.open([], 0)
        expect(lb.isOpen.value).toBe(false)
    })

    it('next()/prev() blaettern zyklisch', () => {
        const lb = useLightbox()
        lb.open(imgs, 2)
        lb.next()
        expect(lb.index.value).toBe(0) // wrap vorwaerts
        lb.prev()
        expect(lb.index.value).toBe(2) // wrap rueckwaerts
        lb.next()
        lb.next()
        expect(lb.index.value).toBe(1)
    })

    it('next()/prev() sind No-op bei einem einzelnen Bild', () => {
        const lb = useLightbox()
        lb.open([imgs[0]], 0)
        lb.next()
        expect(lb.index.value).toBe(0)
        lb.prev()
        expect(lb.index.value).toBe(0)
        expect(lb.hasMultiple.value).toBe(false)
    })

    it('close() schliesst das Overlay', () => {
        const lb = useLightbox()
        lb.open(imgs, 0)
        expect(lb.isOpen.value).toBe(true)
        lb.close()
        expect(lb.isOpen.value).toBe(false)
    })
})
