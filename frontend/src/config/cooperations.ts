/**
 * Partner banners below "Andere beliebte Modelle" on the model pages. The image lives under
 * public/partners/ (no third-party script, no tracking on the partner side). Inactive until the
 * partner delivers the image.
 */
export interface ModelCooperationNoteConfig {
  id: string
  active: boolean
  /** Partner target without UTM; ModelCooperationNote adds utm_source=ev-monitor. */
  href: string
  imageUrl: string
  alt: string
  width: number
  height: number
}

export const PARTNER_BANNERS: ModelCooperationNoteConfig[] = [
  {
    id: 'firstev',
    active: false,
    href: 'https://www.firstev.de/',
    imageUrl: '/partners/firstev.webp',
    alt: 'firstev.de - Forum für Elektroautos',
    width: 728,
    height: 90,
  },
]
