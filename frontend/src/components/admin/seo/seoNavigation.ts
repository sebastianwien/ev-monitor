import type { SeoModelFilter, SeoPageFilter, SeoQueryFilter } from '../../../api/seoService'

export type SeoSub = 'overview' | 'keywords' | 'landingpages' | 'acquisition' | 'audit' | 'serp'

/** Sprung aus der Übersicht in eine Ansicht mit vorgewähltem Befundfilter, Modell-Datenbasis oder Suchbegriff. */
export interface SeoNavigation {
  sub: SeoSub
  pageFilter?: SeoPageFilter
  queryFilter?: SeoQueryFilter
  modelFilter?: SeoModelFilter
  search?: string
}
