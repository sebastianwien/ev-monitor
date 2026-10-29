import { describe, it, expect } from 'vitest'
import { nextTick, ref } from 'vue'
import { useAutoOlder } from '../useAutoOlder'

// Logbuch: sichtbares Ende lädt den Vormonat, solange das etwas bringt. Bringt ein Schritt nichts
// (vor dem ersten Eintrag), hört das Nachladen auf; der Knopf bleibt.
function setup() {
  const loading = ref(false)
  let count = 10
  const calls: number[] = []
  let gain = 5
  const auto = useAutoOlder({
    loading,
    count: () => count,
    enabled: () => true,
    load: () => { calls.push(count); loading.value = true },
  })
  const finish = async () => { count += gain; loading.value = false; await nextTick() }
  return { auto, loading, calls, finish, setGain: (g: number) => { gain = g } }
}

describe('useAutoOlder', () => {
  it('lädt nach, solange das Ende sichtbar ist und jeder Schritt Einträge bringt', async () => {
    const { auto, calls, finish } = setup()
    auto.setVisible(true)
    expect(calls).toHaveLength(1)
    await finish()
    expect(calls).toHaveLength(2)
    auto.setVisible(false)
    await finish()
    expect(calls).toHaveLength(2)
  })

  it('bringt ein Schritt nichts, hört es auf; erst ein Klick oder Neustart lädt weiter', async () => {
    const { auto, calls, finish, setGain } = setup()
    setGain(0)
    auto.setVisible(true)
    await finish()
    expect(auto.auto.value).toBe(false)
    auto.setVisible(true)
    expect(calls).toHaveLength(1)
    auto.older()
    expect(calls).toHaveLength(2)
    auto.reset()
    expect(auto.auto.value).toBe(true)
  })

  it('während eines Ladevorgangs kein zweiter, danach geht es weiter', async () => {
    const { auto, loading, calls } = setup()
    loading.value = true
    auto.setVisible(true)
    expect(calls).toHaveLength(0)
    loading.value = false
    await nextTick()
    expect(calls).toHaveLength(1)
  })
})
