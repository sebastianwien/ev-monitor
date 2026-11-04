// Probe-CI "v1" (Marketing-Entwurf) als Skin über dem App-CI, umschaltbar per URL: ?ci=v1 an, ?ci=off aus.
// Die Wahl bleibt im Browser gemerkt. Der Skin gilt nur auf den Routen unten; die Farben stehen in styles/ci-v1.css.
import type { LocationQuery, RouteRecordNameGeneric } from 'vue-router'

export const CI_SKIN_KEY = 'ci-skin'
export type CiSkin = 'v1'

const SKIN_ROUTES = new Set<RouteRecordNameGeneric>(['story'])

export function resolveCiSkin(query: unknown, stored: string | null): CiSkin | null {
  if (query === 'v1') return 'v1'
  if (query === 'off') return null
  return stored === 'v1' ? 'v1' : null
}

function readStored(): string | null {
  try { return localStorage.getItem(CI_SKIN_KEY) } catch { return null }
}

function writeStored(skin: CiSkin | null) {
  try {
    if (skin) localStorage.setItem(CI_SKIN_KEY, skin)
    else localStorage.removeItem(CI_SKIN_KEY)
  } catch { /* privater Modus: Skin gilt dann nur für diesen Aufruf */ }
}

export function applyCiSkin(to: { name?: RouteRecordNameGeneric; query: LocationQuery }) {
  const param = to.query.ci
  const skin = resolveCiSkin(param, readStored())
  if (param === 'v1' || param === 'off') writeStored(skin)
  const root = document.documentElement
  if (skin && SKIN_ROUTES.has(to.name)) root.dataset.ci = skin
  else delete root.dataset.ci
}
