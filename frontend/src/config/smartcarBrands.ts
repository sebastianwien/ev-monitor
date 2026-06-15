/**
 * Single source of truth for the user-facing list of vehicle brands AutoSync
 * can serve via Smartcar (EU, 2026), in display order and casing.
 *
 * Tesla is intentionally NOT in this list - it connects through Fleet Telemetry,
 * not Smartcar, and is shown as its own item wherever brands are listed.
 *
 * Consumers:
 * - SmartcarIntegration.vue        (brand chips in the connect flow)
 * - UpgradeView.vue                (brand chips on the AutoSync plan)
 * - useCarAutoSyncProvider.ts      (derives its lookup set from this list)
 *
 * Keep this in sync with what Smartcar actually delivers - it is the authoritative
 * "is my brand supported?" list users see before buying. Brands NOT here (e.g.
 * XPeng, Lucid, Rivian) are not covered by AutoSync.
 */
export const SMARTCAR_BRANDS = [
    'BMW', 'MINI', 'VW', 'Mercedes', 'Audi', 'Porsche', 'Skoda', 'SEAT', 'CUPRA', 'Opel',
    'Hyundai', 'Kia', 'Volvo', 'Polestar', 'Renault', 'Dacia', 'Nissan', 'Ford',
    'Fiat', 'Alfa Romeo', 'Peugeot', 'Citroën', 'Mazda', 'MG', 'BYD',
    'Jaguar', 'Land Rover',
] as const

/** All brands covered by AutoSync, including Tesla (Fleet Telemetry). */
export const AUTOSYNC_BRANDS = [...SMARTCAR_BRANDS, 'Tesla'] as const
