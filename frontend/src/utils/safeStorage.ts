/**
 * localStorage ohne Absturz: Safari privat, Policies und eingebettete Webviews werfen beim
 * Zugriff. Lesen liefert dann null, Schreiben wird still verworfen (Komfortwerte, kein Zustand).
 */
export function readLocal(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeLocal(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* Speicher gesperrt: Komfortwert geht verloren, Seite laeuft weiter */
  }
}
