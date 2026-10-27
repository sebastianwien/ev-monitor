/**
 * Vorschau der neuen Oberfläche auf einer eigenen Adresse (vorschau.ev-monitor.net), gleiches
 * Backend und gleiche Daten. Wer eingeladen ist, trägt im JWT den Claim "preview"; alle anderen
 * schickt die Vorschau nach dem Login auf die Hauptadresse. Login und localStorage sind je
 * Adresse getrennt, der Rückweg "Zur gewohnten App" steht auf der Seite.
 */
export const PREVIEW_HOST_PREFIX = 'vorschau.'

export function isPreviewHost(hostname = typeof window === 'undefined' ? '' : window.location.hostname): boolean {
  return hostname.startsWith(PREVIEW_HOST_PREFIX)
}

/** Hauptadresse zur Vorschau-Adresse, z. B. https://ev-monitor.net. Auf der Hauptadresse selbst: eigener Ursprung. */
export function mainAppOrigin(hostname = window.location.hostname, protocol = window.location.protocol): string {
  const host = isPreviewHost(hostname) ? hostname.slice(PREVIEW_HOST_PREFIX.length) : hostname
  return `${protocol}//${host}`
}

/**
 * Wohin die Vorschau einen Besucher schickt: null heißt bleiben. Nur Eingeloggte ohne Einladung
 * gehen auf dieselbe Seite der Hauptadresse; die Anmeldeseite bleibt erreichbar.
 */
export function previewRedirect(input: { hostname: string; protocol: string; authenticated: boolean; preview: boolean | undefined; path: string }): string | null {
  if (!isPreviewHost(input.hostname) || !input.authenticated || input.preview) return null
  return `${mainAppOrigin(input.hostname, input.protocol)}${input.path}`
}
