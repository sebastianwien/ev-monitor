import { computed, type Ref } from 'vue'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import type { TopModelPreview } from '../api/publicModelService'
import { useMarketRoute, OG_LOCALE, MARKET_HTML_LANG } from './useMarketRoute'

/**
 * Head tags for the model overview (/modelle and its market variants): title,
 * description, canonical, hreflang, ItemList and BreadcrumbList JSON-LD.
 * The ItemList names the listed models with their real URLs, followed by the brands.
 * `noindex` keeps a preview variant out of the index.
 */
export function useModelsListSeo(models: Ref<TopModelPreview[]>, noindex: Ref<boolean>) {
  const { t } = useI18n()
  const { currentMarket, marketUrl, hreflangLinks } = useMarketRoute()

  const pathOf = (...segments: string[]) => segments.map(s => `/${encodeURIComponent(s)}`).join('')

  const itemListJsonLd = computed(() => {
    const market = currentMarket.value
    const modelItems = models.value.map(m => ({
      name: m.modelDisplayName,
      url: marketUrl(market, pathOf(m.brandDisplayName, m.modelUrlSlug)),
    }))
    const brands = [...new Set(models.value.map(m => m.brandDisplayName))].sort()
    const brandItems = brands.map(brand => ({
      name: t('models_list.jsonld.brand_item', { brand }),
      url: marketUrl(market, pathOf(brand)),
    }))
    return {
      '@context': 'https://schema.org',
      '@type': 'ItemList',
      name: t('models_list.jsonld.list_name'),
      description: t('models_list.meta_description'),
      itemListElement: [...modelItems, ...brandItems].map((item, i) => ({
        '@type': 'ListItem',
        position: i + 1,
        ...item,
      })),
    }
  })

  const breadcrumbJsonLd = computed(() => ({
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
      { '@type': 'ListItem', position: 2, name: t('models_list.jsonld.breadcrumb_models'), item: marketUrl(currentMarket.value) },
    ],
  }))

  useHead(computed(() => {
    const canonical = marketUrl(currentMarket.value)
    return {
      title: t('models_list.meta_title'),
      htmlAttrs: { lang: MARKET_HTML_LANG[currentMarket.value] },
      meta: [
        { name: 'description', content: t('models_list.meta_description') },
        { name: 'keywords', content: t('models_list.meta_keywords') },
        { name: 'robots', content: noindex.value ? 'noindex, nofollow' : 'index, follow' },
        { property: 'og:title', content: t('models_list.og_title') },
        { property: 'og:description', content: t('models_list.og_description') },
        { property: 'og:type', content: 'website' },
        { property: 'og:url', content: canonical },
        { property: 'og:locale', content: OG_LOCALE[currentMarket.value] ?? 'en_GB' },
        { name: 'twitter:card', content: 'summary_large_image' },
        { name: 'twitter:title', content: t('models_list.og_title') },
        { name: 'twitter:description', content: t('models_list.og_description') },
      ],
      link: [
        { rel: 'canonical', href: canonical },
        ...hreflangLinks(),
      ],
      script: [
        { type: 'application/ld+json', innerHTML: () => JSON.stringify(itemListJsonLd.value) },
        { type: 'application/ld+json', innerHTML: () => JSON.stringify(breadcrumbJsonLd.value) },
      ],
    }
  }))
}
