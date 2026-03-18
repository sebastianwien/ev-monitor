<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { XMarkIcon, ChatBubbleLeftEllipsisIcon } from '@heroicons/vue/24/outline'
import { useAuthStore } from '../stores/auth'

const STORAGE_KEY = 'feedbackToastDismissed'
const DELAY_MS = 60_000

const authStore = useAuthStore()
const visible = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

const shouldShow = () => {
  if (localStorage.getItem(STORAGE_KEY)) return false
  if (localStorage.getItem('wasRealUser')) return false
  // Show for unauthenticated visitors and demo accounts only
  if (authStore.isAuthenticated() && !authStore.isDemoAccount) return false
  return true
}

const dismiss = () => {
  visible.value = false
  localStorage.setItem(STORAGE_KEY, '1')
}

onMounted(() => {
  if (!shouldShow()) return
  timer = setTimeout(() => {
    // Re-check at show time in case user logged in during the 90s
    if (shouldShow()) visible.value = true
  }, DELAY_MS)
})

onUnmounted(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <Transition
    enter-active-class="transition duration-300 ease-out"
    enter-from-class="opacity-0 translate-y-4"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition duration-200 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-4"
  >
    <div
      v-if="visible"
      class="fixed bottom-4 left-4 right-4 sm:left-auto sm:right-4 sm:w-80 z-50 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl shadow-2xl p-4"
    >
      <button
        @click="dismiss"
        class="absolute top-3 right-3 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition"
        aria-label="Schließen"
      >
        <XMarkIcon class="h-4 w-4" />
      </button>

      <div class="flex items-start gap-3 pr-4">
        <div class="flex-shrink-0 mt-0.5 p-1.5 bg-green-50 rounded-lg">
          <ChatBubbleLeftEllipsisIcon class="h-5 w-5 text-green-600" />
        </div>
        <div>
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-200">Kurze Frage an dich</p>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5 leading-snug">
            Was überzeugt dich noch nicht? Was fehlt dir? Dein Feedback hilft uns sehr.
          </p>
          <a
            href="https://tally.so/r/aQxkkq"
            target="_blank"
            rel="noopener noreferrer"
            @click="dismiss"
            class="inline-block mt-3 px-3 py-1.5 bg-green-600 hover:bg-green-700 text-white text-xs font-medium rounded-lg transition"
          >
            Feedback geben →
          </a>
        </div>
      </div>
    </div>
  </Transition>
</template>
