/**
 * Pure helpers for the Tessie import VIN -> car mapping step.
 *
 * Kept outside the Vue component so the gating logic that decides whether the
 * "Import starten" button is enabled has its own deterministic Vitest coverage,
 * independent of @vue/test-utils.
 */

/** True when every selected VIN has a non-empty car id assigned. */
export function isMappingComplete(
  selectedVins: ReadonlyArray<string>,
  mapping: Readonly<Record<string, string | undefined>>,
): boolean {
  if (selectedVins.length === 0) return false;
  return selectedVins.every((vin) => {
    const carId = mapping[vin];
    return typeof carId === 'string' && carId.length > 0;
  });
}
