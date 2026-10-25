<script setup lang="ts">
import CommunityLeaderboard from '../components/shared/CommunityLeaderboard.vue'
</script>

<template>
  <!-- Handy edge-to-edge, ab md eine Box mit Kante (App-CI) -->
  <div class="min-h-full bg-gray-100 pb-6 md:mx-auto md:max-w-2xl md:bg-transparent md:p-6 dark:bg-gray-950 md:dark:bg-transparent">
    <div class="overflow-hidden md:rounded-3xl md:edge-box">
      <CommunityLeaderboard />
    </div>
  </div>
</template>
