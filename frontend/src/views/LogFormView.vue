<script setup lang="ts">
import { useRouter } from 'vue-router'
import LogForm from '../components/LogForm.vue'

const router = useRouter()
</script>

<template>
  <div class="md:max-w-4xl md:mx-auto">
    <LogForm @success="router.push('/dashboard')" />
  </div>
</template>
