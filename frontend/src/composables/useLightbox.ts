import { ref, computed, readonly } from 'vue'

export interface LightboxImage {
    src: string
    alt: string
}

// Modul-weiter Singleton-State: es gibt genau ein Lightbox-Overlay auf der Seite,
// jede Bildergalerie ruft dieselbe open()-Funktion mit ihrem eigenen Bild-Set auf.
const isOpen = ref(false)
const images = ref<LightboxImage[]>([])
const index = ref(0)

/**
 * Zustand + Navigation fuer das Bild-Lightbox-Overlay. Logik ausgelagert, damit sie
 * isoliert testbar ist (Komponente rendert nur diesen State).
 */
export function useLightbox() {
    /** Oeffnet die Lightbox mit einem Bild-Set ab startIndex (geklemmt auf gueltigen Bereich). */
    function open(imgs: LightboxImage[], startIndex = 0) {
        if (!imgs.length) return
        images.value = imgs
        index.value = Math.min(Math.max(startIndex, 0), imgs.length - 1)
        isOpen.value = true
    }

    function close() {
        isOpen.value = false
    }

    /** Naechstes Bild, zyklisch. No-op bei < 2 Bildern. */
    function next() {
        if (images.value.length < 2) return
        index.value = (index.value + 1) % images.value.length
    }

    /** Vorheriges Bild, zyklisch. No-op bei < 2 Bildern. */
    function prev() {
        if (images.value.length < 2) return
        index.value = (index.value - 1 + images.value.length) % images.value.length
    }

    const current = computed<LightboxImage | null>(() => images.value[index.value] ?? null)
    const hasMultiple = computed(() => images.value.length > 1)

    return {
        isOpen: readonly(isOpen),
        images: readonly(images),
        index: readonly(index),
        current,
        hasMultiple,
        open,
        close,
        next,
        prev,
    }
}
