<script setup lang="ts">
import { useI18n } from 'vue-i18n'

/** Schliessen-Knopf der Story-Sheets: gleiche Kante wie die Chips (ChoiceChip), volle Breite. */
defineEmits<{ click: [] }>()
const { t } = useI18n()
</script>

<template>
  <button
    type="button"
    class="btn-3d cta-shadow-sm flex min-h-11 w-full items-center justify-center rounded-full border-2 border-gray-900 bg-white text-sm font-semibold text-gray-800 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:border-transparent dark:bg-white/10 dark:text-gray-200 dark:shadow-none! dark:hover:bg-white/15"
    @click="$emit('click')">
    {{ t('story.sheet_close') }}
  </button>
</template>
