// Probe-CI "v1" (Marketing-Entwurf) als Skin über dem App-CI, umschaltbar per URL: ?ci=v1 an, ?ci=off aus.
// Die Wahl bleibt im Browser gemerkt. Der Skin gilt nur auf den Routen unten; die Farben stehen in styles/ci-v1.css.
import { ref } from 'vue'
import type { LocationQuery, RouteRecordNameGeneric } from 'vue-router'

export const CI_SKIN_KEY = 'ci-skin'
// v1: Marketing-Entwurf; k1 bis k3: Vergleich leichterer Kanten im App-CI (styles/ci-edge-variants.css)
const SKINS = ['v1', 'k1', 'k2', 'k3'] as const
export type CiSkin = typeof SKINS[number]

const SKIN_ROUTES = new Set<RouteRecordNameGeneric>(['story', 'logs'])

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

type SkinRoute = { name?: RouteRecordNameGeneric; query: LocationQuery }

/** Die gewählte Skin, unabhängig von der Route (für den Admin-Umschalter). */
export const ciSkin = ref<CiSkin | null>(null)

export const isSkinRoute = (name: RouteRecordNameGeneric | undefined) => SKIN_ROUTES.has(name)

function paint(skin: CiSkin | null, to: SkinRoute) {
  ciSkin.value = skin
  const root = document.documentElement
  if (skin && isSkinRoute(to.name)) root.dataset.ci = skin
  else delete root.dataset.ci
}

export function applyCiSkin(to: SkinRoute) {
  const param = to.query.ci
  const skin = resolveCiSkin(param, readStored())
  if (isSkin(param) || param === 'off') writeStored(skin)
  paint(skin, to)
}

/** Umschalten ohne URL-Parameter (Admin-Umschalter auf der Vorschau). */
export function setCiSkin(skin: CiSkin | null, to: SkinRoute) {
  writeStored(skin)
  paint(skin, to)
}
