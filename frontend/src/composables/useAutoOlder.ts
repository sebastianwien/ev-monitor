import { ref, watch, type Ref } from 'vue'

/**
 * Logbuch: ist das Ende der Liste sichtbar, lädt der Vormonat nach, solange jeder Schritt Einträge
 * bringt. Bringt einer nichts (vor dem ersten Eintrag oder ein leerer Monat), hört das Nachladen auf;
 * dann lädt nur noch der Knopf (`older`). `reset` beim Autowechsel.
 */
export function useAutoOlder(opts: {
  loading: Ref<boolean>
  /** Anzahl geladener Einträge, um zu sehen, ob ein Schritt etwas gebracht hat. */
  count: () => number
  /** Nachladen überhaupt sinnvoll (es gibt Einträge). */
  enabled: () => boolean
  load: () => void
}) {
  const auto = ref(true)
  let visible = false
  let before = -1

  function older() {
    if (opts.loading.value) return
    before = opts.count()
    opts.load()
  }
  function setVisible(v: boolean) {
    visible = v
    if (v && auto.value && opts.enabled()) older()
  }
  watch(opts.loading, (busy) => {
    if (busy) return
    if (before >= 0) {
      auto.value = opts.count() > before
      before = -1
    }
    if (auto.value && visible && opts.enabled()) older()
  }, { flush: 'sync' }) // jeden Wechsel sehen, auch zwei im selben Tick
  function reset() {
    auto.value = true
    before = -1
  }
  return { auto, older, setVisible, reset }
}
