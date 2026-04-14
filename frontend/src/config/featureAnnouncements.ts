export interface AnnouncementContext {
  hasGoeConnection: boolean
}

export interface FeatureAnnouncement {
  key: string
  expiresAt: string    // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  releasedAt?: string  // ISO date string - nur User die VOR diesem Datum registriert waren sehen die Ankündigung
  titleKey: string     // i18n key
  bodyKey: string      // i18n key
  ctaLabelKey?: string // i18n key, optional
  ctaRoute?: string
  condition?: (ctx: AnnouncementContext) => boolean
}

export const featureAnnouncements: FeatureAnnouncement[] = [
  {
    key: 'goe-statistics-fix-v1',
    expiresAt: '2026-04-17',
    releasedAt: '2026-04-14',
    titleKey: 'announcements.goe_statistics_fix_v1_title',
    bodyKey: 'announcements.goe_statistics_fix_v1_body',
    condition: (ctx) => ctx.hasGoeConnection,
  },
  {
    key: 'car-reassignment-v1',
    expiresAt: '2026-05-18',
    releasedAt: '2026-04-14',
    titleKey: 'announcements.car_reassignment_v1_title',
    bodyKey: 'announcements.car_reassignment_v1_body',
  },
  {
    key: 'autosync-premium-v1',
    expiresAt: '2026-05-09',
    releasedAt: '2026-04-14',
    titleKey: 'announcements.autosync_premium_v1_title',
    bodyKey: 'announcements.autosync_premium_v1_body',
    ctaLabelKey: 'announcements.autosync_premium_v1_cta',
    ctaRoute: '/upgrade',
  },
  {
    key: 'charging-cards-portfolio-v1',
    expiresAt: '2026-05-02',
    releasedAt: '2026-04-14',
    titleKey: 'announcements.charging_cards_portfolio_v1_title',
    bodyKey: 'announcements.charging_cards_portfolio_v1_body',
    ctaLabelKey: 'announcements.charging_cards_portfolio_v1_cta',
    ctaRoute: '/settings',
  },
]
