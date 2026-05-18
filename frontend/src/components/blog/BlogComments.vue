<template>
  <section aria-label="Kommentare" class="mt-12">
    <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4 flex items-center gap-2">
      <ChatBubbleLeftRightIcon class="h-5 w-5 text-green-600" aria-hidden="true" />
      Kommentare
    </h2>

    <div v-if="!configured" class="rounded-md border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-4 text-sm text-gray-600 dark:text-gray-400">
      Kommentare bald verfügbar.
    </div>

    <div v-else ref="giscusContainer" class="giscus" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ChatBubbleLeftRightIcon } from '@heroicons/vue/24/outline'
import { GISCUS_CONFIG, isGiscusConfigured } from '../../config/giscus'

const giscusContainer = ref<HTMLElement | null>(null)
const configured = isGiscusConfigured()

let scriptEl: HTMLScriptElement | null = null

onMounted(() => {
  if (!configured || !giscusContainer.value) return

  const s = document.createElement('script')
  s.src = 'https://giscus.app/client.js'
  s.async = true
  s.crossOrigin = 'anonymous'
  s.setAttribute('data-repo', GISCUS_CONFIG.repo)
  s.setAttribute('data-repo-id', GISCUS_CONFIG.repoId)
  s.setAttribute('data-category', GISCUS_CONFIG.category)
  s.setAttribute('data-category-id', GISCUS_CONFIG.categoryId)
  s.setAttribute('data-mapping', GISCUS_CONFIG.mapping)
  s.setAttribute('data-strict', '0')
  s.setAttribute('data-reactions-enabled', '1')
  s.setAttribute('data-emit-metadata', '0')
  s.setAttribute('data-input-position', 'top')
  s.setAttribute('data-theme', GISCUS_CONFIG.theme)
  s.setAttribute('data-lang', GISCUS_CONFIG.lang)
  s.setAttribute('data-loading', 'lazy')

  scriptEl = s
  giscusContainer.value.appendChild(s)
})

onBeforeUnmount(() => {
  // Clean up the injected giscus iframe + script on route change.
  if (scriptEl?.parentNode) {
    scriptEl.parentNode.removeChild(scriptEl)
  }
  scriptEl = null
})
</script>
