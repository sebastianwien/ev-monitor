<script setup lang="ts">
import CommunityLeaderboard from '../components/shared/CommunityLeaderboard.vue'
</script>

<template>
  <!-- Handy edge-to-edge bis unter die Ticker-Lasche, ab md eine Box mit Kante (App-CI) -->
  <div class="pb-6 max-md:-mt-[var(--ticker-lasche-h)] md:mx-auto md:max-w-2xl md:px-6 md:pb-6 md:pt-[var(--page-top,1.5rem)]">
    <div class="overflow-hidden md:rounded-3xl md:edge-box">
      <CommunityLeaderboard dock-chips class="max-md:pt-[calc(1.5rem+var(--ticker-lasche-h))]" />
    </div>
  </div>
</template>
