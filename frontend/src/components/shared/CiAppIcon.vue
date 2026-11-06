<template>
  <!-- App-Icon klein des Logo-CI v1.1 (Variante H): Fläche Night Green bis Deep Night, Rundung 22,5 %,
       Kreis in Mist 76 % mittig, Blitz mit Verlauf. Ohne Rauschen, das trägt erst die Datei aus dem CI. -->
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" aria-hidden="true">
    <defs>
      <linearGradient :id="`${id}-face`" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#0B4A3D" />
        <stop offset="100%" stop-color="#04201A" />
      </linearGradient>
      <linearGradient :id="`${id}-bolt`" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#6AF5D0" />
        <stop offset="55%" stop-color="#00B08C" />
        <stop offset="100%" stop-color="#00755F" />
      </linearGradient>
    </defs>
    <rect width="100" height="100" rx="22.5" :fill="`url(#${id}-face)`" />
    <circle cx="50" cy="50" r="38" fill="#DFF7EF" />
    <svg x="35.4" y="25.7" width="29.2" height="48.6" viewBox="0 0 60 100">
      <path d="M38 0 L4 52 L24 52 L14 100 L56 40 L32 40 Z" :fill="`url(#${id}-bolt)`" />
    </svg>
  </svg>
</template>

<script setup lang="ts">
import { useId } from 'vue'

const id = `ci-app-${useId()}`
</script>
