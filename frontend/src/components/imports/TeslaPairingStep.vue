<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTopRightOnSquareIcon, ArrowPathIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import type { TeslaPairingStatus } from '@/api/teslaFleetService'
import { useAuthStore } from '@/stores/auth'

/**
 * Schritt 2 der Tesla-Einrichtung: Virtual Key in der Tesla-App pairen, danach
 * Telemetry aktivieren. Rein darstellend - den Zustand haelt `useTeslaPairing`
 * beim Aufrufer (Tesla-Sektion in /imports, Modal in /cars).
 */
defineProps<{
  status: TeslaPairingStatus | null
  loading: boolean
  error: string | null
}>()

defineEmits<{
  (e: 'enable'): void
  (e: 'refresh'): void
}>()

const { t } = useI18n()
const authStore = useAuthStore()

// Rollenabhaengige Kennzeichnung: Beta-Tester helfen bei der Trip-Erkennung,
// Founder behalten Live-Sync dauerhaft gratis.
const roleBadge = computed(() => {
  if (authStore.isAdmin) return 'Beta · Admin'
  if (authStore.isBetaTester) return 'Beta'
  if (authStore.isTeslaFounder) return t('tesla.pairing_founder_badge')
  return ''
})
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-center gap-2">
      <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-gray-700 dark:text-gray-300">
        {{ t('tesla.pairing_title') }}
      </p>
      <span v-if="roleBadge" class="text-[10px] font-bold uppercase tracking-wider bg-amber-500 text-gray-950 px-1.5 py-0.5 rounded-sm">{{ roleBadge }}</span>
    </div>

    <div v-if="status" class="border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm p-3 text-xs space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('tesla.pairing_key_label') }}</span>
        <span :class="status.keyPaired
          ? 'bg-emerald-500 text-white'
          : 'bg-amber-500 text-gray-950'"
          class="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-sm">
          {{ status.keyPaired ? t('tesla.pairing_key_ok') : t('tesla.pairing_key_missing') }}
        </span>
      </div>
      <details v-if="!status.keyPaired" class="pt-0.5">
        <summary class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 cursor-pointer">{{ t('tesla.pairing_key_unreliable_summary') }}</summary>
        <p class="text-xs text-gray-600 dark:text-gray-400 mt-2 font-medium leading-relaxed">{{ t('tesla.pairing_key_unreliable_hint') }}</p>
      </details>
      <div class="flex items-center justify-between">
        <span class="text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('tesla.pairing_config_label') }}</span>
        <span :class="status.telemetryConfigPushed
          ? 'bg-emerald-500 text-white'
          : 'bg-gray-300 dark:bg-gray-700 text-gray-700 dark:text-gray-300'"
          class="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-sm">
          {{ status.telemetryConfigPushed ? t('tesla.pairing_config_ok') : t('tesla.pairing_config_missing') }}
        </span>
      </div>
    </div>
    <div v-else-if="loading" class="flex items-center justify-center py-2">
      <ArrowPathIcon class="h-4 w-4 text-amber-500 animate-spin" />
    </div>

    <a
      v-if="!status?.keyPaired"
      href="https://tesla.com/_ak/ev-monitor.net"
      target="_blank"
      rel="noopener"
      class="w-full inline-flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
    >
      <ArrowTopRightOnSquareIcon class="h-4 w-4" />
      {{ t('tesla.pairing_open_app_btn') }}
    </a>

    <button
      v-if="status"
      @click="$emit('enable')"
      :disabled="loading"
      class="w-full inline-flex items-center justify-center gap-2 bg-red-600 hover:bg-red-500 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-red-600 disabled:border-gray-300 dark:disabled:border-gray-700 shadow-[2px_2px_0_0_#030712] disabled:shadow-none active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
    >
      <ArrowPathIcon class="h-4 w-4" :class="{ 'animate-spin': loading }" />
      {{ loading ? t('tesla.pairing_enable_btn_loading') : t('tesla.pairing_enable_btn') }}
    </button>

    <button
      @click="$emit('refresh')"
      :disabled="loading"
      class="w-full text-[11px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 text-center transition disabled:opacity-50"
    >
      {{ t('tesla.pairing_refresh') }}
    </button>

    <div v-if="error" class="flex items-center justify-center gap-2">
      <ExclamationTriangleIcon class="h-4 w-4 text-red-500 dark:text-red-400 shrink-0" />
      <p class="text-xs text-red-700 dark:text-red-300">{{ error }}</p>
    </div>
  </div>
</template>
