<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import LogForm from './LogForm.vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'

const emit = defineEmits<{
  close: []
}>()

const handleEscape = (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape)
  document.body.style.overflow = 'hidden'
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape)
  document.body.style.overflow = ''
})
</script>

<template>
  <div
    class="fixed inset-0 z-[1000] flex items-center justify-center sm:p-4 bg-black/50 backdrop-blur-sm"
    @click.self="emit('close')"
  >
    <div class="relative bg-white dark:bg-gray-800 w-full h-full sm:h-auto sm:rounded-2xl sm:shadow-2xl sm:max-w-4xl sm:max-h-[90vh] overflow-y-auto">
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-10 p-2 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition"
        aria-label="Schließen"
      >
        <XMarkIcon class="h-6 w-6" />
      </button>

      <!-- LogForm -->
      <div class="p-6">
        <LogForm @success="emit('close')" />
      </div>
    </div>
  </div>
</template>
