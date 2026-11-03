<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { BoltIcon, ChevronDownIcon, ChevronUpIcon, PauseIcon, PlayIcon, ChevronRightIcon, ArrowRightIcon } from '@heroicons/vue/24/outline'
import { useTickerState, setTickerHasItems, tickerHidden } from '../../composables/useTickerState'
import { analytics } from '../../services/analytics'
import { useTickerItems, VARIANT_ICON } from '../../composables/useTickerItems'
import { useTickerRotation, dwellMs } from '../../composables/useTickerRotation'
import { useTickerPingPong, roundTripMs, scrollMs } from '../../composables/useTickerPingPong'

/**
 * Community-Ticker als Wechselanzeige: ein Eintrag zur Zeit, kurzer Übergang, dann Stillstand.
 * Überlange Texte laufen hin und her, mit Halt an jedem Ende. Zwischen zwei Wechseln rechnet der Browser nichts,
 * im Gegensatz zum früheren Endlos-Laufband (rund 20 Prozent CPU im Leerlauf).
 */
/**
 * docked: im sticky Kopf der CI-Seiten (Übersicht) statt fest unter der App-Nav; dunkles Band
 * wie im CI-Entwurf statt Indigo.
 */
const props = withDefaults(defineProps<{ docked?: boolean }>(), { docked: false })
const { t } = useI18n()
const { tickerHasItems, tickerCollapsed: collapsed, toggle } = useTickerState()

const look = computed(() => props.docked
  // In der grünen Kopfzeile: eine Stufe kräftigeres Grün als die Nav, dunkler Text, Kante unten wie die Nav.
  // Dunkel deckend (green-900 zu 40 % auf gray-950 gemischt): der Inhalt scrollt unter dem Band durch.
  ? {
    band: 'bg-green-100 border-b-2 border-gray-900 dark:bg-[#0a251d] dark:border-white/20',
    muted: 'text-green-800/70 hover:text-gray-900 dark:text-green-200/70 dark:hover:text-white',
    tab: 'bg-green-100 border-gray-900 border-2 text-green-800/80 hover:text-gray-900 dark:bg-green-950 dark:border-white/20 dark:text-green-200/80 dark:hover:text-white',
    focus: 'focus-visible:outline-gray-900/60 dark:focus-visible:outline-white/70',
  }
  : {
    band: 'bg-indigo-800',
    muted: 'text-indigo-300 hover:text-white',
    tab: 'bg-indigo-800 border border-indigo-700 text-indigo-300 hover:text-white',
    focus: 'focus-visible:outline-white/70',
  })

/* Plausible: misst, ob der Ticker überhaupt beachtet wird. Nur Typ, kein Inhalt. */
function onToggle() {
  toggle()
  analytics.track('ticker_toggle', { state: collapsed.value ? 'collapsed' : 'expanded' })
}
function onTogglePause() {
  userPaused.value = !userPaused.value
  if (userPaused.value) analytics.track('ticker_pause')
}
function onTargetClick() {
  if (current.value?.target) analytics.track('ticker_tap', { type: current.value.type, target: current.value.target })
}
const { items, fetchTicker } = useTickerItems()

const tabVisible = ref(typeof document !== 'undefined' ? !document.hidden : true)
const hovered = ref(false)
const focused = ref(false)
const userPaused = ref(false)
/** Mobil ohne Pause-Knopf: hält an, solange der Finger aufliegt (wie Hover); danach volle Lesezeit. */
const touched = ref(false)
const reducedMotion = typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** Im Kopf der CI-Seiten gibt es kein Einklappen: das Band gehört zum Kopf, eine Lasche läge über dem Inhalt. */
const isCollapsed = computed(() => !props.docked && collapsed.value)
const paused = computed(() => tickerHidden.value || isCollapsed.value || !tabVisible.value || hovered.value || focused.value || userPaused.value || touched.value)

