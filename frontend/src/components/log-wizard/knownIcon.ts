import { BoltIcon, HomeIcon, MapPinIcon } from '@heroicons/vue/24/outline'
import type { KnownPlace } from '../../composables/useKnownPlaces'
import { placeKindOf } from './knownPlace'

/** Säule: Blitz, privater Ort: Haus, öffentlicher Ort ohne Säule: Pin. */
export function knownIcon(p: KnownPlace) {
  switch (placeKindOf(p)) {
    case 'site': return BoltIcon
    case 'home': return HomeIcon
    default: return MapPinIcon
  }
}
