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
  PlusCircleIcon,
  ChatBubbleLeftEllipsisIcon
} from '@heroicons/vue/24/outline'
import { analytics } from '../services/analytics'
import api from '../api/axios'
import { useAuthStore } from '../stores/auth'
import { useOnboardingState } from '../composables/useOnboardingState'

const router = useRouter()
const authStore = useAuthStore()
const { isOnboardingVisible } = useOnboardingState()

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
    isOnboardingVisible.value = true
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
      isOnboardingVisible.value = true
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
  isOnboardingVisible.value = false
}

const complete = () => {
  analytics.trackOnboardingCompleted(step.value)
  localStorage.setItem(onboardingKey(), 'true')
  showWelcome.value = false
  isOnboardingVisible.value = false
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
          <div v-if="showWelcome" class="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl max-w-2xl w-full relative max-h-[90dvh] overflow-y-auto">

            <!-- Stable Header: Close + Progress (kein Slide, kein Flackern) -->
            <div class="px-8 pt-8 pb-0">
              <button
                @click="skip"
                class="absolute top-4 right-4 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition"
                title="Überspringen">
                <XMarkIcon class="h-6 w-6" />
              </button>

              <!-- Tutorial Badge -->
              <div class="flex justify-center mb-3">
                <span class="text-xs font-semibold uppercase tracking-widest text-indigo-500 bg-indigo-50 px-3 py-1 rounded-full">Einführung</span>
              </div>

              <!-- Progress Dots -->
              <div class="flex justify-center gap-2 mb-6">
                <div
                  v-for="i in 4"
                  :key="i"
                  :class="i === step ? 'w-8 bg-indigo-600' : i < step ? 'w-2 bg-indigo-300' : 'w-2 bg-gray-300 dark:bg-gray-600'"
                  class="h-2 rounded-full transition-all duration-400" />
              </div>
            </div>

            <!-- Slider Container: feste Höhe damit alle Steps gleich groß sind -->
            <div class="overflow-hidden h-[480px]">
              <Transition :name="direction === 'forward' ? 'slide-forward' : 'slide-backward'" mode="out-in">
                <!-- Step 1: Welcome -->
                <div v-if="step === 1" key="1" class="h-[480px] flex flex-col px-8 pb-8 text-center">
                  <div class="flex-1 flex flex-col items-center justify-center gap-4">
                    <div class="text-6xl">👋</div>
                    <h2 class="text-3xl font-bold text-gray-800 dark:text-gray-200">Willkommen bei EV Monitor!</h2>
                    <p class="text-lg text-gray-600 dark:text-gray-400 max-w-md mx-auto">
                      Tracke deine Ladevorgänge, vergleiche deinen Verbrauch mit <strong>WLTP</strong> und teile
                      deine Erfahrungen mit der Community.
                    </p>
                    <div class="grid grid-cols-3 gap-4 max-w-lg mx-auto">
                      <div class="text-center">
                        <BoltIcon class="h-10 w-10 mx-auto text-green-600 mb-1" />
                        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">Ladevorgänge</p>
                      </div>
                      <div class="text-center">
                        <svg class="h-10 w-10 mx-auto text-blue-600 mb-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                        </svg>
                        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">Statistiken</p>
                      </div>
                      <div class="text-center">
                        <svg class="h-10 w-10 mx-auto text-purple-600 mb-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                        </svg>
                        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">Community</p>
                      </div>
                    </div>
                  </div>
                  <div class="flex flex-col items-center gap-2">
                    <button
                      @click="next"
                      class="px-8 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition font-medium text-lg shadow-lg hover:shadow-xl">
                      Los geht's! →
                    </button>
                    <p class="text-xs text-gray-400 dark:text-gray-500">Du kannst das Tutorial jederzeit überspringen</p>
                  </div>
                </div>

                <!-- Step 2: Add Car -->
                <div v-else-if="step === 2" key="2" class="h-[480px] flex flex-col px-8 pb-8 text-center">
                  <div class="flex-1 flex flex-col items-center justify-center gap-4">
                    <TruckIcon class="h-16 w-16 mx-auto text-indigo-600" />
                    <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200">Füge dein E-Auto hinzu</h2>
                    <p class="text-gray-600 dark:text-gray-400 max-w-md mx-auto">
                      Wähle dein Auto aus über <strong>100 Modellen</strong> aus.
                    </p>
                    <div class="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/30 dark:to-indigo-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-4 text-left max-w-md mx-auto w-full">
                      <ul class="text-sm text-blue-800 dark:text-blue-200 space-y-0.5">
                        <li>✅ Automatische WLTP-Verbrauchsdaten</li>
                        <li>✅ Batterie-Kapazität & Reichweite</li>
                        <li>✅ Community-Durchschnittswerte</li>
                        <li>✅ Realverbrauch vs. WLTP Vergleich</li>
                      </ul>
                    </div>
                  </div>
                  <div class="flex gap-4 justify-center">
                    <button @click="back" class="px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition">← Zurück</button>
                    <button @click="next" class="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium transition shadow-lg hover:shadow-xl">Weiter →</button>
                  </div>
                </div>

                <!-- Step 3: Add First Log -->
                <div v-else-if="step === 3" key="3" class="h-[480px] flex flex-col px-4 sm:px-8 pb-4 sm:pb-8 text-center">
                  <div class="flex-1 flex flex-col items-center justify-center gap-4">
                    <BoltIcon class="h-12 w-12 mx-auto text-green-600" />
                    <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200">Erfasse deinen ersten Ladevorgang</h2>
                    <p class="text-gray-600 dark:text-gray-400 max-w-md mx-auto">
                      Wähle zwischen zwei einfachen Methoden – beide dauern weniger als <strong>30 Sekunden</strong>!
                    </p>
                    <p class="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">Deine Optionen</p>
                    <div class="grid grid-cols-2 gap-3 w-full max-w-sm mx-auto">
                      <div class="bg-gradient-to-br from-indigo-50 to-blue-50 border border-indigo-200 rounded-xl p-3 flex items-center gap-3">
                        <CameraIcon class="h-7 w-7 text-indigo-600 flex-shrink-0" />
                        <div class="text-left">
                          <p class="text-sm font-semibold text-indigo-900">Foto scannen</p>
                          <p class="text-xs text-indigo-600">100% privat – kein Upload</p>
                        </div>
                      </div>
                      <div class="bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl p-3 flex items-center gap-3">
                        <PencilSquareIcon class="h-7 w-7 text-gray-600 dark:text-gray-400 flex-shrink-0" />
                        <div class="text-left">
                          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">Manuell eingeben</p>
                          <p class="text-xs text-gray-600 dark:text-gray-400">Schnell & flexibel</p>
                        </div>
                      </div>
                    </div>
                    <div class="w-full">
                      <p class="text-xs text-gray-500 dark:text-gray-400 mb-1.5">Oder importiere bestehende Daten aus:</p>
                      <div class="flex flex-wrap justify-center gap-1.5">
                        <span class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 px-2.5 py-1 rounded-full">Sprit-Monitor</span>
                        <span class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 px-2.5 py-1 rounded-full">go-eCharger</span>
                        <span class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 px-2.5 py-1 rounded-full">Tesla / TeslaLogger</span>
                        <span class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 px-2.5 py-1 rounded-full">OCPP Wallbox</span>
                      </div>
                    </div>
                  </div>
                  <div class="flex gap-4 justify-center pt-4">
                    <button @click="back" class="px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition">← Zurück</button>
                    <button @click="next" class="px-8 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium transition shadow-lg hover:shadow-xl">Weiter →</button>
                  </div>
                </div>

                <!-- Step 4: PWA Install -->
                <div v-else-if="step === 4" key="4" class="h-[480px] flex flex-col px-4 sm:px-8 pb-4 sm:pb-8 text-center">
                  <div class="flex-1 flex flex-col items-center justify-center gap-3">
                    <DevicePhoneMobileIcon class="h-12 w-12 mx-auto text-indigo-600" />
                    <h2 class="text-2xl font-bold text-gray-800 dark:text-gray-200">Direkt vom Handy nutzen</h2>
                    <p class="text-gray-600 dark:text-gray-400 max-w-md mx-auto">
                      Füge EV Monitor zum Homescreen hinzu – kein App Store nötig.
                    </p>

                    <div v-if="isAlreadyInstalled" class="bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded-xl p-3 max-w-md mx-auto w-full">
                      <p class="text-sm text-green-800 dark:text-green-200 font-medium">Bereits installiert! Du nutzt EV Monitor schon als App.</p>
                    </div>

                    <template v-else>
                      <div v-if="deferredInstallPrompt" class="max-w-md mx-auto w-full">
                        <button @click="triggerInstall" class="w-full px-5 py-3 bg-indigo-600 text-white rounded-xl hover:bg-indigo-700 font-medium transition shadow-lg flex items-center justify-center gap-2">
                          <PlusCircleIcon class="h-5 w-5" />
                          Zum Homescreen hinzufügen
                        </button>
                      </div>
                      <div v-else-if="isIOS" class="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-3 text-left max-w-md mx-auto w-full">
                        <p class="font-semibold text-blue-900 dark:text-blue-200 text-sm mb-1.5">In Safari:</p>
                        <ol class="text-sm text-blue-800 dark:text-blue-200 space-y-1.5">
                          <li class="flex items-center gap-2"><span class="font-bold">1.</span><ArrowUpOnSquareIcon class="h-4 w-4 flex-shrink-0" />Teilen-Button antippen</li>
                          <li class="flex items-center gap-2"><span class="font-bold">2.</span><PlusCircleIcon class="h-4 w-4 flex-shrink-0" />„Zum Home-Bildschirm" wählen</li>
                          <li class="flex items-center gap-2"><span class="font-bold">3.</span><BoltIcon class="h-4 w-4 flex-shrink-0 text-green-600" />„Hinzufügen" bestätigen – fertig!</li>
                        </ol>
                      </div>
                      <div v-else-if="isAndroid" class="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl p-3 text-left max-w-md mx-auto w-full">
                        <p class="font-semibold text-blue-900 dark:text-blue-200 text-sm mb-1.5">In Chrome:</p>
                        <ol class="text-sm text-blue-800 dark:text-blue-200 space-y-1.5">
                          <li class="flex items-center gap-2"><span class="font-bold">1.</span><EllipsisVerticalIcon class="h-4 w-4 flex-shrink-0" />Menü (drei Punkte) antippen</li>
                          <li class="flex items-center gap-2"><span class="font-bold">2.</span><PlusCircleIcon class="h-4 w-4 flex-shrink-0" />„App installieren" oder „Zum Startbildschirm"</li>
                          <li class="flex items-center gap-2"><span class="font-bold">3.</span><BoltIcon class="h-4 w-4 flex-shrink-0 text-green-600" />Bestätigen – fertig!</li>
                        </ol>
                      </div>
                      <div v-else class="bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-xl p-3 text-sm text-gray-600 dark:text-gray-400 max-w-md mx-auto w-full">
                        Auf dem Smartphone: Öffne <strong>ev-monitor.net</strong> in Safari (iOS) oder Chrome (Android) und füge die Seite zum Homescreen hinzu.
                      </div>
                    </template>

                    <div class="bg-gradient-to-r from-purple-50 to-pink-50 border border-purple-200 rounded-xl p-3 max-w-md mx-auto w-full">
                      <div class="flex items-center gap-3">
                        <ChatBubbleLeftEllipsisIcon class="h-5 w-5 text-purple-600 flex-shrink-0" />
                        <div class="text-left">
                          <p class="text-sm font-semibold text-purple-900">Deine Meinung zählt!</p>
                          <p class="text-sm text-purple-800">Oben rechts findest du den <strong>Feedback-Button</strong> 💬</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div class="flex gap-4 justify-center">
                    <button @click="back" class="px-6 py-3 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition">← Zurück</button>
                    <button @click="complete" class="px-8 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 font-medium transition shadow-lg hover:shadow-xl flex items-center gap-2">
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
