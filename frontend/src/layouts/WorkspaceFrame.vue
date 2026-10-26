<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue'
import PublicNav from '../components/shared/PublicNav.vue'
import LeaderboardTicker from '../components/shared/LeaderboardTicker.vue'
import WorkspaceNav from '../components/shared/WorkspaceNav.vue'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'
import { WORKSPACE_FRAME_HEAD } from './workspaceFrameHead'
import { useRoute } from 'vue-router'
import { isModelsRoute } from '../router/pageTransitions'

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
// Die Modellseiten bringen ihre eigene PublicNav mit: unter dem Gerüst tritt sie zurück, nur dieser Kopf bleibt
provide(WORKSPACE_FRAME_HEAD, computed(() => props.active))
const authStore = useAuthStore()
const countryStore = useCountryStore()
// Community-Ticker wie im alten App-Kopf: nicht im Demo-Konto, nur DACH
const showTicker = computed(() => !authStore.isDemoAccount && ['DE', 'AT', 'CH'].includes(countryStore.country))
const navFits = ref(true)
// Modellseiten bringen eigenen Hintergrund und oberen Rand mit (auch für Gäste): kein Abstandhalter, sonst
// schaut zwischen Kopf und Seite ein Streifen Hintergrundbild durch
const route = useRoute()
const pageOwnsTop = computed(() => isModelsRoute(route.name))

// Klebende Koepfe im Inhalt (Datumsbaender im Log-Feed) liegen unter dem gruenen Kopf: dessen Hoehe
// als --content-top fuer den Teilbaum, wie App.vue es fuer sein Chrome tut. Keine Lasche im Frame.
// Gemessen wird der eigene Wrapper, solange er da ist: er kommt und geht mit active.
// --page-top: den Abstand unter dem Kopf bringt das Gerüst selbst mit (Abstandhalter oder Chip-Reihe),
// Seiten, die es auch im alten Chrome gibt (/settings), lassen ihren eigenen oberen Rand dann weg.
const head = ref<HTMLElement | null>(null)
const headHeight = ref(0)
watch(head, (el, _old, onCleanup) => {
  if (!el) { headHeight.value = 0; return }
  const observer = new ResizeObserver(() => { headHeight.value = el.offsetHeight })
  observer.observe(el)
  onCleanup(() => observer.disconnect())
}, { flush: 'post' })
const frameVars = computed(() => props.active
  ? { '--content-top': `${headHeight.value}px`, '--ticker-lasche-h': '0px', '--page-top': '0px' }
  : undefined)
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
      <div v-else-if="!pageOwnsTop" class="hidden h-6 md:block" aria-hidden="true" />
    </template>
    <slot />
  </div>
</template>
