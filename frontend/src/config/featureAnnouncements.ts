export interface AnnouncementContext {
  hasGoeConnection: boolean
  isPremium: boolean
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
    key: 'brutto-netto-v1',
    expiresAt: '2026-05-19',
    releasedAt: '2026-04-28',
    titleKey: 'announcements.brutto_netto_v1_title',
    bodyKey: 'announcements.brutto_netto_v1_body',
  },
  {
    key: 'trip-detection-beta-v1',
    expiresAt: '2026-05-16',
    titleKey: 'announcements.trip_detection_beta_v1_title',
    bodyKey: 'announcements.trip_detection_beta_v1_body',
    ctaLabelKey: 'announcements.trip_detection_beta_v1_cta',
    ctaRoute: '/',
    condition: ctx => ctx.isPremium,
  },
  {
    key: 'net-capacity-v1',
    expiresAt: '2026-05-12',
    releasedAt: '2026-04-21',
    titleKey: 'announcements.net_capacity_v1_title',
    bodyKey: 'announcements.net_capacity_v1_body',
  },
  {
    key: 'kwh-primary-formula-v1',
    expiresAt: '2026-05-10',
    releasedAt: '2026-04-19',
    titleKey: 'announcements.kwh_primary_formula_v1_title',
    bodyKey: 'announcements.kwh_primary_formula_v1_body',
    ctaLabelKey: 'announcements.consumption_methodology_v1_cta',
    ctaRoute: '/consumption-methodology',
    credit: 'Danke an Mike_L',
  },
  {
    key: 'spritmonitor-raw-refresh-v1',
    expiresAt: '2026-05-10',
    releasedAt: '2026-04-19',
    titleKey: 'announcements.spritmonitor_raw_refresh_v1_title',
    bodyKey: 'announcements.spritmonitor_raw_refresh_v1_body',
  },
  {
    key: 'soh-autodetect-v1',
    expiresAt: '2026-05-08',
    releasedAt: '2026-04-17',
    titleKey: 'announcements.soh_autodetect_v1_title',
    bodyKey: 'announcements.soh_autodetect_v1_body',
    ctaLabelKey: 'announcements.soh_autodetect_v1_cta',
    ctaRoute: '/cars',
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
