/**
 * Where to continue after a login that leaves the page (Google redirect flow from the gate).
 * Only same-origin paths are returned, so a manipulated value cannot become an open redirect.
 */
const KEY = 'postLoginRedirect'

export function rememberReturnPath(path: string): void {
  try { sessionStorage.setItem(KEY, path) } catch { /* storage blocked: falls back to the dashboard */ }
}

export function takeReturnPath(): string | null {
  try {
    const path = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return path && /^\/(?![/\\])/.test(path) ? path : null
  } catch {
    return null
  }
}