/** Gemessene Überlänge des aktuellen Eintrags in px; 0 = passt. */
const overflowPx = ref(0)
/* Hin und Her läuft auch bei Hover und Berührung weiter (zum Mitlesen), stoppt nur beim Pause-Knopf. */
const { atEnd, reset: resetPingPong } = useTickerPingPong({
  overflowPx,
  paused: computed(() => userPaused.value || !tabVisible.value || tickerHidden.value || isCollapsed.value),
})

const count = computed(() => items.value.length)
const { index, next, prev, restart } = useTickerRotation({
  count,
  paused,
  // Überlange Einträge bleiben mindestens einmal hin und zurück stehen.
  dwell: i => Math.max(dwellMs(items.value[i]?.text.length ?? 0), roundTripMs(overflowPx.value)),
})
const current = computed(() => items.value[index.value])

/* Überlänge messen, sobald der neue Eintrag steht; dann Hin und Her per CSS-Transition. */
const viewport = ref<HTMLElement | null>(null)
async function measure() {
  overflowPx.value = 0
  resetPingPong()
  await nextTick()
  const el = viewport.value?.querySelector<HTMLElement>('[data-ticker-text]')
  if (!el || !viewport.value) return
  const over = Math.ceil(el.scrollWidth - viewport.value.clientWidth)
  if (over > 4 && !reducedMotion) {
    overflowPx.value = over
    restart()
    resetPingPong()
  }
}
watch(index, measure)

function onVisibilityChange() { tabVisible.value = !document.hidden }

/* Wischgeste auf Mobile: links = nächster, rechts = vorheriger Eintrag. */
let touchX = 0
const onTouchStart = (e: TouchEvent) => { touchX = e.touches[0].clientX; touched.value = true }
const onTouchEnd = (e: TouchEvent) => {
  touched.value = false
  const dx = e.changedTouches[0].clientX - touchX
  if (Math.abs(dx) > 40) (dx < 0 ? next : prev)()
}

const direction = ref<'fwd' | 'back'>('fwd')
watch(index, (n, o) => { direction.value = (n === 0 && o === count.value - 1) || n > o ? 'fwd' : 'back' })

const TYPE_STYLE_DOCKED = {
  LEADER: { chip: 'bg-amber-100 text-amber-700 ring-amber-300 dark:bg-amber-400/15 dark:text-amber-300 dark:ring-amber-300/30', text: 'text-gray-900 dark:text-gray-100' },
  STAT: { chip: 'bg-white text-green-700 ring-green-300 dark:bg-green-400/15 dark:text-green-300 dark:ring-green-300/30', text: 'text-gray-900 dark:text-gray-100' },
  PERSONAL: { chip: 'bg-sky-100 text-sky-700 ring-sky-300 dark:bg-sky-400/15 dark:text-sky-300 dark:ring-sky-300/30', text: 'text-gray-900 dark:text-gray-100' },
} as const
const TYPE_STYLE = {
  LEADER: { chip: 'bg-yellow-400/15 text-yellow-300 ring-yellow-300/30', text: 'text-yellow-100' },
  STAT: { chip: 'bg-emerald-400/15 text-emerald-300 ring-emerald-300/30', text: 'text-emerald-50' },
  PERSONAL: { chip: 'bg-sky-400/15 text-sky-300 ring-sky-300/30', text: 'text-sky-50' },
} as const

const typeStyle = computed(() => props.docked ? TYPE_STYLE_DOCKED : TYPE_STYLE)

