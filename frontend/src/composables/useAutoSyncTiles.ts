import type { Car } from '../api/carService'
import { autoSyncProviderFor } from './useCarAutoSyncProvider'

export type AutoSyncTileState = 'available' | 'active' | 'locked' | 'unavailable'

/**
 * Zustand einer Auto-Kachel im AutoSync-Picker.
 *
 * AutoSync-Slots: ein Abo deckt ein Fahrzeug. `slots` ist die Anzahl aktiver AutoSync-Abos,
 * `connectedCarIds` die Autos mit laufender Smartcar-Verbindung. Ein bereits verbundenes Auto ist
 * `active`, weitere Autos sind `available`, solange ein Platz frei ist, sonst `locked`.
 * Bei einem Auto und einem Abo greift die Slot-Logik damit nie sichtbar.
 */
export function autoSyncTileState(
    car: Pick<Car, 'id' | 'brand'>,
    connectedCarIds: readonly string[],
    slots: number,
): AutoSyncTileState {
    if (autoSyncProviderFor(car) === 'NONE') return 'unavailable'
    if (connectedCarIds.includes(car.id)) return 'active'
    if (connectedCarIds.length >= Math.max(1, slots)) return 'locked'
    return 'available'
}
