import { ref, onUnmounted } from 'vue'

/**
 * Reaktiver Mobile-Breakpoint (<768px, deckungsgleich mit Tailwind `md`).
 * Aktualisiert sich bei Resize/Rotate und raeumt seinen Listener selbst ab.
 */
export function useIsMobile(query = '(max-width: 767px)') {
  const mql = typeof window !== 'undefined' ? window.matchMedia(query) : null
  const isMobile = ref(mql?.matches ?? false)
  const onChange = (e: MediaQueryListEvent) => { isMobile.value = e.matches }
  mql?.addEventListener('change', onChange)
  onUnmounted(() => mql?.removeEventListener('change', onChange))
  return isMobile
}
