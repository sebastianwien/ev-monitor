<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrophyIcon, BoltIcon, SparklesIcon, BanknotesIcon, NewspaperIcon, ArrowTopRightOnSquareIcon, ChevronDownIcon, ChevronUpIcon, PauseIcon, PlayIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useTickerState } from '../../composables/useTickerState'
import { useTickerItems } from '../../composables/useTickerItems'
import { useTickerRotation, dwellMs } from '../../composables/useTickerRotation'

/**
 * Community-Ticker als Wechselanzeige: ein Eintrag zur Zeit, kurzer Übergang, dann Stillstand.
 * Überlange Texte laufen genau einmal durch. Zwischen zwei Wechseln rechnet der Browser nichts,
 * im Gegensatz zum früheren Endlos-Laufband (rund 20 Prozent CPU im Leerlauf).
 */
const { t } = useI18n()
const { tickerHasItems, tickerCollapsed: collapsed, toggle } = useTickerState()
const { items, fetchTicker } = useTickerItems()

const tabVisible = ref(typeof document !== 'undefined' ? !document.hidden : true)
const hovered = ref(false)
const focused = ref(false)
const userPaused = ref(false)
const reducedMotion = typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const paused = computed(() => collapsed.value || !tabVisible.value || hovered.value || focused.value || userPaused.value)

/** Gemessene Durchlaufzeit überlanger Einträge, je Index. */
const OVERFLOW_PX_PER_S = 40
const OVERFLOW_DELAY_MS = 1200
const overflowPx = ref(0)
const overflowMs = computed(() => overflowPx.value > 0 ? OVERFLOW_DELAY_MS + (overflowPx.value / OVERFLOW_PX_PER_S) * 1000 : 0)

const count = computed(() => items.value.length)
const { index, next, prev, restart } = useTickerRotation({
  count,
  paused,
  dwell: i => dwellMs(items.value[i]?.text.length ?? 0, overflowMs.value),
})
const current = computed(() => items.value[index.value])

/* Überlänge messen, sobald der neue Eintrag steht; dann einmaliger Durchlauf per CSS-Transition. */
const viewport = ref<HTMLElement | null>(null)
const scrolling = ref(false)
let scrollTimer: ReturnType<typeof setTimeout> | undefined
async function measure() {
  clearTimeout(scrollTimer)
  scrolling.value = false
  overflowPx.value = 0
  await nextTick()
  const el = viewport.value?.querySelector<HTMLElement>('[data-ticker-text]')
  if (!el || !viewport.value) return
  const over = Math.ceil(el.scrollWidth - viewport.value.clientWidth)
  if (over > 4 && !reducedMotion) {
    overflowPx.value = over
    restart()
    scrollTimer = setTimeout(() => { scrolling.value = true }, OVERFLOW_DELAY_MS)
  }
}
watch(index, measure)

function onVisibilityChange() { tabVisible.value = !document.hidden }

/* Wischgeste auf Mobile: links = nächster, rechts = vorheriger Eintrag. */
let touchX = 0
const onTouchStart = (e: TouchEvent) => { touchX = e.touches[0].clientX }
const onTouchEnd = (e: TouchEvent) => {
  const dx = e.changedTouches[0].clientX - touchX
  if (Math.abs(dx) > 40) (dx < 0 ? next : prev)()
}

const direction = ref<'fwd' | 'back'>('fwd')
watch(index, (n, o) => { direction.value = (n === 0 && o === count.value - 1) || n > o ? 'fwd' : 'back' })

const VARIANT_ICON = { leader: TrophyIcon, eco: SparklesIcon, money: BanknotesIcon, news: NewspaperIcon, energy: BoltIcon } as const
const TYPE_STYLE = {
  LEADER: { chip: 'bg-yellow-400/15 text-yellow-300 ring-yellow-300/30', text: 'text-yellow-100' },
  STAT: { chip: 'bg-emerald-400/15 text-emerald-300 ring-emerald-300/30', text: 'text-emerald-50' },
  NEWS: { chip: 'bg-lime-400/15 text-lime-300 ring-lime-300/30', text: 'text-lime-50' },
} as const

