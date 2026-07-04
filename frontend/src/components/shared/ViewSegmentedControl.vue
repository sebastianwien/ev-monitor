<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useHaptic } from '../../composables/useHaptic'

/**
 * Tab-Umschalter zwischen Dashboard-Uebersicht und Log-Feed (nur Mobile, im
 * CarContextLayout). Die zwei Segmente sind die frueheren Headlines. Der aktive
 * Zustand kommt aus der Route; ein Pill-Indikator gleitet zwischen den Tabs.
 */
const route = useRoute()
const { t } = useI18n()
const { haptic } = useHaptic()

const isLogs = computed(() => route.name === 'logs')

const labelClass = (active: boolean) =>
  `relative z-10 flex-1 text-center text-sm font-semibold py-1.5 transition-colors ${
    active ? 'text-indigo-600 dark:text-indigo-300' : 'text-gray-500 dark:text-gray-400'
  }`
</script>

<template>
  <div class="relative flex p-1 rounded-md bg-gray-100 dark:bg-gray-800 select-none" role="tablist">
    <!-- Gleitender Indikator (haelfte minus Padding) -->
    <div
      class="absolute top-1 bottom-1 left-1 w-[calc(50%-0.25rem)] rounded-sm bg-white dark:bg-gray-700 shadow-sm transition-transform duration-200 ease-out"
      :style="{ transform: isLogs ? 'translateX(100%)' : 'translateX(0)' }"
      aria-hidden="true"
    />
    <router-link to="/dashboard" role="tab" :aria-selected="!isLogs" :class="labelClass(!isLogs)" @click="haptic()">
      {{ t('nav.tab_overview') }}
    </router-link>
    <router-link to="/logs" role="tab" :aria-selected="isLogs" :class="labelClass(isLogs)" @click="haptic()">
      {{ t('logs.title') }}
    </router-link>
  </div>
</template>
