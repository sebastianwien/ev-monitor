<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  XMarkIcon,
  BoltIcon,
  CameraIcon,
  PencilSquareIcon,
  TruckIcon,
  DevicePhoneMobileIcon,
  ArrowUpOnSquareIcon,
  EllipsisVerticalIcon,
  PlusCircleIcon
} from '@heroicons/vue/24/outline'
import { analytics } from '../services/analytics'
import api from '../api/axios'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function onboardingKey() {
  const userId = authStore.user?.sub ?? 'anonymous'
  return `onboarding-completed-${userId}`
}
const showWelcome = ref(false)
const step = ref(1)
const direction = ref<'forward' | 'backward'>('forward')

const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent)
const isAndroid = /Android/.test(navigator.userAgent)
const isAlreadyInstalled = window.matchMedia('(display-mode: standalone)').matches
const deferredInstallPrompt = ref<any>(null)

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  deferredInstallPrompt.value = e
})

const triggerInstall = async () => {
  if (!deferredInstallPrompt.value) return
  deferredInstallPrompt.value.prompt()
  await deferredInstallPrompt.value.userChoice
  deferredInstallPrompt.value = null
}

onMounted(async () => {
  const forceOnboarding = localStorage.getItem('onboarding-force') === 'true'
  if (forceOnboarding) {
    localStorage.removeItem('onboarding-force')
    showWelcome.value = true
    analytics.trackOnboardingStarted()
    return
  }

  const hasSeenOnboarding = localStorage.getItem(onboardingKey())
  if (hasSeenOnboarding) return

  try {
    const carsResponse = await api.get('/cars')
    const hasCars = carsResponse.data && carsResponse.data.length > 0

    if (!hasCars) {
      showWelcome.value = true
      analytics.trackOnboardingStarted()
    } else {
      localStorage.setItem(onboardingKey(), 'true')
    }
  } catch (err) {
    console.error('Failed to check cars for onboarding:', err)
    localStorage.setItem(onboardingKey(), 'true')
  }
})

watch(step, (newStep) => {
  analytics.trackOnboardingStepViewed(newStep)
})

const next = () => {
  direction.value = 'forward'
  step.value++
}

const back = () => {
  direction.value = 'backward'
  step.value--
}

const skip = () => {
  analytics.trackOnboardingSkipped(step.value)
  localStorage.setItem(onboardingKey(), 'true')
  showWelcome.value = false
}

const complete = () => {
  analytics.trackOnboardingCompleted(step.value)
  localStorage.setItem(onboardingKey(), 'true')
  showWelcome.value = false
  router.push('/cars')
}
</script>

