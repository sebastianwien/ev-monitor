<script setup lang="ts">
import { ChevronRightIcon } from '@heroicons/vue/24/outline'

/**
 * Chip, der etwas oeffnet (SoH-Details, eigene Bewertung, Teilen). CI-Indigo mit hartem
 * Versatzschatten wie btn-3d und Chevron, gibt beim Tippen nach. Info-Chips daneben sind
 * dunkleres Indigo und flach. Gleiche Form ueberall, damit "klickbar" immer gleich aussieht.
 */
withDefaults(defineProps<{
  /** sm: dichte Spec-Zeile, md: Standard, lg: Fahrzeug-Hero mit 44 px Tap-Target mobil. */
  size?: 'sm' | 'md' | 'lg'
}>(), { size: 'md' })

defineEmits<{ click: [] }>()

// Schattenversatz waechst mit der Groesse, sonst wirkt der kleine Chip wie verrutscht.
const sizeClass = {
  sm: 'gap-1 px-2 py-0.5 text-[11px] shadow-[2px_2px_0_0_var(--chip-shadow)] active:translate-x-px active:translate-y-px',
  md: 'gap-1 px-2.5 py-1 text-xs shadow-[3px_3px_0_0_var(--chip-shadow)] active:translate-x-[2px] active:translate-y-[2px]',
  lg: 'gap-1.5 min-h-11 md:min-h-9 pl-3 pr-2.5 text-sm shadow-[3px_3px_0_0_var(--chip-shadow)] active:translate-x-[2px] active:translate-y-[2px]',
}
</script>

<template>
  <button
    type="button"
    class="inline-flex items-center whitespace-nowrap rounded-full font-semibold transition-[transform,box-shadow,background-color] duration-75 bg-indigo-600 text-white hover:bg-indigo-500 [--chip-shadow:#a5b4fc] dark:[--chip-shadow:#3730a3] active:shadow-none touch-manipulation focus:outline-hidden focus-visible:ring-2 focus-visible:ring-indigo-300 focus-visible:ring-offset-2 focus-visible:ring-offset-gray-900"
    :class="sizeClass[size]"
    @click.stop="$emit('click')"
  >
    <slot />
    <ChevronRightIcon class="flex-none opacity-80" :class="size === 'lg' ? 'h-4 w-4' : 'h-3.5 w-3.5'" aria-hidden="true" />
  </button>
</template>
