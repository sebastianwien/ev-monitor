<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import LogForm from './LogForm.vue'

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
      <!-- LogForm -->
      <div class="p-6">
        <LogForm @success="emit('close')" @cancel="emit('close')" />
      </div>
    </div>
  </div>
</template>
