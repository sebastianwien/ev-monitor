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
