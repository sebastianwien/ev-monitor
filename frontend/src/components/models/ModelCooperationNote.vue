<template>
  <div v-if="partner.active" class="max-w-4xl mx-auto md:px-4 mt-6">
    <p class="px-4 md:px-0 mb-1 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ t('model.partner_label') }}</p>
    <a :href="href" target="_blank" rel="noopener" class="block" @click="onClick">
      <img :src="partner.imageUrl" :alt="partner.alt" :width="partner.width" :height="partner.height" loading="lazy"
           class="w-full h-auto md:rounded-xl border-y-2 md:border-2 border-gray-800 dark:border-gray-200" />
    </a>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { analytics } from '../../services/analytics'
import type { ModelCooperationNoteConfig } from '../../config/cooperations'

/** One partner banner; followable link (no nofollow) with our UTM so the partner sees where visitors come from. */
const props = defineProps<{ partner: ModelCooperationNoteConfig }>()
const { t } = useI18n()

const href = computed(() => {
  const url = new URL(props.partner.href)
  url.searchParams.set('utm_source', 'ev-monitor')
  url.searchParams.set('utm_medium', 'banner')
  url.searchParams.set('utm_campaign', 'modellseite')
  return url.toString()
})

function onClick() {
  analytics.track('partner_banner_click', { partner: props.partner.id })
}
</script>
