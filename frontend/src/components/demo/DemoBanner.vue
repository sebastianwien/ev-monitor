<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { analytics } from '../../services/analytics'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const shaking = ref(false)

const onBlocked = () => {
  shaking.value = false
  requestAnimationFrame(() => {
    shaking.value = true
    setTimeout(() => { shaking.value = false }, 600)
  })
}

const goToRegister = () => {
  analytics.track('demo_banner_register_clicked')
  sessionStorage.removeItem('ev_demo_entry_url')
  authStore.logout(false)
  router.push({ path: '/register', state: { fromDemo: true } })
}

const exitDemo = () => {
  analytics.track('demo_banner_exit_clicked')
  const entryUrl = sessionStorage.getItem('ev_demo_entry_url') || '/'
  sessionStorage.removeItem('ev_demo_entry_url')
  authStore.logout(false)
  router.push(entryUrl)
}

onMounted(() => window.addEventListener('demo-account-blocked', onBlocked))
onUnmounted(() => window.removeEventListener('demo-account-blocked', onBlocked))
</script>

<template>
  <!-- Sits directly under the fixed navbar (top offset = nav height). Compact single row so it
       reads as a primary banner, not an ignorable cookie-style footer. Height is fixed (56px:
       4px accent + 52px bar) and mirrored in App.vue's mainPaddingTop. -->
  <div
    class="fixed left-0 right-0 z-30"
    style="top: calc(64px + env(safe-area-inset-top))"
    :class="{ 'animate-shake': shaking }">
    <div class="h-1 bg-gradient-to-r from-green-400 via-emerald-500 to-green-600"></div>

    <div
      class="h-[52px] bg-white/95 dark:bg-gray-950/95 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 shadow-[0_6px_24px_-12px_rgba(0,0,0,0.35)]">
      <div class="max-w-7xl mx-auto h-full px-4 sm:px-6 flex items-center gap-3">

        <!-- Status -->
        <span
          class="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-amber-100 dark:bg-amber-900/40 px-2.5 py-0.5 text-xs font-bold uppercase tracking-wide text-amber-700 dark:text-amber-300">
          <span class="h-1.5 w-1.5 rounded-full bg-amber-500"></span>
          {{ t('demo.title') }}
        </span>

        <!-- Marketing hook (desktop only — keeps the bar single-line on mobile) -->
        <p class="hidden md:block min-w-0 truncate text-base text-gray-700 dark:text-gray-300">
          <span class="font-extrabold text-gray-900 dark:text-gray-100">{{ t('demo.cta_headline') }}</span>
          <span class="font-medium text-gray-600 dark:text-gray-300">{{ ' ' + t('demo.cta_subline') }}</span>
        </p>

        <!-- Actions (pushed right): primary CTA first, exit as a distinct red button on the right -->
        <div class="flex items-center gap-2 sm:gap-3 shrink-0 ml-auto">
          <button
            @click="goToRegister"
            class="btn-3d [--btn-shadow-color:#111827] dark:[--btn-shadow-color:#000000] text-center bg-green-600 hover:bg-green-700 text-white border-2 border-gray-900 dark:border-gray-100 px-3 sm:px-5 py-1.5 rounded-sm text-sm font-semibold transition whitespace-nowrap">
            <span class="sm:hidden">{{ t('demo.register_btn_short') }}</span>
            <span class="hidden sm:inline">{{ t('demo.register_btn') }}</span>
          </button>
          <button
            @click="exitDemo"
            class="border-2 border-red-300 dark:border-red-900/60 text-red-700 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 px-3 sm:px-4 py-1.5 rounded-sm text-sm font-semibold transition whitespace-nowrap">
            {{ t('demo.exit_btn') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20%       { transform: translateX(-6px); }
  40%       { transform: translateX(6px); }
  60%       { transform: translateX(-4px); }
  80%       { transform: translateX(4px); }
}
.animate-shake {
  animation: shake 0.6s ease-in-out;
}
</style>
