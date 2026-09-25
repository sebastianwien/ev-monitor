import type { TeslaPairingStatus } from '@/api/teslaFleetService'

/**
 * True when the Tesla account is connected but no vehicle (VIN) was found at OAuth time -
 * typically because the car was not yet in the owner's Tesla account. Pairing and telemetry
 * cannot work then; the only fix is to connect the Tesla account again.
 */
export function isTeslaVehicleMissing(status: TeslaPairingStatus | null): boolean {
    return !!status && !status.vin
}
