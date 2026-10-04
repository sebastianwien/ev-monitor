/**
 * Tastaturführung im Wizard: Enter in einem Zahlenfeld springt ins nächste. Zählt nur Felder,
 * die man gerade bedienen kann - nicht versteckt (sr-only), nicht gesperrt, nicht in einem
 * zugeklappten Collapse (inert). jsdom schreibt inert als Attribut "false", wenn es offen ist.
 */
const usable = (el: HTMLInputElement) =>
  !el.disabled && !el.classList.contains('sr-only') && !el.closest('[inert]:not([inert="false"])')

/** Felder mit data-enter-skip sind Ausweichfelder (Preis je kWh neben dem Gesamtbetrag): Enter springt über sie hinweg */
export function nextField(container: HTMLElement, current: HTMLElement): HTMLInputElement | null {
  const fields = [...container.querySelectorAll<HTMLInputElement>('input[type="number"]')].filter(usable)
  const i = fields.indexOf(current as HTMLInputElement)
  return i >= 0 ? fields.slice(i + 1).find(f => !f.hasAttribute('data-enter-skip')) ?? null : null
}
