/**
 * Wer schon zweimal erfolgreich gesprochen hat, braucht Beispielsatz und Knopftext in Schritt 1 nicht mehr:
 * dann bleibt nur das Mikrofon-Symbol im Footer. Zähler nur im Browser, ohne Speicher (privater Modus) bleibt alles erklärt.
 */
const USES_KEY = 'voicelog_uses'
const FAMILIAR_AFTER = 2

const uses = () => {
  try { return Math.max(0, parseInt(localStorage.getItem(USES_KEY) ?? '0', 10) || 0) } catch { return 0 }
}

export const voiceFamiliar = () => uses() >= FAMILIAR_AFTER

export function recordVoiceUse() {
  try { localStorage.setItem(USES_KEY, String(uses() + 1)) } catch { /* privater Modus */ }
}

/** Wer die Beispiel-Blase wegklickt, sieht sie nicht wieder. Knopftext bleibt, bis zwei Aufnahmen geklappt haben. */
const EXAMPLE_DISMISSED_KEY = 'voicelog_example_dismissed'

export const voiceExampleDismissed = () => {
  try { return localStorage.getItem(EXAMPLE_DISMISSED_KEY) === 'true' } catch { return false }
}

export function dismissVoiceExample() {
  try { localStorage.setItem(EXAMPLE_DISMISSED_KEY, 'true') } catch { /* privater Modus: dann nur bis zum nächsten Öffnen */ }
}
