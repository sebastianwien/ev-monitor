<template>
  <div :class="isAuthenticated ? '' : 'min-h-screen bg-gray-50 dark:bg-gray-950'">
    <PublicNav />
    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <div class="bg-white dark:bg-gray-900 border-y md:border border-gray-200 dark:border-gray-800 md:rounded-xl md:shadow-sm px-4 md:px-6 py-6 md:py-8">
      <h1 class="text-2xl md:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">
        {{ t('stories.heading') }}
      </h1>
      <p class="text-gray-600 dark:text-gray-400 mb-8">{{ t('stories.subtitle') }}</p>

      <!-- Own stories (management) -->
      <section v-if="isAuthenticated" class="mb-10" aria-labelledby="my-stories-heading">
        <div class="flex items-center justify-between mb-3">
          <h2 id="my-stories-heading" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ t('stories.my_stories') }}
          </h2>
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold px-3 py-2"
            :disabled="creating"
            @click="createStory">
            <PlusIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('stories.new_story') }}
          </button>
        </div>

        <p v-if="myStories.length === 0 && !loadingMine" class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('stories.empty_mine') }}
        </p>

        <ul class="space-y-2">
          <li v-for="s in myStories" :key="s.id"
              class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-3 flex items-center gap-3">
            <div class="min-w-0 flex-1">
              <RouterLink :to="`/stories/edit/${s.id}`" class="font-medium text-gray-900 dark:text-gray-100 hover:underline block truncate">
                {{ s.title }}
              </RouterLink>
              <span class="text-xs"
                    :class="s.status === 'PUBLISHED' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'">
                {{ s.status === 'PUBLISHED' ? t('stories.status_published') : t('stories.status_draft') }}
              </span>
            </div>
            <RouterLink v-if="s.status === 'PUBLISHED'" :to="`/stories/${s.slug}`"
                        class="p-2 text-gray-500 hover:text-emerald-600 dark:text-gray-400"
                        :aria-label="t('stories.view_public')">
              <EyeIcon class="w-5 h-5" aria-hidden="true" />
            </RouterLink>
            <RouterLink :to="`/stories/edit/${s.id}`"
                        class="p-2 text-gray-500 hover:text-emerald-600 dark:text-gray-400"
                        :aria-label="t('stories.edit')">
              <PencilSquareIcon class="w-5 h-5" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
      </section>

      <!-- Public stories -->
      <section aria-labelledby="public-stories-heading">
        <h2 v-if="isAuthenticated" id="public-stories-heading" class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-3">
          {{ t('stories.latest') }}
        </h2>

        <div v-if="loadingPublic" class="text-center py-10 text-gray-500 dark:text-gray-400">
          {{ t('common.loading') }}
        </div>
        <p v-else-if="publicStories.length === 0" class="text-sm text-gray-500 dark:text-gray-400">
          {{ t('stories.empty_public') }}
        </p>

        <ul class="space-y-3">
          <li v-for="s in publicStories" :key="s.slug">
            <a :href="`/stories/${s.slug}`"
               class="block rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-4 hover:border-emerald-300 dark:hover:border-emerald-700 transition-colors"
               @click.prevent="router.push(`/stories/${s.slug}`)">
              <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ s.title }}</h3>
              <p v-if="s.summary" class="text-sm text-gray-600 dark:text-gray-400 mt-1 line-clamp-2">{{ s.summary }}</p>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-2">
                {{ t('stories.by', { author: s.authorUsername }) }}
                <template v-if="s.publishedAt"> · {{ formatDate(s.publishedAt) }}</template>
              </p>
            </a>
          </li>
        </ul>
      </section>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useHead } from '@unhead/vue'
import { PlusIcon, PencilSquareIcon, EyeIcon } from '@heroicons/vue/24/outline'
import { storyService, type PublicTripStorySummary, type TripStory } from '../api/storyService'
import PublicNav from '../components/shared/PublicNav.vue'
import { useAuthStore } from '../stores/auth'

const BASE_URL = 'https://ev-monitor.net'

const router = useRouter()
const { t, locale } = useI18n()
const authStore = useAuthStore()
const isAuthenticated = computed(() => authStore.isAuthenticated())

const publicStories = ref<PublicTripStorySummary[]>([])
const myStories = ref<TripStory[]>([])
const loadingPublic = ref(true)
const loadingMine = ref(true)
const creating = ref(false)

onMounted(async () => {
  storyService.getPublicStories()
      .then(list => { publicStories.value = list })
      .catch(() => { publicStories.value = [] })
      .finally(() => { loadingPublic.value = false })

  if (isAuthenticated.value) {
    storyService.getMyStories()
        .then(list => { myStories.value = list })
        .catch(() => { myStories.value = [] })
        .finally(() => { loadingMine.value = false })
  } else {
    loadingMine.value = false
  }
})

async function createStory() {
  creating.value = true
  try {
    const story = await storyService.createStory({
      title: t('stories.untitled'),
      summary: null,
      language: locale.value.split('-')[0],
      blocks: [],
    })
    router.push(`/stories/edit/${story.id}`)
  } finally {
    creating.value = false
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric' })
}

useHead(computed(() => {
  const canonical = `${BASE_URL}/stories`
  return {
    title: `${t('stories.meta_title')} - EV Monitor`,
    meta: [
      { name: 'description', content: t('stories.meta_description') },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: t('stories.meta_title') },
      { property: 'og:description', content: t('stories.meta_description') },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonical },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: t('stories.meta_title') },
      { name: 'twitter:description', content: t('stories.meta_description') },
    ],
    link: [{ rel: 'canonical', href: canonical }],
    script: [{
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'ItemList',
        itemListElement: publicStories.value.map((s, i) => ({
          '@type': 'ListItem',
          position: i + 1,
          url: `${BASE_URL}/stories/${s.slug}`,
          name: s.title,
        })),
      }),
    }],
  }
}))
</script>
