export interface AnnouncementContext {
  hasGoeConnection: boolean
}

export interface FeatureAnnouncement {
  key: string
  expiresAt: string   // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  titleKey: string    // i18n key
  bodyKey: string     // i18n key
  ctaLabelKey?: string // i18n key, optional
  ctaRoute?: string
  condition?: (ctx: AnnouncementContext) => boolean
}

export const featureAnnouncements: FeatureAnnouncement[] = [
  {
    key: 'price-suggestion-v1',
    expiresAt: '2026-04-12',
    titleKey: 'announcements.price_suggestion_v1_title',
    bodyKey: 'announcements.price_suggestion_v1_body',
  },
  {
    key: 'consumption-normalization-v1',
    expiresAt: '2026-04-12',
    titleKey: 'announcements.consumption_normalization_v1_title',
    bodyKey: 'announcements.consumption_normalization_v1_body',
  },
  {
    key: 'battery-soh-heatpump-v1',
    expiresAt: '2026-04-12',
    titleKey: 'announcements.battery_soh_heatpump_v1_title',
    bodyKey: 'announcements.battery_soh_heatpump_v1_body',
    ctaLabelKey: 'announcements.battery_soh_heatpump_v1_cta',
    ctaRoute: '/cars',
  },

  {
    key: 'goe-statistics-fix-v1',
    expiresAt: '2026-04-17',
    titleKey: 'announcements.goe_statistics_fix_v1_title',
    bodyKey: 'announcements.goe_statistics_fix_v1_body',
    condition: (ctx) => ctx.hasGoeConnection,
  },
  {
    key: 'car-reassignment-v1',
    expiresAt: '2026-05-18',
    titleKey: 'announcements.car_reassignment_v1_title',
    bodyKey: 'announcements.car_reassignment_v1_body',
  },
  {
    key: 'autosync-premium-v1',
    expiresAt: '2026-05-09',
    titleKey: 'announcements.autosync_premium_v1_title',
    bodyKey: 'announcements.autosync_premium_v1_body',
    ctaLabelKey: 'announcements.autosync_premium_v1_cta',
    ctaRoute: '/upgrade',
  },
]
