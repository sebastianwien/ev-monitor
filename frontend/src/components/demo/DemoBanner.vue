<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { InformationCircleIcon } from '@heroicons/vue/24/outline'
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
  <div
    :class="[
      'fixed bottom-0 left-0 right-0 z-40',
      'bg-amber-400 border-t-2 border-amber-500',
      'px-4 py-3 flex flex-col gap-2',
      { 'animate-shake': shaking }
    ]">
    <div class="flex items-center gap-2 text-amber-900">
      <InformationCircleIcon class="h-5 w-5 flex-shrink-0" />
      <span class="font-bold">{{ t('demo.title') }}</span>
      <span class="font-normal">· {{ t('demo.read_only') }}</span>
    </div>
    <div class="flex items-center gap-2">
      <button
        @click="goToRegister"
        class="btn-3d flex-1 text-center px-3 py-1.5 rounded-md bg-amber-900 hover:bg-amber-800 text-amber-100 font-semibold transition text-xs whitespace-nowrap">
        {{ t('demo.register_btn') }}
      </button>
      <button
        @click="exitDemo"
        class="btn-3d flex-1 px-3 py-1.5 rounded-md bg-amber-100 hover:bg-white text-amber-900 font-semibold transition text-xs border border-amber-600 whitespace-nowrap">
        {{ t('demo.exit_btn') }}
      </button>
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
