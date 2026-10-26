<script setup lang="ts">
import { computed } from 'vue'
import PublicNav from '../components/shared/PublicNav.vue'
import LeaderboardTicker from '../components/shared/LeaderboardTicker.vue'
import WorkspaceNav from '../components/shared/WorkspaceNav.vue'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'

/**
 * Gerüst des eingeloggten Bereichs im neuen Design (docs/design/ci.md): grüner Kopf mit Ticker,
 * darunter die Chip-Reihe (Desktop), darunter der Inhalt. Das Layout, das den Frame hält, bleibt
 * beim Tab-Wechsel gemountet - Kopf und Chips stehen, nur der Slot tauscht.
 * Die Routen setzen dazu meta.publicNav: App.vue lässt dann sein eigenes Chrome weg.
 */
const authStore = useAuthStore()
const countryStore = useCountryStore()
// Community-Ticker wie im alten App-Kopf: nicht im Demo-Konto, nur DACH
const showTicker = computed(() => !authStore.isDemoAccount && ['DE', 'AT', 'CH'].includes(countryStore.country))
</script>

<template>
  <div>
    <PublicNav home>
      <template #below><LeaderboardTicker v-if="showTicker" docked /></template>
    </PublicNav>
    <WorkspaceNav class="mx-auto hidden max-w-6xl px-6 py-4 md:flex" />
    <slot />
  </div>
</template>
