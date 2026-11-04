// Probe-CI "v1" (Marketing-Entwurf) als Skin über dem App-CI, umschaltbar per URL: ?ci=v1 an, ?ci=off aus.
// Die Wahl bleibt im Browser gemerkt. Der Skin gilt nur auf den Routen unten; die Farben stehen in styles/ci-v1.css.
import type { LocationQuery, RouteRecordNameGeneric } from 'vue-router'

export const CI_SKIN_KEY = 'ci-skin'
// v1: Marketing-Entwurf; edge1: App-CI mit 1 px statt 2 px Schatten; edge2: Schatten gray-400 statt gray-500
const SKINS = ['v1', 'edge1', 'edge2'] as const
export type CiSkin = typeof SKINS[number]

const SKIN_ROUTES = new Set<RouteRecordNameGeneric>(['story'])

const isSkin = (v: unknown): v is CiSkin => SKINS.includes(v as CiSkin)

export function resolveCiSkin(query: unknown, stored: string | null): CiSkin | null {
  if (isSkin(query)) return query
  if (query === 'off') return null
  return isSkin(stored) ? stored : null
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
  if (isSkin(param) || param === 'off') writeStored(skin)
  const root = document.documentElement
  if (skin && SKIN_ROUTES.has(to.name)) root.dataset.ci = skin
  else delete root.dataset.ci
}
