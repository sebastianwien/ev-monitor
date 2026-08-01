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
    key: 'tesla_telemetry_recovery_v1',
    releasedAt: '2026-07-16', // nur Bestandsuser, die den Ausfall erlebt haben
    expiresAt: '2026-07-31',
    titleKey: 'announcements.tesla_telemetry_recovery_v1_title',
    bodyKey: 'announcements.tesla_telemetry_recovery_v1_body',
    condition: (ctx) => ctx.hasTeslaConnection, // nur Tesla-Nutzer
  },
  {
    key: 'tesla_location_reconnect_v1',
    releasedAt: '2026-07-28', // nur Bestandsuser - Neuverbindungen bringen den Scope schon mit
    expiresAt: '2026-09-30',
    titleKey: 'announcements.tesla_location_reconnect_v1_title',
    bodyKey: 'announcements.tesla_location_reconnect_v1_body',
    ctaLabelKey: 'announcements.tesla_location_reconnect_v1_cta',
    ctaRoute: '/imports', // dort sitzt TeslaFleetIntegration; Tesla-Fahrer landen automatisch im Tesla-Tab

    condition: (ctx) => ctx.hasTeslaConnection, // nur Tesla-Nutzer
  },
  {
    key: 'survey_ev_pain_points_2026',
    expiresAt: '2026-09-30',
    titleKey: 'announcements.survey_ev_pain_points_2026_title',
    bodyKey: 'announcements.survey_ev_pain_points_2026_body',
    ctaLabelKey: 'announcements.survey_ev_pain_points_2026_cta',
    ctaRoute: '/umfrage/ev-pain-points-2026',
  },
]
