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
    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
    @click.self="emit('close')"
  >
    <div class="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full max-h-[90vh] overflow-y-auto">
      <!-- Close Button -->
      <button
        @click="emit('close')"
        class="absolute top-4 right-4 z-10 p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition"
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
