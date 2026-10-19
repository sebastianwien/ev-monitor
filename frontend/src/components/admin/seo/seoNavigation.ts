import type { SeoModelFilter, SeoPageFilter, SeoQueryFilter } from '../../../api/seoService'

export type SeoSub = 'overview' | 'pages' | 'queries' | 'campaigns' | 'models' | 'tech' | 'competition'

/** Sprung aus dem Überblick in eine Ansicht mit vorgewähltem Befundfilter. */
export interface SeoNavigation {
  sub: SeoSub
  pageFilter?: SeoPageFilter
  queryFilter?: SeoQueryFilter
  modelFilter?: SeoModelFilter
}
