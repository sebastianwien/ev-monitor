import teslaFleetService from '../api/teslaFleetService'

export interface AnnouncementContext {
  hasGoeConnection: boolean
  isPremium: boolean
  isAutoSyncLive: boolean
  hasTeslaConnection: boolean
  teslaLocationScopeGranted: boolean
}

export interface FeatureAnnouncement {
  key: string
  expiresAt: string    // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  releasedAt?: string  // ISO date string - nur User die VOR diesem Datum registriert waren sehen die Ankündigung
  titleKey: string     // i18n key
  bodyKey: string      // i18n key
  ctaLabelKey?: string // i18n key, optional
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
  {
    key: 'tesla_location_reconnect_v1',
    releasedAt: '2026-07-28', // nur Bestandsuser - Neuverbindungen bringen den Scope schon mit
    expiresAt: '2026-09-30',
    titleKey: 'announcements.tesla_location_reconnect_v1_title',
    bodyKey: 'announcements.tesla_location_reconnect_v1_body',
    ctaLabelKey: 'announcements.tesla_location_reconnect_v1_cta',
    // Startet den OAuth-Redirect direkt aus dem Modal statt nur zu /imports zu verlinken - der
    // User muesste dort sonst den Connect-Button erst noch selbst finden. Nutzt denselben
    // startReconnect wie TeslaFleetIntegration.vue/TeslaTelemetryPrompt.vue. Wirft bei jedem
    // Nicht-Erfolg (kein carId, 'not_configured', Netzwerkfehler) - das Modal dismissed nur bei
    // Erfolg, sonst bleibt die Ankuendigung offen und zeigt einen Fehlertext statt spurlos zu
    // verschwinden.
    ctaAction: async () => {
      const status = await teslaFleetService.getStatus()
      if (!status.carId) throw new Error('No Tesla car linked')
      const result = await teslaFleetService.startReconnect(status.carId)
      if (result === 'not_configured') throw new Error('Tesla Fleet API not configured')
    },

    // Nur Tesla-Nutzer, deren Verbindung den vehicle_location-Scope nachweislich noch nicht hat -
    // nicht mehr jeder verbundene Tesla-User pauschal (die mit dem Scope brauchten den Hinweis nie).
    condition: (ctx) => ctx.hasTeslaConnection && !ctx.teslaLocationScopeGranted,
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
    key: 'survey_ev_pain_points_2026',
    expiresAt: '2026-09-30',
    titleKey: 'announcements.survey_ev_pain_points_2026_title',
    bodyKey: 'announcements.survey_ev_pain_points_2026_body',
    ctaLabelKey: 'announcements.survey_ev_pain_points_2026_cta',
    ctaRoute: '/umfrage/ev-pain-points-2026',
  },
]
