<script setup lang="ts">
import { useRouter } from 'vue-router'
import LogForm from '../components/log-form/LogForm.vue'

const router = useRouter()

const handleCancel = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}
</script>

<template>
  <div class="md:max-w-2xl md:mx-auto md:mt-8 bg-white dark:bg-gray-800 md:rounded-sm md:shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:md:shadow-[4px_4px_0_rgba(255,255,255,0.30)]">
    <LogForm @success="router.push('/dashboard')" @cancel="handleCancel" />
  </div>
</template>
