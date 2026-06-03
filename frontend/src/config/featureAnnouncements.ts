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
    key: 'fixed-costs-dashboard-v1',
    expiresAt: '2026-06-16',
    titleKey: 'announcements.fixed_costs_dashboard_v1_title',
    bodyKey: 'announcements.fixed_costs_dashboard_v1_body',
    ctaLabelKey: 'announcements.fixed_costs_dashboard_v1_cta',
    ctaRoute: '/cars',
  },
  {
    key: 'feature-survey-v1',
    expiresAt: '2026-07-15',
    titleKey: 'announcements.feature_survey_v1_title',
    bodyKey: 'announcements.feature_survey_v1_body',
    ctaLabelKey: 'announcements.feature_survey_v1_cta',
    ctaRoute: 'https://whenly.de/p/vPbGD6x0uZGdSPWpgPQIzUaD5H4xEgyZ',
  },
  {
    key: 'spec-charging-efficiency-v1',
    expiresAt: '2026-06-21',
    releasedAt: '2026-05-21',
    titleKey: 'announcements.spec_charging_efficiency_v1_title',
    bodyKey: 'announcements.spec_charging_efficiency_v1_body',
    ctaLabelKey: 'announcements.spec_charging_efficiency_v1_cta',
    ctaRoute: '/blog/modellspezifische-ladeverluste',
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
]
