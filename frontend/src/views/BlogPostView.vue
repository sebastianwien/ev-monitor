<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10">
      <!-- 404 state -->
      <div v-if="!post" class="px-4 md:px-0 text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">
          {{ t('blog.not_found_title') }}
        </h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('blog.not_found_text') }}</p>
        <a href="/blog" class="inline-block bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">
          {{ t('blog.back_to_list') }}
        </a>
      </div>

      <article
        v-else
        class="bg-white dark:bg-gray-900 md:rounded-lg md:shadow-sm border-y md:border border-gray-200 dark:border-gray-800 px-4 md:px-8 py-6 md:py-10"
      >
        <!-- Breadcrumb -->
        <nav class="text-sm text-gray-500 dark:text-gray-400 mb-4">
          <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">EV Monitor</a>
          <span class="mx-2">›</span>
          <a href="/blog" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('blog.breadcrumb') }}</a>
        </nav>

        <header class="mb-6 md:mb-8">
          <h1 class="text-3xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 tracking-tight">
            {{ post.title }}
          </h1>
          <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-gray-500 dark:text-gray-400">
            <time :datetime="post.date">{{ formatDate(post.date) }}</time>
            <span aria-hidden="true">·</span>
            <span>{{ post.author }}</span>
          </div>
        </header>

        <!-- Markdown body. Source comes from a controlled .md file in the repo, not user input. -->
        <div class="blog-prose" v-html="post.bodyHtml" />

        <BlogComments />
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import PublicNav from '../components/shared/PublicNav.vue'
import BlogComments from '../components/blog/BlogComments.vue'
import { findPostBySlug } from '../utils/markdownLoader'

const { t } = useI18n()
const route = useRoute()

const post = computed(() => {
  const slug = String(route.params.slug ?? '')
  return findPostBySlug(slug)
})

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('de-DE', { year: 'numeric', month: 'long', day: 'numeric' })
}

const BASE_URL = 'https://ev-monitor.net'

useHead(computed(() => {
  const p = post.value
  if (!p) {
    return {
      title: t('blog.not_found_title') + ' – EV Monitor',
      meta: [{ name: 'robots', content: 'noindex, nofollow' }],
    }
  }

  const canonical = `${BASE_URL}/blog/${p.slug}`

  const blogPostingJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: p.title,
    description: p.description,
    datePublished: p.date,
    author: { '@type': 'Person', name: p.author },
    publisher: {
      '@type': 'Organization',
      name: 'EV Monitor',
      url: BASE_URL,
    },
    mainEntityOfPage: { '@type': 'WebPage', '@id': canonical },
    inLanguage: 'de',
  }

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: BASE_URL },
      { '@type': 'ListItem', position: 2, name: 'Blog', item: `${BASE_URL}/blog` },
      { '@type': 'ListItem', position: 3, name: p.title, item: canonical },
    ],
  }

  return {
    title: `${p.title} – EV Monitor Blog`,
    htmlAttrs: { lang: 'de' },
    meta: [
      { name: 'description', content: p.description },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: p.title },
      { property: 'og:description', content: p.description },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: canonical },
      { property: 'article:published_time', content: p.date },
      { property: 'article:author', content: p.author },
    ],
    link: [{ rel: 'canonical', href: canonical }],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(blogPostingJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
    ],
  }
}))
</script>

<style scoped>
/* Minimal prose styling - we don't ship @tailwindcss/typography so we keep
   this lightweight and reading-friendly. Mobile-first, ~65ch on desktop. */
.blog-prose {
  color: rgb(55 65 81);
  font-size: 1rem;
  line-height: 1.75;
  max-width: 65ch;
}
:global(.dark) .blog-prose {
  color: rgb(209 213 219);
}
.blog-prose :deep(h2) {
  font-size: 1.5rem;
  font-weight: 700;
  margin-top: 2rem;
  margin-bottom: 0.75rem;
  color: rgb(17 24 39);
  line-height: 1.3;
}
:global(.dark) .blog-prose :deep(h2) { color: rgb(243 244 246); }
.blog-prose :deep(h3) {
  font-size: 1.25rem;
  font-weight: 600;
  margin-top: 1.5rem;
  margin-bottom: 0.5rem;
  color: rgb(17 24 39);
}
:global(.dark) .blog-prose :deep(h3) { color: rgb(243 244 246); }
.blog-prose :deep(h4) {
  font-size: 1.1rem;
  font-weight: 600;
  margin-top: 1.25rem;
  margin-bottom: 0.5rem;
  color: rgb(17 24 39);
}
:global(.dark) .blog-prose :deep(h4) { color: rgb(243 244 246); }
.blog-prose :deep(p) { margin: 1rem 0; }
.blog-prose :deep(ul),
.blog-prose :deep(ol) {
  margin: 1rem 0;
  padding-left: 1.5rem;
}
.blog-prose :deep(ul) { list-style: disc; }
.blog-prose :deep(ol) { list-style: decimal; }
.blog-prose :deep(li) { margin: 0.25rem 0; }
.blog-prose :deep(a) {
  color: rgb(21 128 61);
  text-decoration: underline;
  text-underline-offset: 2px;
}
:global(.dark) .blog-prose :deep(a) { color: rgb(74 222 128); }
.blog-prose :deep(strong) { font-weight: 600; color: rgb(17 24 39); }
:global(.dark) .blog-prose :deep(strong) { color: rgb(243 244 246); }
.blog-prose :deep(code) {
  background: rgb(243 244 246);
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  font-size: 0.9em;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
:global(.dark) .blog-prose :deep(code) { background: rgb(31 41 55); }
.blog-prose :deep(pre) {
  background: rgb(17 24 39);
  color: rgb(243 244 246);
  padding: 1rem;
  border-radius: 0.5rem;
  overflow-x: auto;
  margin: 1rem 0;
}
.blog-prose :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}
.blog-prose :deep(blockquote) {
  border-left: 4px solid rgb(34 197 94);
  padding-left: 1rem;
  margin: 1rem 0;
  color: rgb(75 85 99);
  font-style: italic;
}
:global(.dark) .blog-prose :deep(blockquote) { color: rgb(156 163 175); }
.blog-prose :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.5rem;
  margin: 1rem 0;
}
.blog-prose :deep(hr) {
  border: 0;
  border-top: 1px solid rgb(229 231 235);
  margin: 2rem 0;
}
:global(.dark) .blog-prose :deep(hr) { border-top-color: rgb(55 65 81); }
</style>