<template>
  <Teleport to="body">
    <!-- Backdrop fade in/out -->
    <Transition
      enter-active-class="transition-opacity duration-300"
      leave-active-class="transition-opacity duration-300"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0">
      <div
        v-if="showWelcome"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">

        <!-- Modal: bleibt stabil, öffnet sich einmal und slidet dann nur innen -->
        <Transition
          enter-active-class="transition-all duration-300"
          leave-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          leave-to-class="opacity-0 scale-95">
          <div v-if="showWelcome" class="bg-white rounded-2xl shadow-2xl max-w-2xl w-full relative">

            <!-- Stable Header: Close + Progress (kein Slide, kein Flackern) -->
            <div class="px-8 pt-8 pb-0">
              <button
                @click="skip"
                class="absolute top-4 right-4 text-gray-400 hover:text-gray-600 transition"
                title="Überspringen">
                <XMarkIcon class="h-6 w-6" />
              </button>

              <!-- Progress Dots -->
              <div class="flex justify-center gap-2 mb-6">
                <div
                  v-for="i in 4"
                  :key="i"
                  :class="i === step ? 'w-8 bg-indigo-600' : i < step ? 'w-2 bg-indigo-300' : 'w-2 bg-gray-300'"
                  class="h-2 rounded-full transition-all duration-400" />
              </div>
            </div>

            <!-- Slider Container: overflow hidden damit content nicht rausfällt -->
            <div class="overflow-hidden">
              <Transition :name="direction === 'forward' ? 'slide-forward' : 'slide-backward'" mode="out-in">
                <!-- Step 1: Welcome -->
                <div v-if="step === 1" key="1" class="px-8 pb-8 text-center space-y-6">
                  <div class="text-7xl">👋</div>
                  <h2 class="text-3xl font-bold text-gray-800">Willkommen bei EV Monitor!</h2>
                  <p class="text-lg text-gray-600 max-w-md mx-auto">
                    Tracke deine Ladevorgänge, vergleiche deinen Verbrauch mit <strong>WLTP</strong> und teile
                    deine Erfahrungen mit der Community.
                  </p>

                  <div class="grid grid-cols-3 gap-4 max-w-lg mx-auto">
                    <div class="text-center">
                      <BoltIcon class="h-12 w-12 mx-auto text-green-600 mb-2" />
                      <p class="text-sm font-medium text-gray-700">Ladevorgänge</p>
                    </div>
                    <div class="text-center">
                      <svg class="h-12 w-12 mx-auto text-blue-600 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                      </svg>
                      <p class="text-sm font-medium text-gray-700">Statistiken</p>
                    </div>
                    <div class="text-center">
                      <svg class="h-12 w-12 mx-auto text-purple-600 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                      </svg>
                      <p class="text-sm font-medium text-gray-700">Community</p>
                    </div>
                  </div>

                  <button
                    @click="next"
                    class="px-8 py-4 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition font-medium text-lg shadow-lg hover:shadow-xl">
                    Los geht's! →
                  </button>

                  <p class="text-xs text-gray-400 pb-2">
                    Du kannst das Tutorial jederzeit überspringen
                  </p>
                </div>

                <!-- Step 2: Add Car -->
                <div v-else-if="step === 2" key="2" class="px-8 pb-8 text-center space-y-6">
                  <TruckIcon class="h-20 w-20 mx-auto text-indigo-600" />
                  <h2 class="text-2xl font-bold text-gray-800">Füge dein E-Auto hinzu</h2>
                  <p class="text-gray-600 max-w-md mx-auto">
                    Wähle dein Fahrzeugmodell aus unserer Datenbank mit über <strong>100 E-Autos</strong> von 68 Marken.
                  </p>

                  <div class="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-xl p-6 text-left max-w-md mx-auto">
                    <div class="flex items-start gap-3">
                      <svg class="h-6 w-6 text-blue-600 flex-shrink-0 mt-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      <div>
                        <p class="font-semibold text-blue-900 mb-2">Was du bekommst:</p>
                        <ul class="text-sm text-blue-800 space-y-1">
                          <li>✅ Automatische WLTP-Verbrauchsdaten</li>
                          <li>✅ Batterie-Kapazität & Reichweite</li>
                          <li>✅ Community-Durchschnittswerte</li>
                          <li>✅ Realverbrauch vs. WLTP Vergleich</li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  <div class="flex gap-4 justify-center">
                    <button
                      @click="back"
                      class="px-6 py-3 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition">
                      ← Zurück
                    </button>
                    <button
                      @click="next"
                      class="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium transition shadow-lg hover:shadow-xl flex items-center gap-2">
                      Weiter →
                    </button>
                  </div>
                </div>

                <!-- Step 3: Add First Log -->
                <div v-else-if="step === 3" key="3" class="px-8 pb-8 text-center space-y-6">
                  <BoltIcon class="h-20 w-20 mx-auto text-green-600" />
                  <h2 class="text-2xl font-bold text-gray-800">Erfasse deinen ersten Ladevorgang</h2>
                  <p class="text-gray-600 max-w-md mx-auto">
                    Wähle zwischen zwei einfachen Methoden – beide dauern weniger als <strong>30 Sekunden</strong>!
                  </p>

                  <div class="grid md:grid-cols-2 gap-4 max-w-2xl mx-auto">
                    <div class="bg-gradient-to-br from-indigo-50 to-blue-50 border-2 border-indigo-200 rounded-xl p-6 group">
                      <div class="flex flex-col items-center text-center">
                        <CameraIcon class="h-12 w-12 text-indigo-600 mb-3 group-hover:scale-110 transition" />
                        <p class="text-lg font-semibold text-indigo-900 mb-2">Foto scannen</p>
                        <p class="text-sm text-indigo-700 mb-4">
                          OCR extrahiert automatisch kWh, Kosten & Dauer aus dem Ladedisplay
                        </p>
                        <span class="text-xs text-indigo-600">100% privat – kein Upload</span>
                      </div>
                    </div>

                    <div class="bg-gray-50 border-2 border-gray-200 rounded-xl p-6 group">
                      <div class="flex flex-col items-center text-center">
                        <PencilSquareIcon class="h-12 w-12 text-gray-600 mb-3 group-hover:scale-110 transition" />
                        <p class="text-lg font-semibold text-gray-900 mb-2">Manuell eingeben</p>
                        <p class="text-sm text-gray-700 mb-4">
                          Trage kWh, Kosten und Dauer direkt ein
                        </p>
                        <span class="text-xs text-gray-600">Schnell & flexibel</span>
                      </div>
                    </div>
                  </div>

                  <div class="flex gap-4 justify-center">
                    <button
                      @click="back"
                      class="px-6 py-3 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition">
                      ← Zurück
                    </button>
                    <button
                      @click="next"
                      class="px-8 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium transition shadow-lg hover:shadow-xl flex items-center gap-2">
                      Weiter →
                    </button>
                  </div>
                </div>

                <!-- Step 4: PWA Install -->
                <div v-else-if="step === 4" key="4" class="px-8 pb-8 text-center space-y-6">
                  <DevicePhoneMobileIcon class="h-20 w-20 mx-auto text-indigo-600" />
                  <h2 class="text-2xl font-bold text-gray-800">Direkt vom Handy nutzen</h2>
                  <p class="text-gray-600 max-w-md mx-auto">
                    Füge EV Monitor zum Homescreen hinzu – kein App Store nötig, startet wie eine native App.
                  </p>

                  <div v-if="isAlreadyInstalled" class="bg-green-50 border border-green-200 rounded-xl p-5 max-w-md mx-auto">
                    <p class="text-green-800 font-medium">Bereits installiert! Du nutzt EV Monitor schon als App.</p>
                  </div>

                  <template v-else>
                    <!-- Android mit Install-Prompt -->
                    <div v-if="deferredInstallPrompt" class="max-w-md mx-auto space-y-4">
                      <button
                        @click="triggerInstall"
                        class="w-full px-6 py-4 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 font-medium transition shadow-lg flex items-center justify-center gap-3">
                        <PlusCircleIcon class="h-6 w-6" />
                        Zum Homescreen hinzufügen
                      </button>
                    </div>

                    <!-- iOS -->
                    <div v-else-if="isIOS" class="bg-blue-50 border border-blue-200 rounded-xl p-5 text-left max-w-md mx-auto space-y-3">
                      <p class="font-semibold text-blue-900 text-sm">In Safari:</p>
                      <ol class="text-sm text-blue-800 space-y-2">
                        <li class="flex items-center gap-2">
                          <span class="font-bold">1.</span>
                          <ArrowUpOnSquareIcon class="h-5 w-5 flex-shrink-0" />
                          Teilen-Button antippen
                        </li>
                        <li class="flex items-center gap-2">
                          <span class="font-bold">2.</span>
                          <PlusCircleIcon class="h-5 w-5 flex-shrink-0" />
                          „Zum Home-Bildschirm" wählen
                        </li>
                        <li class="flex items-center gap-2">
                          <span class="font-bold">3.</span>
                          <BoltIcon class="h-5 w-5 flex-shrink-0 text-green-600" />
                          „Hinzufügen" bestätigen – fertig!
                        </li>
                      </ol>
                    </div>

                    <!-- Android ohne Prompt / Desktop -->
                    <div v-else-if="isAndroid" class="bg-blue-50 border border-blue-200 rounded-xl p-5 text-left max-w-md mx-auto space-y-3">
                      <p class="font-semibold text-blue-900 text-sm">In Chrome:</p>
                      <ol class="text-sm text-blue-800 space-y-2">
                        <li class="flex items-center gap-2">
                          <span class="font-bold">1.</span>
                          <EllipsisVerticalIcon class="h-5 w-5 flex-shrink-0" />
                          Menü (drei Punkte) antippen
                        </li>
                        <li class="flex items-center gap-2">
                          <span class="font-bold">2.</span>
                          <PlusCircleIcon class="h-5 w-5 flex-shrink-0" />
                          „App installieren" oder „Zum Startbildschirm"
                        </li>
                        <li class="flex items-center gap-2">
                          <span class="font-bold">3.</span>
                          <BoltIcon class="h-5 w-5 flex-shrink-0 text-green-600" />
                          Bestätigen – fertig!
                        </li>
                      </ol>
                    </div>

                    <!-- Desktop -->
                    <div v-else class="bg-gray-50 border border-gray-200 rounded-xl p-5 text-sm text-gray-600 max-w-md mx-auto">
                      Auf dem Smartphone: Öffne <strong>ev-monitor.net</strong> in Safari (iOS) oder Chrome (Android) und füge die Seite zum Homescreen hinzu.
                    </div>
                  </template>

                  <div class="flex gap-4 justify-center">
                    <button
                      @click="back"
                      class="px-6 py-3 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition">
                      ← Zurück
                    </button>
                    <button
                      @click="complete"
                      class="px-8 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 font-medium transition shadow-lg hover:shadow-xl flex items-center gap-2">
                      <BoltIcon class="h-5 w-5" />
                      Los geht's!
                    </button>
                  </div>
                </div>
              </Transition>
            </div>

          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Vorwärts: neuer Step kommt von rechts */
.slide-forward-enter-active,
.slide-forward-leave-active {
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.35s ease;
}
.slide-forward-enter-from {
  transform: translateX(60px);
  opacity: 0;
}
.slide-forward-leave-to {
  transform: translateX(-60px);
  opacity: 0;
}

/* Rückwärts: neuer Step kommt von links */
.slide-backward-enter-active,
.slide-backward-leave-active {
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.35s ease;
}
.slide-backward-enter-from {
  transform: translateX(-60px);
  opacity: 0;
}
.slide-backward-leave-to {
  transform: translateX(60px);
  opacity: 0;
}
</style>
