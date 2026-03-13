<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '../api/axios'
import { useCoinStore } from '../stores/coins'
import { BoltIcon } from '@heroicons/vue/24/outline'

const coinStore = useCoinStore()

interface CoinLog {
  id: string
  coinType: string
  amount: number
  actionDescription: string
  createdAt: string
}

const logs = ref<CoinLog[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const coinTypeLabel: Record<string, string> = {
  ACHIEVEMENT_COIN: 'Achievement',
  SOCIAL_COIN: 'Community',
  GREEN_COIN: 'Eco',
  DISTANCE_COIN: 'Strecke',
  STREAK_COIN: 'Streak',
  EFFICIENCY_COIN: 'Effizienz'
}

const formatDate = (iso: string): string => {
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const fetchLogs = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await api.get('/coins/logs')
    // Sort newest first
    logs.value = (response.data as CoinLog[]).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Fehler beim Laden des Watt-Verlaufs'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchLogs()
})
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto p-4 md:p-6 md:mt-8">
    <div class="bg-white md:rounded-xl md:shadow-lg p-4 md:p-6">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-2xl font-bold text-gray-800">Watt-Verlauf</h1>
        <div class="flex items-center gap-2 px-4 py-2 bg-indigo-50 border border-indigo-200 rounded-lg">
          <BoltIcon class="h-5 w-5 text-indigo-600" />
          <span class="text-xl font-bold text-indigo-700">{{ coinStore.balance }}</span>
          <span class="text-sm text-indigo-500">Watt</span>
        </div>
      </div>

      <!-- Legende -->
      <details class="mb-6 rounded-lg border border-gray-200 bg-gray-50 text-sm">
        <summary class="cursor-pointer px-4 py-3 font-medium text-gray-700 select-none">Wofür gibt es Watt?</summary>
        <div class="px-4 pb-4 pt-2 space-y-3">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Ladevorgänge</p>
            <ul class="space-y-1 text-gray-600">
              <li class="flex justify-between"><span>Erster Ladevorgang erfasst</span><span class="font-semibold text-indigo-600">+25 ⚡</span></li>
              <li class="flex justify-between"><span>Weiterer Ladevorgang</span><span class="font-semibold text-indigo-600">+5 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via OCR (erster)</span><span class="font-semibold text-indigo-600">+27 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via OCR</span><span class="font-semibold text-indigo-600">+7 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang gelöscht</span><span class="font-semibold text-red-500">− ⚡</span></li>
            </ul>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Imports (einmalig)</p>
            <ul class="space-y-1 text-gray-600">
              <li class="flex justify-between"><span>Sprit-Monitor verbunden</span><span class="font-semibold text-indigo-600">+50 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via Sprit-Monitor</span><span class="font-semibold text-indigo-600">+2 ⚡</span></li>
              <li class="flex justify-between"><span>Tesla verbunden</span><span class="font-semibold text-indigo-600">+50 ⚡</span></li>
              <li class="flex justify-between"><span>TeslaLogger verbunden</span><span class="font-semibold text-indigo-600">+20 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via TeslaLogger (Historie)</span><span class="font-semibold text-indigo-600">+2 ⚡</span></li>
              <li class="flex justify-between"><span>Ladevorgang via Tesla Sync (täglich)</span><span class="font-semibold text-indigo-600">+5 ⚡</span></li>
            </ul>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Fahrzeuge</p>
            <ul class="space-y-1 text-gray-600">
              <li class="flex justify-between"><span>Erstes Fahrzeug hinzugefügt</span><span class="font-semibold text-indigo-600">+20 ⚡</span></li>
              <li class="flex justify-between"><span>Weiteres Fahrzeug</span><span class="font-semibold text-indigo-600">+5 ⚡</span></li>
              <li class="flex justify-between"><span>Erstes Auto-Bild hochgeladen (einmalig)</span><span class="font-semibold text-indigo-600">+15 ⚡</span></li>
              <li class="flex justify-between"><span>Bild öffentlich geteilt (einmalig)</span><span class="font-semibold text-indigo-600">+10 ⚡</span></li>
            </ul>
          </div>
          <div>
            <p class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">Community</p>
            <ul class="space-y-1 text-gray-600">
              <li class="flex justify-between"><span>Freund eingeladen</span><span class="font-semibold text-indigo-600">+100 ⚡</span></li>
              <li class="flex justify-between"><span>Willkommensbonus (eingeladen worden)</span><span class="font-semibold text-indigo-600">+25 ⚡</span></li>
            </ul>
          </div>
        </div>
      </details>

      <!-- Error -->
      <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-md text-sm">
        {{ error }}
      </div>

      <!-- Loading -->
      <div v-if="loading" class="text-center py-12 text-gray-500">
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600 mb-3"></div>
        <p class="text-sm">Lade Watt-Verlauf...</p>
      </div>

      <!-- Empty state -->
      <div v-else-if="logs.length === 0" class="text-center py-12">
        <BoltIcon class="h-16 w-16 mx-auto mb-4 text-gray-300" />
        <h3 class="text-lg font-semibold text-gray-700 mb-2">Noch kein Watt verdient</h3>
        <p class="text-gray-500 text-sm">
          Erfasse deinen ersten Ladevorgang oder füge ein Fahrzeug hinzu, um Coins zu verdienen!
        </p>
      </div>

      <!-- Coin log list -->
      <ul v-else class="space-y-3">
        <li
          v-for="log in logs"
          :key="log.id"
          class="flex items-center justify-between p-4 bg-gray-50 border border-gray-200 rounded-lg">
          <div class="flex items-center gap-3">
            <BoltIcon class="h-6 w-6 text-indigo-400 flex-shrink-0" />
            <div>
              <p class="font-medium text-gray-800 text-sm">{{ log.actionDescription }}</p>
              <p class="text-xs text-gray-500 mt-0.5">
                {{ coinTypeLabel[log.coinType] || log.coinType }} · {{ formatDate(log.createdAt) }}
              </p>
            </div>
          </div>
          <span class="font-bold text-green-600 text-base">+{{ log.amount }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
