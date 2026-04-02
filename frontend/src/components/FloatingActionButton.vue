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
    class="fixed bottom-6 right-6 z-40 w-14 h-14 bg-green-600 hover:bg-green-700 text-white rounded-full shadow-lg hover:shadow-xl transition-all duration-200 flex items-center justify-center group"
    aria-label="Ladevorgang erfassen"
  >
    <PlusIcon class="h-7 w-7 group-hover:scale-110 transition-transform" />
  </button>
</template>
