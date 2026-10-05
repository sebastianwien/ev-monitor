import type { RouteLocationNormalized } from 'vue-router'
import { analytics } from './analytics'

/**
 * Woher kommen Besucher der Bezahlseiten? `?from=` gewinnt (z. B. voice), sonst die vorige Route.
 * Die Herkunft bleibt für die Sitzung gemerkt, damit checkout_started und checkout_completed sie mitsenden.
 */
const KEY = 'upsell_source'
const PAGES: Record<string, 'supporter' | 'upgrade'> = { '/supporter': 'supporter', '/upgrade': 'upgrade' }

type Route = Pick<RouteLocationNormalized, 'path' | 'query' | 'name'>

export function upsellSourceOf(to: Route, from: Route): string {
  const raw = typeof to.query.from === 'string' ? to.query.from : typeof from.name === 'string' ? from.name : ''
  // Kommt aus der URL: nur harmlose Zeichen, kurz, damit Plausible keine Freitexte sammelt
  return raw.replace(/[^a-z0-9_-]/gi, '').slice(0, 40) || 'direct'
}

export function onUpsellNavigation(to: Route, from: Route): void {
  const page = PAGES[to.path]
  if (!page || to.path === from.path) return
  const source = upsellSourceOf(to, from)
  try { sessionStorage.setItem(KEY, source) } catch { /* privater Modus: dann ohne Herkunft beim Checkout */ }
  analytics.trackUpsellViewed(page, source)
}

export function currentUpsellSource(): string {
  try { return sessionStorage.getItem(KEY) || 'direct' } catch { return 'direct' }
}
