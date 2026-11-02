/**
 * Arbeit, die warten kann (Vorladen), erst wenn der Browser nichts zu tun hat. WebKit (iOS, auch die
 * App-WebView) kennt requestIdleCallback nicht: dort kurz nach dem aktuellen Paint.
 */
export function whenIdle(fn: () => void, timeoutMs = 2000): void {
  const ric = (globalThis as { requestIdleCallback?: (cb: () => void, o?: { timeout: number }) => number }).requestIdleCallback
  if (typeof ric === 'function') ric.call(globalThis, fn, { timeout: timeoutMs })
  else setTimeout(fn, 300)
}
