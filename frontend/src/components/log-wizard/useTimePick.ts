const pad = (n: number) => String(n).padStart(2, '0')
const toLocal = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
export const nowLocal = () => toLocal(new Date())
/** Nativen Datum/Zeit-Picker öffnen. Nur direkt im Click-Handler aufrufen, showPicker() braucht die Tap-Geste;
 *  das Feld darf nicht display:none sein (sr-only), sonst öffnet Safari nichts. */
export function openNativePicker(el: HTMLInputElement | null) {
  if (!el) return
  try { el.showPicker() } catch { el.focus(); el.click() }
}