onMounted(async () => {
  await fetchTicker()
  tickerHasItems.value = items.value.length > 0
  document.addEventListener('visibilitychange', onVisibilityChange)
  measure()
})
onUnmounted(() => {
  clearTimeout(scrollTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <!-- top = Nav-Höhe (--top-nav-h: 0 auf Mobile, 64px auf Desktop) + Notch: der Ticker dockt
       direkt unter der Top-Nav an; auf Mobile (keine Nav) sitzt er unter der Statusbar. -->
  <section
    v-if="items.length > 0"
    class="fixed left-0 right-0 z-39"
    style="top: calc(var(--top-nav-h) + env(safe-area-inset-top))"
    :aria-label="t('ticker.region')"
    @mouseenter="hovered = true" @mouseleave="hovered = false"
    @focusin="focused = true" @focusout="focused = false">

    <div class="bg-indigo-800 overflow-hidden transition-[height] duration-300" :class="collapsed ? 'h-1' : 'h-8'">
      <div v-show="!collapsed" class="flex items-center h-full max-w-5xl mx-auto pl-2 sm:pl-4">

        <!-- Typ-Chip: Farbe und Icon tragen die Kategorie, damit der Text kurz bleiben kann -->
        <Transition name="ticker-chip" mode="out-in">
          <span v-if="current" :key="index"
            :class="['flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full ring-1', TYPE_STYLE[current.type].chip]"
            aria-hidden="true">
            <component :is="VARIANT_ICON[current.variant] ?? BoltIcon" class="h-3 w-3" />
          </span>
        </Transition>

        <!-- Text: Tippen schaltet weiter, Wischen blättert -->
        <div ref="viewport"
          class="ticker-viewport relative flex-1 min-w-0 h-full overflow-hidden ml-2 cursor-pointer select-none"
          :class="{ 'ticker-fade-end': overflowPx > 0 && !scrolling, 'ticker-fade-start': scrolling }"
          aria-live="off"
          @click="next"
          @touchstart.passive="onTouchStart" @touchend.passive="onTouchEnd">
          <Transition :name="reducedMotion ? 'ticker-fade' : `ticker-${direction}`">
            <div v-if="current" :key="index" class="absolute inset-0 flex items-center">
              <a v-if="current.type === 'NEWS' && current.url"
                data-ticker-text
                :href="current.url" target="_blank" rel="noopener noreferrer"
                :class="['ticker-text flex items-center gap-1 text-xs font-medium whitespace-nowrap underline decoration-lime-400/40 underline-offset-2 hover:decoration-lime-300', TYPE_STYLE.NEWS.text]"
                :style="scrolling ? { transform: `translateX(-${overflowPx}px)`, transitionDuration: `${overflowPx / OVERFLOW_PX_PER_S}s` } : undefined"
                @click.stop>
                {{ current.text }}
                <ArrowTopRightOnSquareIcon class="h-3 w-3 flex-shrink-0 opacity-60" />
              </a>
              <span v-else
                data-ticker-text
                :class="['ticker-text text-xs font-medium whitespace-nowrap', TYPE_STYLE[current.type].text]"
                :style="scrolling ? { transform: `translateX(-${overflowPx}px)`, transitionDuration: `${overflowPx / OVERFLOW_PX_PER_S}s` } : undefined"
                :title="current.text">
                {{ current.text }}
              </span>
            </div>
          </Transition>
        </div>

        <!-- Position, nur Desktop -->
        <span class="hidden sm:inline ml-3 text-[10px] tabular-nums text-indigo-300/80" aria-hidden="true">
          {{ index + 1 }}/{{ count }}
        </span>

        <!-- Steuerung: Pause (WCAG 2.2.2) und Weiter; volle Bandhöhe als Trefferfläche -->
        <button type="button"
          class="ml-1 flex h-8 w-9 flex-shrink-0 items-center justify-center text-indigo-300 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-white/70 focus-visible:-outline-offset-4 rounded"
          :aria-label="userPaused ? t('ticker.play') : t('ticker.pause')"
          :aria-pressed="userPaused"
          @click="userPaused = !userPaused">
          <PlayIcon v-if="userPaused" class="h-3.5 w-3.5" />
          <PauseIcon v-else class="h-3.5 w-3.5" />
        </button>
        <button type="button"
          class="hidden sm:flex h-8 w-9 flex-shrink-0 items-center justify-center text-indigo-300 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-white/70 focus-visible:-outline-offset-4 rounded"
          :aria-label="t('ticker.next')"
          @click="next">
          <ChevronRightIcon class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <!-- Lasche: hängt mittig unter dem Band -->
    <button type="button"
      @click="toggle"
      class="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-full bg-indigo-800 border border-t-0 border-indigo-700 rounded-b-lg px-7 py-0.5 flex items-center gap-1 text-indigo-300 hover:text-white transition-colors"
      :aria-expanded="!collapsed"
      :title="collapsed ? 'Ticker einblenden' : 'Ticker ausblenden'">
      <ChevronUpIcon v-if="!collapsed" class="h-3 w-3" />
      <ChevronDownIcon v-else class="h-3 w-3" />
    </button>
  </section>
</template>

<style scoped>
/* Einmaliger Durchlauf überlanger Texte; läuft aus und bleibt stehen. */
.ticker-text {
  transition-property: transform;
  transition-timing-function: cubic-bezier(0.45, 0, 0.55, 1);
}

/* Weicher Rand rechts, solange ein überlanger Text noch nicht gelaufen ist */
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
