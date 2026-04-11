<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { shouldShowCookieBanner, setRedditConsent, initRedditPixel } from '../../utils/reddit-pixel'
import { InformationCircleIcon } from '@heroicons/vue/24/outline'

const showBanner = ref(false)

onMounted(() => {
  // Prüfen ob Banner gezeigt werden soll
  if (shouldShowCookieBanner()) {
    showBanner.value = true
  } else {
    // Wenn User bereits Consent gegeben hat (früherer Besuch), Pixel initialisieren
    const hasConsent = localStorage.getItem('reddit-pixel-consent') === 'true'
    if (hasConsent) {
      initRedditPixel()
    }
  }
})

function accept() {
  setRedditConsent(true)
  showBanner.value = false
}

function reject() {
  setRedditConsent(false)
  showBanner.value = false
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-transform duration-300 ease-out"
      leave-active-class="transition-transform duration-200 ease-in"
      enter-from-class="translate-y-full"
      leave-to-class="translate-y-full">
      <div
        v-if="showBanner"
        class="fixed bottom-0 left-0 right-0 z-50 bg-gray-900 text-white shadow-2xl border-t border-gray-700">
        <div class="max-w-6xl mx-auto px-4 py-4">
          <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <!-- Text -->
            <div class="flex items-start gap-3 flex-1">
              <InformationCircleIcon class="h-6 w-6 text-blue-400 flex-shrink-0 mt-0.5" />
              <div>
                <p class="text-sm text-gray-200 leading-relaxed">
                  <strong class="text-white">Du kommst von einer Reddit-Anzeige.</strong>
                  Wir nutzen Cookies um zu messen, welche Ads gut funktionieren.
                  Das hilft uns bessere Inhalte zu erstellen.
                </p>
                <router-link
                  to="/datenschutz"
                  class="text-xs text-blue-400 hover:text-blue-300 underline mt-1 inline-block">
                  Mehr Infos in der Datenschutzerklärung
                </router-link>
              </div>
            </div>

            <!-- Buttons -->
            <div class="flex gap-3 flex-shrink-0 w-full sm:w-auto">
              <button
                @click="reject"
                class="flex-1 sm:flex-none px-4 py-2.5 text-sm border border-gray-600 rounded-lg hover:bg-gray-800 transition">
                Ablehnen
              </button>
              <button
                @click="accept"
                class="flex-1 sm:flex-none px-6 py-2.5 text-sm bg-green-600 rounded-lg hover:bg-green-700 font-medium transition shadow-lg">
                Akzeptieren
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
