export interface FeatureAnnouncement {
  key: string
  expiresAt: string   // ISO date string - nach diesem Datum wird die Ankündigung nicht mehr gezeigt
  title: string
  body: string
  ctaLabel?: string
  ctaRoute?: string
}

export const featureAnnouncements: FeatureAnnouncement[] = [
  {
    key: 'charging-provider-v1',
    expiresAt: '2026-04-04',
    title: 'Neu: Ladetarif eintragen',
    body: 'Du kannst jetzt deinen Ladeanbieter und Tarif in den Einstellungen hinterlegen. So berechnen wir bald, wie viel du mit einem anderen Tarif gespart hättest.',
    ctaLabel: 'Jetzt eintragen',
    ctaRoute: '/settings',
  },
]
