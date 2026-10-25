<script setup lang="ts">
import CommunityLeaderboard from '../components/shared/CommunityLeaderboard.vue'
</script>

<template>
  <!-- Handy edge-to-edge, ab md eine Box mit Kante (App-CI) -->
  <div class="pb-6 md:mx-auto md:max-w-2xl md:p-6">
    <div class="overflow-hidden md:rounded-3xl md:edge-box">
      <CommunityLeaderboard dock-chips />
    </div>
  </div>
</template>
