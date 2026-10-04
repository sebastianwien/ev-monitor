import type { KnownPlace } from '../../composables/useKnownPlaces'
import type { StationMatch } from '../../composables/useNearbyStations'
import type { PlaceKind } from './wizardLogic'

/** Was ein bekannter Ort im Formular ist: Säule, öffentlicher Anbieter ohne Säule oder privat (Zuhause). */
export function placeKindOf(p: KnownPlace): PlaceKind {
  if (p.site) return 'site'
  return p.isPublic ? 'other' : 'home'
}

/**
 * Titel eines bekannten Orts: Säulenname, sonst Ortsteil, sonst Anbieter, sonst "Privat". Nie ein
 * nacktes "Ort" - damit könnte niemand etwas anfangen.
 */
export function knownPlaceTitle(p: KnownPlace, t: (key: string) => string): string {
  if (p.site) return p.site.name
  if (p.placeName) return p.placeName
  if (p.isPublic && p.cpoName) return p.cpoName
  return t('logwizard.known_private')
}

const nameKey = (s: string) => s.trim().toLowerCase()

/** Ob diese Register-Säule schon als bekannter Ort steht - dann nicht ein zweites Mal in der Liste. */
export function stationIsKnown(s: StationMatch, known: KnownPlace[]): boolean {
  return known.some(p => p.site && p.site.geohash === s.geohash && nameKey(p.site.name) === nameKey(s.name))
}
