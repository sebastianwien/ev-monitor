export function useHaptic() {
  function haptic(duration = 10) {
    if (navigator.vibrate?.(duration)) return

    // iOS fallback: kurzer Sinuston bewegt den Lautsprecher minimal
    try {
      const ctx = new AudioContext()
      const oscillator = ctx.createOscillator()
      const gain = ctx.createGain()
      oscillator.connect(gain)
      gain.connect(ctx.destination)
      oscillator.frequency.value = 440
      gain.gain.value = 0.015
      oscillator.start()
      oscillator.stop(ctx.currentTime + 0.012)
      oscillator.onended = () => ctx.close()
    } catch {
      // kein Audio-Support — still ignorieren
    }
  }
  return { haptic }
}
