<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { EllipsisVerticalIcon } from '@heroicons/vue/24/outline'

/**
 * Aktionen einer Feed-Zeile hinter dem Drei-Punkte-Knopf, ein Baustein fuer alle Zeilen
 * (Ladung, Teilladung, Ladegruppe, Fahrt). Haelt den offenen Zustand selbst; ein Tipp
 * daneben oder Escape schliesst. Die Eintraege kommen als Slot und bekommen `close`.
 * `up` oeffnet nach oben, fuer Zeilen am Ende einer Karte.
 */
withDefaults(defineProps<{ placement?: 'up' | 'down' }>(), { placement: 'down' })
const open = ref(false)
const { t } = useI18n()
function close() { open.value = false }
</script>

<template>
  <div class="relative" @click.stop @mousedown.stop @keydown.esc="close">
    <button type="button" :aria-label="t('dashboard.action_menu_label') || 'Aktionen'" aria-haspopup="menu" :aria-expanded="open"
      class="grid h-10 w-10 place-items-center rounded-full text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-story-accent dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-gray-100"
      @click="open = !open">
      <EllipsisVerticalIcon class="h-5 w-5" aria-hidden="true" />
    </button>
    <template v-if="open">
      <div class="fixed inset-0 z-40" @click="close" />
      <div role="menu" class="absolute right-0 z-50 w-56 overflow-hidden rounded-xl bg-white py-1 edge-card dark:bg-gray-800"
        :class="placement === 'up' ? 'bottom-full mb-1' : 'top-full mt-1'">
        <slot :close="close" />
      </div>
    </template>
  </div>
</template>
