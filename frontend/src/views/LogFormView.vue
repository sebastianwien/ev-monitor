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
  <div class="md:max-w-4xl md:mx-auto">
    <LogForm @success="router.push('/dashboard')" @cancel="handleCancel" />
  </div>
</template>
