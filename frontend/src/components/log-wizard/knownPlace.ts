import type { KnownPlace } from '../../composables/useKnownPlaces'
import type { StationMatch } from '../../composables/useNearbyStations'
import type { PlaceKind } from './wizardLogic'

/** Höchstens so viele Kacheln unter "Zuletzt genutzt" - mehr unterscheidet niemand auf einen Blick. */
export const KNOWN_TILES = 5

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

/**
 * Bekannte Orte im Umkreis der Liste, ohne den Ort "hier" (der steht als eigene Zeile oben) und
 * ohne Säulen, die ohnehin in der Umkreisliste stehen.
 */
export function knownInRadius(places: KnownPlace[], radiusMeters: number, stations: StationMatch[]): KnownPlace[] {
  return places.filter(p => !p.here && p.distanceMeters != null && p.distanceMeters <= radiusMeters
    && !(p.site && stations.some(s => s.geohash === p.site!.geohash && nameKey(s.name) === nameKey(p.site!.name))))
}

/** Die Kachelreihe: Reihenfolge des Backends (hier zuerst, dann nach Häufigkeit), begrenzt. */
export function knownTiles(places: KnownPlace[]): KnownPlace[] {
  return places.slice(0, KNOWN_TILES)
}
