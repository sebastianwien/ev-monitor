/**
 * Die zwei Tabs des Fuhrparks: die Autos und die Karten, mit denen sie geladen werden.
 * Eine Quelle, damit /cars und /charging-providers nie auseinanderlaufen.
 */
export const CAR_TABS = [
  { to: '/cars', labelKey: 'cars.title' },
  { to: '/charging-providers', labelKey: 'settings.tariff_title' },
] as const
