<script setup lang="ts">
import { ChevronRightIcon } from '@heroicons/vue/24/outline'

/**
 * Chip, der etwas oeffnet (SoH-Details, eigene Bewertung, Teilen). Aussehen wie ChoiceChip
 * (App-CI: Rand und harter Schatten in der Kantenfarbe) plus Chevron, gibt beim Tippen nach.
 * Info-Chips daneben sind flach und ohne Rand. Gleiche Form ueberall, damit "klickbar" immer
 * gleich aussieht.
 */
withDefaults(defineProps<{
  /** sm: dichte Spec-Zeile, md: Standard, lg: Fahrzeug-Hero mit 44 px Tap-Target mobil. */
  size?: 'sm' | 'md' | 'lg'
}>(), { size: 'md' })

defineEmits<{ click: [] }>()

const sizeClass = {
  sm: 'gap-1 px-2 py-0.5 text-[11px]',
  md: 'gap-1 px-2.5 py-1 text-xs',
  lg: 'gap-1.5 min-h-11 md:min-h-9 pl-3 pr-2.5 text-sm',
}
</script>

<template>
  <button
    type="button"
    class="btn-3d cta-shadow-sm inline-flex items-center whitespace-nowrap rounded-full border-2 border-edge bg-white font-semibold text-gray-900 transition-colors hover:bg-gray-50 touch-manipulation focus:outline-hidden focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-gray-800 dark:text-gray-100 dark:hover:bg-gray-700"
    :class="sizeClass[size]"
    @click.stop="$emit('click')"
  >
    <slot />
    <ChevronRightIcon class="flex-none opacity-80" :class="size === 'lg' ? 'h-4 w-4' : 'h-3.5 w-3.5'" aria-hidden="true" />
  </button>
</template>
