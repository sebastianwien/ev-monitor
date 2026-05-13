<script setup lang="ts">
import { PlusIcon } from '@heroicons/vue/24/outline'
import { useHaptic } from '@/composables/useHaptic'

const emit = defineEmits<{ click: [] }>()
const { haptic } = useHaptic()

function handleClick() {
  haptic()
  emit('click')
}
</script>

<template>
  <button
    @click="handleClick"
    class="fixed right-6 z-40 w-14 h-14 bg-green-600 hover:bg-green-700 text-white rounded-full shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] hover:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:hover:shadow-[5px_5px_0_rgba(255,255,255,0.35)] transition-all duration-200 flex items-center justify-center group"
    style="bottom: calc(1.5rem + var(--bulk-bar-offset, 0px));"
    aria-label="Ladevorgang erfassen"
  >
    <PlusIcon class="h-7 w-7 group-hover:scale-110 transition-transform" />
  </button>
</template>
