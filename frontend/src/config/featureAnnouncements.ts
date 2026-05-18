export interface AnnouncementContext {
  hasGoeConnection: boolean
  isPremium: boolean
  isAutoSyncLive: boolean
  hasTeslaConnection: boolean
}

export interface FeatureAnnouncement {
  key: string
  expiresAt: string    // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  releasedAt?: string  // ISO date string - nur User die VOR diesem Datum registriert waren sehen die Ankündigung
  titleKey: string     // i18n key
  bodyKey: string      // i18n key
  ctaLabelKey?: string // i18n key, optional
  ctaRoute?: string
  credit?: string      // optional plain text credit, rendered small + italic
  condition?: (ctx: AnnouncementContext) => boolean
}

export const featureAnnouncements: FeatureAnnouncement[] = [
  {
    key: 'blog-launch-v1',
    expiresAt: '2026-06-18',
    releasedAt: '2026-05-18',
    titleKey: 'announcements.blog_launch_v1_title',
    bodyKey: 'announcements.blog_launch_v1_body',
    ctaLabelKey: 'announcements.blog_launch_v1_cta',
    ctaRoute: '/blog',
  },
  {
    key: 'autosync-live-launch-v1',
    expiresAt: '2026-06-15',
    releasedAt: '2026-05-12',
    titleKey: 'announcements.autosync_live_launch_v1_title',
    bodyKey: 'announcements.autosync_live_launch_v1_body',
    ctaLabelKey: 'announcements.autosync_live_launch_v1_cta',
    ctaRoute: '/upgrade',
    condition: ctx => ctx.isPremium && !ctx.isAutoSyncLive,
  },
  {
    key: 'tesla-telemetry-rollout-v1',
    expiresAt: '2026-05-24',
    releasedAt: '2026-05-03',
    titleKey: 'announcements.tesla_telemetry_rollout_v1_title',
    bodyKey: 'announcements.tesla_telemetry_rollout_v1_body',
    ctaLabelKey: 'announcements.tesla_telemetry_rollout_v1_cta',
    ctaRoute: '/imports',
    condition: ctx => ctx.hasTeslaConnection,
  },
  {
    key: 'negative-prices-v1',
    expiresAt: '2026-05-24',
    releasedAt: '2026-05-02',
    titleKey: 'announcements.negative_prices_v1_title',
    bodyKey: 'announcements.negative_prices_v1_body',
  },
  {
    key: 'brutto-netto-v1',
    expiresAt: '2026-05-19',
    releasedAt: '2026-04-28',
    titleKey: 'announcements.brutto_netto_v1_title',
    bodyKey: 'announcements.brutto_netto_v1_body',
  },
  {
    key: 'car-reassignment-v1',
    expiresAt: '2026-05-18',
    releasedAt: '2026-04-14',
    titleKey: 'announcements.car_reassignment_v1_title',
    bodyKey: 'announcements.car_reassignment_v1_body',
  },
]
