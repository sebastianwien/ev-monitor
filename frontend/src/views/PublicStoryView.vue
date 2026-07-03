<template>
  <div :class="isAuthenticated ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-950'">
    <PublicNav />
    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <div class="bg-white dark:bg-gray-900 border-y md:border border-gray-200 dark:border-gray-800 md:rounded-xl md:shadow-sm px-4 md:px-6 py-6 md:py-8">
      <div v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">
        {{ t('common.loading') }}
      </div>

      <div v-else-if="!story" class="text-center py-16">
        <p class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">{{ t('stories.not_found') }}</p>
        <RouterLink to="/stories" class="text-emerald-600 dark:text-emerald-400 hover:underline">
          {{ t('stories.back_to_stories') }}
        </RouterLink>
      </div>

      <article v-else>
        <nav class="mb-4 text-sm" aria-label="Breadcrumb">
          <RouterLink to="/stories" class="text-emerald-600 dark:text-emerald-400 hover:underline">
            {{ t('stories.heading') }}
          </RouterLink>
          <span class="text-gray-400 mx-1">/</span>
          <span class="text-gray-500 dark:text-gray-400">{{ story.title }}</span>
        </nav>

        <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-3">{{ story.title }}</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mb-8 flex items-center gap-2">
          <UserCircleIcon class="w-5 h-5 shrink-0" aria-hidden="true" />
          {{ t('stories.by', { author: story.authorUsername }) }}
          <template v-if="publishedDate"> · {{ publishedDate }}</template>
        </p>

        <div class="space-y-6">
          <template v-for="(block, index) in story.blocks" :key="index">
            <div v-if="block.type === 'text'" class="story-prose" v-html="renderStoryMarkdown(block.markdown || '')" />
            <TripStatsWidget v-else-if="block.type === 'tripStats'" :block="block" />
          </template>
        </div>

        <div class="mt-12 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-4 text-sm text-gray-600 dark:text-gray-300">
          {{ t('stories.cta_text') }}
          <RouterLink to="/register" class="text-emerald-600 dark:text-emerald-400 font-semibold hover:underline">
            {{ t('common.free_start') }}
          </RouterLink>
        </div>
      </article>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useHead } from '@unhead/vue'
import { UserCircleIcon } from '@heroicons/vue/24/outline'
import { storyService, type PublicTripStory } from '../api/storyService'
import { renderStoryMarkdown } from '../utils/storyMarkdown'
import TripStatsWidget from '../components/stories/TripStatsWidget.vue'
import PublicNav from '../components/shared/PublicNav.vue'
import { useAuthStore } from '../stores/auth'

const BASE_URL = 'https://ev-monitor.net'

const route = useRoute()
const { t, locale } = useI18n()
const authStore = useAuthStore()
const isAuthenticated = computed(() => authStore.isAuthenticated())

const story = ref<PublicTripStory | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    story.value = await storyService.getPublicStory(String(route.params.slug))
  } catch {
    story.value = null
  } finally {
    loading.value = false
  }
})

const publishedDate = computed(() => {
  if (!story.value?.publishedAt) return null
  return new Date(story.value.publishedAt).toLocaleDateString(locale.value, {
    day: '2-digit', month: 'long', year: 'numeric',
  })
})

const metaDescription = computed(() => {
  const s = story.value
  if (!s) return ''
  if (s.summary) return s.summary
  const firstText = s.blocks.find(b => b.type === 'text')?.markdown ?? ''
  return firstText.replace(/[#*_>`\[\]]/g, '').slice(0, 200)
})

useHead(computed(() => {
  const s = story.value
  const canonical = `${BASE_URL}/stories/${route.params.slug}`
  if (!s) {
    return {
      title: `${t('stories.heading')} - EV Monitor`,
      meta: [{ name: 'robots', content: loading.value ? 'noindex' : 'noindex, nofollow' }],
    }
  }
  return {
    title: `${s.title} - EV Monitor Stories`,
    htmlAttrs: { lang: s.language },
    meta: [
      { name: 'description', content: metaDescription.value },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: s.title },
      { property: 'og:description', content: metaDescription.value },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: canonical },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: s.title },
      { name: 'twitter:description', content: metaDescription.value },
    ],
    link: [{ rel: 'canonical', href: canonical }],
    script: [{
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: s.title,
        description: metaDescription.value,
        datePublished: s.publishedAt,
        author: { '@type': 'Person', name: s.authorUsername },
        publisher: { '@type': 'Organization', name: 'EV Monitor', url: BASE_URL },
        mainEntityOfPage: { '@type': 'WebPage', '@id': canonical },
        inLanguage: s.language,
      }),
    }],
  }
}))
</script>

<style scoped>
.story-prose {
  max-width: 65ch;
  color: rgb(55 65 81);
  line-height: 1.7;
}
:global(.dark) .story-prose {
  color: rgb(209 213 219);
}
.story-prose :deep(h1), .story-prose :deep(h2), .story-prose :deep(h3), .story-prose :deep(h4) {
  font-weight: 700;
  color: rgb(17 24 39);
  margin: 1.5em 0 0.5em;
}
:global(.dark) .story-prose :deep(h1), :global(.dark) .story-prose :deep(h2),
:global(.dark) .story-prose :deep(h3), :global(.dark) .story-prose :deep(h4) {
  color: rgb(243 244 246);
}
.story-prose :deep(h1) { font-size: 1.5rem; }
.story-prose :deep(h2) { font-size: 1.25rem; }
.story-prose :deep(h3) { font-size: 1.125rem; }
.story-prose :deep(p) { margin: 0.75em 0; }
.story-prose :deep(ul), .story-prose :deep(ol) { margin: 0.75em 0; padding-left: 1.5em; }
.story-prose :deep(ul) { list-style: disc; }
.story-prose :deep(ol) { list-style: decimal; }
.story-prose :deep(a) { color: rgb(5 150 105); text-decoration: underline; }
.story-prose :deep(blockquote) {
  border-left: 3px solid rgb(5 150 105);
  padding-left: 1em;
  font-style: italic;
  margin: 1em 0;
}
.story-prose :deep(code) {
  background: rgb(243 244 246);
  padding: 0.15em 0.35em;
  border-radius: 0.25rem;
  font-size: 0.875em;
}
:global(.dark) .story-prose :deep(code) { background: rgb(31 41 55); }
.story-prose :deep(table) { border-collapse: collapse; margin: 1em 0; width: 100%; }
.story-prose :deep(th), .story-prose :deep(td) {
  border: 1px solid rgb(229 231 235);
  padding: 0.4em 0.6em;
  text-align: left;
}
:global(.dark) .story-prose :deep(th), :global(.dark) .story-prose :deep(td) {
  border-color: rgb(55 65 81);
}
</style>
