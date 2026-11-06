<template>
  <!-- Bildmarke des Logo-CI v1.1: Blitz mit Verlauf Mint Glow, Volt Green, Deep Teal (135 Grad) -->
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 100" aria-hidden="true">
    <defs>
      <linearGradient :id="gradId" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#6AF5D0" />
        <stop offset="55%" stop-color="#00B08C" />
        <stop offset="100%" stop-color="#00755F" />
      </linearGradient>
    </defs>
    <path d="M38 0 L4 52 L24 52 L14 100 L56 40 L32 40 Z" :fill="`url(#${gradId})`" />
  </svg>
</template>

<script setup lang="ts">
import { useId } from 'vue'

const gradId = `ci-bolt-${useId()}`
</script>
