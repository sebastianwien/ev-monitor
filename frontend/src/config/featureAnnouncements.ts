export interface FeatureAnnouncement {
  key: string
  expiresAt: string   // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  titleKey: string    // i18n key
  bodyKey: string     // i18n key
  ctaLabelKey?: string // i18n key, optional
  ctaRoute?: string
}

export const featureAnnouncements: FeatureAnnouncement[] = [
  {
    key: 'charging-provider-v1',
    expiresAt: '2026-04-04',
    titleKey: 'announcements.charging_provider_v1_title',
    bodyKey: 'announcements.charging_provider_v1_body',
    ctaLabelKey: 'announcements.charging_provider_v1_cta',
    ctaRoute: '/settings',
  },
  {
    key: 'consumption-normalization-v1',
    expiresAt: '2026-05-10',
    titleKey: 'announcements.consumption_normalization_v1_title',
    bodyKey: 'announcements.consumption_normalization_v1_body',
  },
  {
    key: 'battery-soh-heatpump-v1',
    expiresAt: '2026-05-15',
    titleKey: 'announcements.battery_soh_heatpump_v1_title',
    bodyKey: 'announcements.battery_soh_heatpump_v1_body',
    ctaLabelKey: 'announcements.battery_soh_heatpump_v1_cta',
    ctaRoute: '/cars',
  },
]
