export interface AnnouncementContext {
  hasGoeConnection: boolean
  isPremium: boolean
  isAutoSyncLive: boolean
  hasTeslaConnection: boolean
  teslaLocationScopeGranted: boolean
  /** Bewertbare, noch unbewertete eigene Autos (Fahrzeugbewertung). 0 bis geladen. */
  ratableUnratedCars: number
}

export interface FeatureAnnouncement {
  key: string
  expiresAt: string    // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  releasedAt?: string  // ISO date string - nur User die VOR diesem Datum registriert waren sehen die Ankündigung
  titleKey: string     // i18n key
  bodyKey: string      // i18n key
  ctaLabelKey?: string // i18n key, optional
  /** Platzhalter wie {ratingCarId} werden aus den Ankündigungs-Parametern ersetzt. */
  ctaRoute?: string
  /** Runs instead of ctaRoute navigation when set - for a CTA that needs to do something
   *  (call an API, start a redirect) rather than just navigate to a page. Must throw on
   *  failure: the modal only dismisses the announcement once this resolves, so a throw
   *  keeps it open and shows an error instead of silently losing the announcement. */
  ctaAction?: () => void | Promise<void>
  credit?: string      // optional plain text credit, rendered small + italic
  condition?: (ctx: AnnouncementContext) => boolean
}

export const featureAnnouncements: FeatureAnnouncement[] = [
  // Fahrzeugbewertung: nur Bestandsnutzer mit bewertbarem, unbewertetem Auto, Release 11.10.2026
  {
    key: 'vehicle_rating_v1',
    releasedAt: '2026-10-11',
    expiresAt: '2026-11-10',
    titleKey: 'announcements.vehicle_rating_v1_title',
    bodyKey: 'announcements.vehicle_rating_v1_body',
    ctaLabelKey: 'announcements.vehicle_rating_v1_cta',
    ctaRoute: '/cars/{ratingCarId}/bewerten?src=announcement',
    // Bei mehreren Autos verlinkt {ratingCarId} das Hauptfahrzeug
    condition: ctx => ctx.ratableUnratedCars >= 1,
  },
  {
    key: 'power_curve_share_v1',
    expiresAt: '2026-10-15',
    titleKey: 'announcements.power_curve_share_v1_title',
    bodyKey: 'announcements.power_curve_share_v1_body',
    ctaLabelKey: 'announcements.power_curve_share_v1_cta',
    ctaRoute: '/logs',
    // Ladekurven brauchen Tesla-Telemetrie (nur dort entsteht eine) und das
    // Analytics-Entitlement - ohne beides laeuft der Teilen-Button ins Leere.
    condition: (ctx) => ctx.hasTeslaConnection && ctx.isAutoSyncLive,
  },
  {
    key: 'log_wizard_voice_v1',
    releasedAt: '2026-10-05', // nur Bestandsuser - Neue kennen das alte Formular nicht, sie landen direkt im Assistenten
    expiresAt: '2026-11-15',
    titleKey: 'announcements.log_wizard_voice_v1_title',
    bodyKey: 'announcements.log_wizard_voice_v1_body',
    ctaLabelKey: 'announcements.log_wizard_voice_v1_cta',
    ctaRoute: '/erfassen',
  },
]
