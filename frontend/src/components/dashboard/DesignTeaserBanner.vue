<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { SparklesIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useStoryOptIn } from '../../composables/useStoryOptIn'

/** Einzeiliger Hinweis im alten Dashboard: "Neues Design ausprobieren?" */
const { t } = useI18n()
const router = useRouter()
const { teaserDismissed, set, dismissTeaser } = useStoryOptIn()

function tryIt() {
  set(true)
  router.push('/story')
}
</script>

<template>
  <div
    v-if="!teaserDismissed"
    data-testid="story-teaser"
    class="flex items-center gap-3 px-4 py-2.5 md:rounded-sm bg-emerald-50 dark:bg-emerald-900/30 text-emerald-900 dark:text-emerald-100 text-sm">
    <SparklesIcon class="w-5 h-5 shrink-0" />
    <span class="flex-1 min-w-0">{{ t('story.teaser_text') }}</span>
    <button type="button" class="font-semibold underline underline-offset-2 min-h-[44px] px-1" @click="tryIt">{{ t('story.teaser_cta') }}</button>
    <button type="button" class="min-h-[44px] min-w-[44px] inline-flex items-center justify-center" :aria-label="t('story.teaser_dismiss')" @click="dismissTeaser">
      <XMarkIcon class="w-5 h-5" />
    </button>
  </div>
</template>
