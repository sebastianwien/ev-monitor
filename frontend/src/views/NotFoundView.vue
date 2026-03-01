<script setup lang="ts">
import { useRouter } from 'vue-router';

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
        Die Seite hat sich wohl selbst entladen...
      </p>

      <p class="text-gray-400 mb-8">
        <code class="bg-gray-800 px-2 py-1 rounded text-red-400">{{ $route.fullPath }}</code> existiert nicht.
      </p>

      <!-- Fun Facts -->
      <div class="bg-gray-800/50 border border-gray-700 rounded-lg p-6 mb-8 text-left">
        <p class="text-gray-300 text-sm mb-2">
          <span class="text-yellow-400">💡 Fun Fact:</span> HTTP 404 wurde nach Room 404 im CERN benannt,
          wo Tim Berners-Lee das WWW erfand. (Ist aber wahrscheinlich ein Mythos 🤓)
        </p>
      </div>

      <!-- Action Buttons -->
      <div class="flex flex-col sm:flex-row gap-4 justify-center">
        <button
          @click="goBack"
          class="px-6 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors font-medium"
        >
          ← Zurück
        </button>
        <button
          @click="goHome"
          class="px-6 py-3 bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-700 hover:to-cyan-700 text-white rounded-lg transition-all font-medium shadow-lg hover:shadow-xl"
        >
          🏠 Zum Dashboard
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
