<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <header class="px-4 md:px-0 mb-8">
        <h1 class="text-3xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 tracking-tight">
          {{ t('blog.list_title') }}
        </h1>
        <p class="mt-2 text-gray-600 dark:text-gray-400">
          {{ t('blog.list_subtitle') }}
        </p>
      </header>

      <!-- Empty state -->
      <div
        v-if="posts.length === 0"
        data-testid="blog-empty"
        class="px-4 md:px-0 text-gray-600 dark:text-gray-400 py-12 text-center"
      >
        {{ t('blog.no_posts') }}
      </div>

      <!-- Post list -->
      <ul v-else class="space-y-4 md:space-y-6" data-testid="blog-list">
        <li
          v-for="post in posts"
          :key="post.slug"
          class="bg-white dark:bg-gray-900 md:rounded-lg md:shadow-sm border-y md:border border-gray-200 dark:border-gray-800 transition hover:md:shadow-md"
        >
          <a
            :href="`/blog/${post.slug}`"
            class="block px-4 md:px-6 py-5 md:py-6 focus:outline-none focus:ring-2 focus:ring-green-600 focus:ring-offset-2 dark:focus:ring-offset-gray-950 rounded-lg"
          >
            <time
              :datetime="post.date"
              class="text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400"
            >
              {{ formatDate(post.date) }}
            </time>
            <h2 class="mt-1 text-xl md:text-2xl font-semibold text-gray-900 dark:text-gray-100">
              {{ post.title }}
            </h2>
            <p class="mt-2 text-gray-600 dark:text-gray-300 text-sm md:text-base leading-relaxed">
              {{ post.description }}
            </p>
            <span class="mt-3 inline-flex items-center gap-1 text-sm font-medium text-green-700 dark:text-green-400">
              {{ t('blog.read_more') }}
              <ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
            </span>
          </a>
        </li>
      </ul>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import { loadAllPosts } from '../utils/markdownLoader'

const { t } = useI18n()

const posts = loadAllPosts()

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('de-DE', { year: 'numeric', month: 'long', day: 'numeric' })
}

const BASE_URL = 'https://ev-monitor.net'
const canonical = `${BASE_URL}/blog`

useHead(computed(() => {
  const blogJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Blog',
    name: 'EV Monitor Blog',
    url: canonical,
    inLanguage: 'de',
    blogPost: posts.map(p => ({
      '@type': 'BlogPosting',
      headline: p.title,
      description: p.description,
      datePublished: p.date,
      author: { '@type': 'Person', name: p.author },
      url: `${BASE_URL}/blog/${p.slug}`,
    })),
  }

  return {
    title: t('blog.meta_title'),
    htmlAttrs: { lang: 'de' },
    meta: [
      { name: 'description', content: t('blog.meta_description') },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: t('blog.meta_title') },
      { property: 'og:description', content: t('blog.meta_description') },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonical },
    ],
    link: [{ rel: 'canonical', href: canonical }],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(blogJsonLd) },
    ],
  }
}))
</script>
