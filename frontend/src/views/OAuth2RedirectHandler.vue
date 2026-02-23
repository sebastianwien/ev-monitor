<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

onMounted(() => {
  const token = route.query.token as string;
  if (token) {
    authStore.setToken(token);
    router.push('/');
  } else {
    // No token found, maybe redirect to login with error
    router.push('/login');
  }
});
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh]">
    <div class="text-xl text-gray-600">Completing sign in...</div>
  </div>
</template>
