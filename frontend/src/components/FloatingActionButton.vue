<script setup lang="ts">
import { PlusIcon } from '@heroicons/vue/24/outline'

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    @click="$emit('click')"
    class="fixed bottom-6 right-6 z-40 w-14 h-14 bg-green-600 hover:bg-green-700 text-white rounded-full shadow-lg hover:shadow-xl transition-all duration-200 flex items-center justify-center group"
    aria-label="Ladevorgang erfassen"
  >
    <PlusIcon class="h-7 w-7 group-hover:scale-110 transition-transform" />
  </button>
</template>
