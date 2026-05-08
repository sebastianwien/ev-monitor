import type { TeslaPairingStatus } from '@/api/teslaFleetService'

/**
 * Decides whether to auto-fire enableTelemetry() right after loading pairing
 * status. Founders no longer have to find and click "Live-Sync aktivieren"
 * themselves once their virtual key is paired - the moment the page sees
 * keyPaired=true with config not yet pushed, the request goes out.
 *
 * Guarded against retry-storms: skipped while an error is pending so a Tesla
 * 4xx/5xx doesn't get hammered every reload.
 */
export function shouldAutoEnableTelemetry(
    status: TeslaPairingStatus | null,
    pendingError: string | null,
): boolean {
    if (!status) return false
    if (pendingError) return false
    if (status.dataSource === 'TELEMETRY') return false
    if (!status.keyPaired) return false
    if (status.telemetryConfigPushed) return false
    return true
}
