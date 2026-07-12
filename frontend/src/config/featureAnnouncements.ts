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
    key: 'feature-survey-v1',
    expiresAt: '2026-07-15',
    titleKey: 'announcements.feature_survey_v1_title',
    bodyKey: 'announcements.feature_survey_v1_body',
    ctaLabelKey: 'announcements.feature_survey_v1_cta',
    ctaRoute: 'https://whenly.de/p/vPbGD6x0uZGdSPWpgPQIzUaD5H4xEgyZ',
  },
]
