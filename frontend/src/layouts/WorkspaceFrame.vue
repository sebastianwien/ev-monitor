<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
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

// Klebende Koepfe im Inhalt (Datumsbaender im Log-Feed) liegen unter dem gruenen Kopf: dessen Hoehe
// als --content-top fuer den Teilbaum, wie App.vue es fuer sein Chrome tut. Keine Lasche im Frame.
// Gemessen wird der eigene Wrapper: PublicNav rendert erst, wenn die Route publicNav traegt - beim
// Einschalten des neuen Designs mountet der Frame aber schon vor dem Routenwechsel.
const head = ref<HTMLElement | null>(null)
const headHeight = ref(0)
let observer: ResizeObserver | null = null
onMounted(() => {
  const el = head.value
  if (!el) return
  observer = new ResizeObserver(() => { headHeight.value = el.offsetHeight })
  observer.observe(el)
})
onUnmounted(() => observer?.disconnect())
const frameVars = computed(() => ({ '--content-top': `${headHeight.value}px`, '--ticker-lasche-h': '0px' }))
</script>

<template>
  <div :style="frameVars">
    <div ref="head" class="sticky top-0 z-50">
      <PublicNav home>
        <template #below><LeaderboardTicker v-if="showTicker" docked /></template>
      </PublicNav>
    </div>
    <WorkspaceNav class="mx-auto hidden max-w-6xl px-6 py-4 md:flex" />
    <slot />
  </div>
</template>
