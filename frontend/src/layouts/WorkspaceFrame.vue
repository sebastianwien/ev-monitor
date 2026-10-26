<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import PublicNav from '../components/shared/PublicNav.vue'
import LeaderboardTicker from '../components/shared/LeaderboardTicker.vue'
import WorkspaceNav from '../components/shared/WorkspaceNav.vue'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'

/**
 * Gerüst des eingeloggten Bereichs im neuen Design (docs/design/ci.md): grüner Kopf mit Ticker,
 * darunter der Inhalt. Die vier Bereiche stehen als Reiter in der Navbar, sobald sie neben die rechte Gruppe
 * passen (PublicNav misst das, navFits), sonst ab md als Chip-Reihe unter dem Kopf; mobil trägt die Bottom-Nav.
 * App.vue legt es einmal um die router-view, nicht jedes Layout für sich: so bleiben Kopf, Ticker und
 * Reiter auch beim Wechsel zwischen den Layouts (Übersicht/Logs gegen Fahrzeuge/Ladekarten) stehen,
 * nur der Inhalt tauscht. Die Routen tragen dazu meta.publicNav, App.vue lässt dann sein eigenes Chrome weg.
 */
/** false: nur der Inhalt (altes Design, andere Routen); der Slot bleibt an derselben Stelle und wird nicht neu gemountet. */
const props = defineProps<{ active: boolean }>()
const authStore = useAuthStore()
const countryStore = useCountryStore()
// Community-Ticker wie im alten App-Kopf: nicht im Demo-Konto, nur DACH
const showTicker = computed(() => !authStore.isDemoAccount && ['DE', 'AT', 'CH'].includes(countryStore.country))
const navFits = ref(true)

// Klebende Koepfe im Inhalt (Datumsbaender im Log-Feed) liegen unter dem gruenen Kopf: dessen Hoehe
// als --content-top fuer den Teilbaum, wie App.vue es fuer sein Chrome tut. Keine Lasche im Frame.
// Gemessen wird der eigene Wrapper, solange er da ist: er kommt und geht mit active.
const head = ref<HTMLElement | null>(null)
const headHeight = ref(0)
watch(head, (el, _old, onCleanup) => {
  if (!el) { headHeight.value = 0; return }
  const observer = new ResizeObserver(() => { headHeight.value = el.offsetHeight })
  observer.observe(el)
  onCleanup(() => observer.disconnect())
}, { flush: 'post' })
const frameVars = computed(() => props.active ? { '--content-top': `${headHeight.value}px`, '--ticker-lasche-h': '0px' } : undefined)
</script>

<template>
  <div :style="frameVars">
    <template v-if="active">
      <div ref="head" class="sticky top-0 z-50">
        <PublicNav home @nav-fits="navFits = $event">
          <template #nav><WorkspaceNav variant="bar" class="hidden md:flex" /></template>
          <template #below><LeaderboardTicker v-if="showTicker" docked /></template>
        </PublicNav>
      </div>
      <WorkspaceNav v-if="!navFits" class="mx-auto hidden max-w-6xl px-6 py-4 md:flex" />
      <div v-else class="hidden h-6 md:block" aria-hidden="true" />
    </template>
    <slot />
  </div>
</template>
