<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { InformationCircleIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const shaking = ref(false)

const onBlocked = () => {
  shaking.value = false
  requestAnimationFrame(() => {
    shaking.value = true
    setTimeout(() => { shaking.value = false }, 600)
  })
}

onMounted(() => window.addEventListener('demo-account-blocked', onBlocked))
onUnmounted(() => window.removeEventListener('demo-account-blocked', onBlocked))
</script>

<template>
  <div
    :class="['bg-amber-50 border-b border-amber-200 px-4 py-2 flex items-center justify-between gap-3 text-sm', { 'animate-shake': shaking }]">
    <div class="flex items-center gap-2 text-amber-800">
      <InformationCircleIcon class="h-5 w-5 flex-shrink-0 text-amber-500" />
      <span><strong>{{ t('demo.title') }}</strong> · {{ t('demo.read_only') }}</span>
    </div>
    <router-link
      to="/register"
      class="flex-shrink-0 px-3 py-1 rounded-md bg-amber-500 hover:bg-amber-600 text-white font-medium transition text-xs">
      {{ t('demo.register_btn') }}
    </router-link>
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