onMounted(async () => {
  await fetchTicker({ withUserItems: true })
  setTickerHasItems(items.value.length > 0)
  document.addEventListener('visibilitychange', onVisibilityChange)
  measure()
})
onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <!-- top = Nav-Höhe (--top-nav-h: 0 auf Mobile, 64px auf Desktop) + Notch: der Ticker dockt
       direkt unter der Top-Nav an; auf Mobile (keine Nav) sitzt er unter der Statusbar. -->
  <section
    v-if="tickerHasItems"
    :class="docked ? 'relative z-10' : 'fixed left-0 right-0 z-39'"
    :style="docked ? undefined : 'top: calc(var(--top-nav-h) + env(safe-area-inset-top))'"
    :aria-label="t('ticker.region')"
    @mouseenter="hovered = true" @mouseleave="hovered = false"
    @focusin="focused = true" @focusout="focused = false">

    <div class="overflow-hidden transition-[height] duration-300" :class="[look.band, isCollapsed ? 'h-1' : 'h-8']">
      <!-- docked (WorkspaceFrame): ab md so breit wie die Workspace-Navigation, mobil volle Breite -->
      <div v-show="!isCollapsed" class="flex items-center h-full mx-auto" :class="docked ? 'px-4 md:max-w-6xl md:px-6' : 'max-w-5xl px-2 sm:pl-4 sm:pr-0'">

        <!-- Typ-Chip: Farbe und Icon tragen die Kategorie, damit der Text kurz bleiben kann -->
        <Transition name="ticker-chip" mode="out-in">
          <span v-if="current" :key="index"
            :class="['flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full ring-1', typeStyle[current.type].chip]"
            aria-hidden="true">
            <component :is="VARIANT_ICON[current.variant] ?? BoltIcon" class="h-3 w-3" />
          </span>
        </Transition>

        <!-- Text: Tippen öffnet das Ziel (falls vorhanden), Wischen blättert -->
        <div ref="viewport"
          class="ticker-viewport relative flex-1 min-w-0 h-full overflow-hidden ml-2 select-none"
          :class="{ 'ticker-fade-end': overflowPx > 0 && !atEnd, 'ticker-fade-start': atEnd }"
          aria-live="off"
          @touchstart.passive="onTouchStart" @touchend.passive="onTouchEnd" @touchcancel.passive="touched = false">
          <Transition :name="reducedMotion ? 'ticker-fade' : `ticker-${direction}`">
            <component :is="current.target ? RouterLink : 'div'" v-if="current" :key="index"
              v-bind="current.target ? { to: current.target } : {}"
              :class="[current.target && ['cursor-pointer rounded focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2', look.focus]]"
              class="absolute inset-0 flex items-center"
              @click="onTargetClick">
              <span
                data-ticker-text
                :class="['ticker-text text-xs font-medium whitespace-nowrap', typeStyle[current.type].text]"
                :style="overflowPx > 0 ? { transform: `translateX(-${atEnd ? overflowPx : 0}px)`, transitionDuration: `${scrollMs(overflowPx)}ms` } : undefined"
                :title="current.text">
                {{ current.text }}
              </span>
            </component>
          </Transition>
        </div>

        <!-- Ziel-Pfeil nur ab sm: mobil ist der Text selbst der Link, damit er mehr Platz hat.
             Ohne Tab-Stopp, der Text-Link ist schon fokussierbar. -->
        <RouterLink v-if="current?.target"
          :to="current.target"
          :class="look.focus"
          tabindex="-1"
          aria-hidden="true"
          class="ml-1 hidden sm:flex h-8 w-11 flex-shrink-0 items-center justify-center rounded focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-4"
          :aria-label="t('ticker.open')"
          :title="t('ticker.open')"
          @click="onTargetClick">
          <span :class="['flex h-5 w-5 items-center justify-center rounded-full ring-1', typeStyle[current.type].chip]">
            <ArrowRightIcon class="h-3 w-3" />
          </span>
        </RouterLink>

        <!-- Position, nur Desktop -->
        <span class="hidden sm:inline ml-3 text-[10px] tabular-nums" :class="look.muted" aria-hidden="true">
          {{ index + 1 }}/{{ count }}
        </span>

        <!-- Steuerung ab sm: Pause (WCAG 2.2.2) und Weiter; volle Bandhöhe als Trefferfläche.
             Mobil: Berührung hält an, Wischen blättert, Ausblenden in den Einstellungen. -->
        <button type="button"
          :class="[look.muted, look.focus]"
          class="ml-1 hidden sm:flex h-8 w-11 flex-shrink-0 items-center justify-center focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-4 rounded"
          :aria-label="userPaused ? t('ticker.play') : t('ticker.pause')"
          :aria-pressed="userPaused"
          @click="onTogglePause">
          <PlayIcon v-if="userPaused" class="h-3.5 w-3.5" />
          <PauseIcon v-else class="h-3.5 w-3.5" />
        </button>
        <button type="button"
          :class="[look.muted, look.focus]"
          class="hidden sm:flex h-8 w-9 flex-shrink-0 items-center justify-center focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-4 rounded"
          :aria-label="t('ticker.next')"
          @click="next">
          <ChevronRightIcon class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <!-- Lasche: hängt mittig unter dem Band (nicht im Kopf der CI-Seiten) -->
    <button v-if="!docked" type="button"
      @click="onToggle"
      :class="look.tab"
      class="before:absolute before:-inset-x-2 before:-top-1 before:-bottom-3 absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-full border-t-0 rounded-b-lg px-7 py-0.5 flex items-center gap-1 transition-colors"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? t('ticker.show') : t('ticker.hide')"
      :title="collapsed ? t('ticker.show') : t('ticker.hide')">
      <ChevronUpIcon v-if="!collapsed" class="h-3 w-3" />
      <ChevronDownIcon v-else class="h-3 w-3" />
    </button>
  </section>
