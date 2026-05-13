<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter();

const goHome = () => {
  router.push('/');
};

const goBack = () => {
  router.back();
};
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 px-4">
    <div class="max-w-2xl w-full text-center">
      <!-- 404 ASCII Art -->
      <div class="mb-8 font-mono text-green-400 text-xs sm:text-sm whitespace-pre overflow-x-auto">
        {{`
    ██╗  ██╗ ██████╗ ██╗  ██╗
    ██║  ██║██╔═████╗██║  ██║
    ███████║██║██╔██║███████║
    ╚════██║████╔╝██║╚════██║
         ██║╚██████╔╝     ██║
         ╚═╝ ╚═════╝      ╚═╝
        `}}
      </div>

      <!-- Error Message -->
      <h1 class="text-4xl sm:text-6xl font-bold text-white mb-4">
        Page Not Found
      </h1>

      <p class="text-xl text-gray-300 mb-2">
        {{ t('notfound.subtitle') }}
      </p>

      <p class="text-gray-400 mb-8">
        <code class="bg-gray-800 px-2 py-1 rounded text-red-400">{{ $route.fullPath }}</code> {{ t('notfound.path_info') }}
      </p>

      <!-- Fun Facts -->
      <div class="bg-gray-800/50 border border-gray-700 rounded-sm p-6 mb-8 text-left">
        <p class="text-gray-300 text-sm mb-2">
          <span class="text-yellow-400">💡 {{ t('notfound.fun_fact_label') }}</span> {{ t('notfound.fun_fact') }}
        </p>
      </div>

      <!-- Action Buttons -->
      <div class="flex flex-col sm:flex-row gap-4 justify-center">
        <button
          @click="goBack"
          class="px-6 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-sm transition-colors font-medium"
        >
          {{ t('notfound.go_back') }}
        </button>
        <button
          @click="goHome"
          class="px-6 py-3 bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-700 hover:to-cyan-700 text-white rounded-sm transition-all font-medium shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] hover:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:hover:shadow-[5px_5px_0_rgba(255,255,255,0.35)]"
        >
          {{ t('notfound.go_home') }}
        </button>
      </div>

      <!-- Easter Egg -->
      <div class="mt-12 text-gray-600 text-xs font-mono">
        <p>Error Code: INSUFFICIENT_CHARGING_ENERGY</p>
        <p>Battery Level: 0% 🔋</p>
      </div>
    </div>
  </div>
</template>
