<script setup lang="ts">
import { ref, computed } from 'vue'
import { XMarkIcon, TrophyIcon } from '@heroicons/vue/24/outline'
import { jwtDecode } from 'jwt-decode'
import { useAuthStore } from '../stores/auth'

const REWARD_UPDATE_DEPLOYMENT_TS = 1773446400000 // 2026-03-14 00:00 UTC
const LS_KEY = 'rewardSystemUpdateBannerDismissed'

const authStore = useAuthStore()
const dismissed = ref(!!localStorage.getItem(LS_KEY))

const show = computed(() => {
  if (dismissed.value) return false
  const token = authStore.token
  if (!token) return false
  try {
    const decoded = jwtDecode<{ iat: number }>(token)
    return decoded.iat * 1000 < REWARD_UPDATE_DEPLOYMENT_TS
  } catch {
    return false
  }
})

function dismiss() {
  localStorage.setItem(LS_KEY, '1')
  dismissed.value = true
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
        <div
          class="relative w-full max-w-md rounded-2xl bg-white shadow-2xl p-6"
          @click.stop
        >
          <!-- Header -->
          <div class="flex items-center gap-3 mb-4">
            <div class="flex-shrink-0 w-10 h-10 rounded-full bg-indigo-100 flex items-center justify-center">
              <TrophyIcon class="w-5 h-5 text-indigo-600" />
            </div>
            <h2 class="text-lg font-bold text-gray-900">Belohnungssystem überarbeitet</h2>
            <button
              @click="dismiss"
              class="ml-auto text-gray-400 hover:text-gray-600 transition-colors"
              aria-label="Schließen"
            >
              <XMarkIcon class="w-5 h-5" />
            </button>
          </div>

          <!-- Body -->
          <div class="space-y-3 text-sm text-gray-600">
            <p>
              Wir haben das <strong class="text-gray-800">Punkte-System grundlegend überarbeitet</strong> —
              wie Watt verdient werden, was sie wert sind und wie sie sich auf die Community auswirken.
            </p>
            <p>
              Deine bisherigen Aktivitäten (Ladevorgänge, eingetragene Fahrzeuge, WLTP-Daten) wurden
              so gut wie möglich ins neue System übertragen. Dein aktueller Kontostand spiegelt das wider.
            </p>
            <p>
              Ab sofort kannst du Watt u.a. durch das Eintragen von Ladevorgängen, das Hinzufügen von
              Fahrzeugen und das Beitragen von WLTP-Verbrauchsdaten verdienen — je mehr du zur Community
              beiträgst, desto mehr profitierst du.
            </p>
          </div>

          <!-- CTA -->
          <button
            @click="dismiss"
            class="mt-5 w-full rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 active:scale-95 transition"
          >
            Verstanden
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