</template>

<style scoped>
/* Hin und Her überlanger Texte, Ziel setzt useTickerPingPong. */
.ticker-text {
  transition-property: transform;
  transition-timing-function: cubic-bezier(0.45, 0, 0.55, 1);
}

/* Weicher Rand rechts, solange ein überlanger Text am Anfang steht */
.ticker-fade-end {
  mask-image: linear-gradient(to right, #000 calc(100% - 24px), transparent);
}

/* Beim Durchlauf läuft der Text links weich aus statt hart abgeschnitten */
.ticker-fade-start {
  mask-image: linear-gradient(to right, transparent, #000 16px);
}

/* Vorwärts: neuer Eintrag kommt von unten, alter geht nach oben - wie eine Anzeigetafel */
.ticker-fwd-enter-active, .ticker-fwd-leave-active,
.ticker-back-enter-active, .ticker-back-leave-active {
  transition: transform 420ms cubic-bezier(0.22, 1, 0.36, 1), opacity 300ms ease-out, filter 300ms ease-out;
}
.ticker-fwd-enter-from { transform: translateY(100%); opacity: 0; filter: blur(2px); }
.ticker-fwd-leave-to { transform: translateY(-100%); opacity: 0; filter: blur(2px); }
.ticker-back-enter-from { transform: translateY(-100%); opacity: 0; filter: blur(2px); }
.ticker-back-leave-to { transform: translateY(100%); opacity: 0; filter: blur(2px); }

.ticker-fade-enter-active, .ticker-fade-leave-active { transition: opacity 250ms linear; }
.ticker-fade-enter-from, .ticker-fade-leave-to { opacity: 0; }

.ticker-chip-enter-active { transition: transform 360ms cubic-bezier(0.34, 1.56, 0.64, 1), opacity 200ms; }
.ticker-chip-leave-active { transition: transform 140ms ease-in, opacity 140ms; }
.ticker-chip-enter-from { transform: scale(0.4) rotate(-30deg); opacity: 0; }
.ticker-chip-leave-to { transform: scale(0.6); opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .ticker-chip-enter-active, .ticker-chip-leave-active { transition: opacity 200ms; }
  .ticker-chip-enter-from, .ticker-chip-leave-to { transform: none; }
}
</style>
