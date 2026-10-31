<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronUpIcon } from '@heroicons/vue/24/outline'
import type { Moment, MomentSeen, ScoredMoment } from '../../composables/storyMoments'
import StoryNewsCard from './StoryNewsCard.vue'
import Confetti from './Confetti.vue'
import { useCardSwipe } from '../../composables/useCardSwipe'

/**
 * Neuigkeiten von der Unterkante, über der Navigation, der Rest leicht abgedunkelt. Ein Stapel wie
 * native Karten: oben liegt eine, die nächsten schauen als Kanten heraus. Seitlich wischen oder ×
 * heißt erledigt (merkt sich nur dieses Gerät), die Karte fliegt raus und die nächste rückt nach.
 * Nach unten ziehen, ein Tipp daneben oder Escape klappt den Stapel zu einem Reiter
 * "N Neuigkeiten" über der Navigation ein; zugeklappt bleibt er für diesen Tab, bis etwas Neues
 * dazukommt. "Alle zeigen" klappt die Liste auf. data-no-swipe: Gesten auf Karte und Abdunkelung
 * gehören dem Stapel, nicht dem Seiten-Pager oder der Zurück-Geste. Nicht modal: role="region"
 * und aria-live, der Fokus bleibt auf der Seite.
 */
const props = defineProps<{
  cards: ScoredMoment[]
  carId: string | null
  carName: string
  seen: MomentSeen
}>()
const emit = defineEmits<{
  act: [moment: Moment, id: string]
  dismiss: [moment: Moment]
  shown: [moment: Moment]
  collapsed: []
  expanded: []
  list: []
}>()
const { t } = useI18n()

const COLLAPSED_KEY = 'story_news_collapsed'
const keys = computed(() => props.cards.map(c => c.moment.key).join('|'))
const readCollapsed = () => { try { return sessionStorage.getItem(COLLAPSED_KEY) } catch { return null } }
const writeCollapsed = (v: string | null) => { try { v == null ? sessionStorage.removeItem(COLLAPSED_KEY) : sessionStorage.setItem(COLLAPSED_KEY, v) } catch { /* egal */ } }

// Zugeklappt schon beim ersten Rendern, sonst gleitet die Karte kurz herein und wieder hinaus.
const mode = ref<'stack' | 'list' | 'collapsed'>(readCollapsed() === keys.value ? 'collapsed' : 'stack')
// Etwas Neues: aufklappen.
watch(keys, (now, before) => {
  if (now !== before && readCollapsed() !== now && mode.value === 'collapsed') mode.value = 'stack'
})

const count = computed(() => props.cards.length)
const current = computed<Moment | null>(() => props.cards[0]?.moment ?? null)
const open = computed(() => count.value > 0 && mode.value !== 'collapsed')
/** Kanten der nächsten Karten hinter der obersten, höchstens zwei. */
const peeks = computed(() => mode.value === 'stack' ? Math.min(2, count.value - 1) : 0)
/** Nach einem Erledigt rückt die nächste Karte aus der Kante nach vorn. */
const rising = ref(false)

/** Konfetti nur beim ersten Zeigen eines Meilensteins; danach ist er "gesehen". */
const celebrate = ref<string | null>(null)
watch([current, open], ([m, isOpen]) => {
  if (!m || !isOpen || mode.value === 'list') return
  if (m.kind === 'milestone' && props.seen.shown[m.key] == null) celebrate.value = m.key
  emit('shown', m)
}, { immediate: true })

function collapse() {
  if (!open.value) return
  mode.value = 'collapsed'
  writeCollapsed(keys.value)
  emit('collapsed')
}
function expand() {
  swipe.reset()
  rising.value = false
  mode.value = 'stack'
  writeCollapsed(null)
  emit('expanded')
}
function showList() { mode.value = 'list'; emit('list') }
function dismiss(m: Moment) { emit('dismiss', m) }
function dismissTop() {
  if (!current.value) return
  rising.value = true
  dismiss(current.value)
}
const cardEl = ref<HTMLElement | null>(null)
const swipe = useCardSwipe({
  axes: () => ({ x: mode.value === 'stack', y: true }),
  card: () => cardEl.value,
  dismiss: dismissTop,
  collapse,
})
function dismissAll() { for (const c of [...props.cards]) emit('dismiss', c.moment) }
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') collapse() }
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))

const panel = 'relative mx-auto w-full max-w-lg rounded-t-2xl border-2 border-b-0 border-gray-900 bg-white px-4 pb-[calc(env(safe-area-inset-bottom)+16px)] pt-2 dark:border-white/20 dark:bg-gray-900'
</script>

<template>
  <div>
    <Confetti v-if="celebrate" :key="celebrate" />
    <Transition name="story-news-dim">
      <div v-if="open" class="fixed inset-0 z-[55] touch-none bg-black/30" data-no-swipe data-testid="story-news-backdrop" @click="collapse" />
    </Transition>
    <Transition name="story-news">
      <section v-if="open" class="fixed inset-x-0 bottom-0 z-[60]" role="region" :aria-label="t('story.news_region')" aria-live="polite" data-no-swipe data-testid="story-news">
        <div class="touch-none select-none" :style="swipe.stackStyle.value" data-testid="story-news-drag"
             v-on="swipe.handlers" @click.capture="swipe.onClickCapture">
          <!-- Die nächsten Karten schauen oben heraus, die hintere schmaler -->
          <div v-for="i in peeks" :key="i" data-testid="story-news-peek" aria-hidden="true"
               class="mx-auto h-2.5 rounded-t-2xl border-2 border-b-0 border-gray-900 bg-gray-100 dark:border-white/20 dark:bg-gray-800"
               :class="i === peeks ? 'w-[calc(100%-2rem)] max-w-[calc(32rem-2rem)]' : 'w-[calc(100%-4rem)] max-w-[calc(32rem-4rem)]'" />
          <div :key="mode === 'stack' ? current?.key : 'list'" ref="cardEl" :class="[panel, mode === 'stack' && rising && 'story-news-rise']"
               :style="mode === 'stack' ? swipe.cardStyle.value : undefined">
            <div class="mx-auto mb-2 h-1 w-10 rounded-full bg-gray-300 dark:bg-gray-600" aria-hidden="true" />
            <template v-if="mode === 'stack' && current">
              <StoryNewsCard :moment="current" :car-id="carId" :car-name="carName" @act="emit('act', current!, $event)" @dismiss="swipe.fly(1)">
                <template v-if="count > 1" #more>
                  <button type="button" class="inline-flex min-h-11 items-center text-sm font-semibold text-gray-700 underline underline-offset-2 dark:text-gray-200" data-testid="story-news-all" @click="showList">{{ t('story.news_show_all_news') }}</button>
                </template>
              </StoryNewsCard>
            </template>
            <template v-else-if="mode === 'list'">
              <div class="mb-3 flex items-center justify-between gap-3">
                <h2 class="text-[18px] font-bold text-gray-900 dark:text-gray-50">{{ t('story.news_title', { n: count }) }}</h2>
                <button type="button" class="inline-flex min-h-11 items-center text-sm font-semibold text-gray-700 underline underline-offset-2 dark:text-gray-200" data-testid="story-news-all-done" @click="dismissAll">{{ t('story.news_all_done') }}</button>
              </div>
              <div class="grid max-h-[65vh] touch-pan-y select-text gap-3 overflow-y-auto overscroll-contain pb-1" data-scroll data-testid="story-news-list">
                <div v-for="c in cards" :key="c.moment.key" class="rounded-xl bg-white p-3.5 edge-card dark:bg-gray-700/50">
                  <StoryNewsCard :moment="c.moment" :car-id="carId" :car-name="carName" @act="emit('act', c.moment, $event)" @dismiss="dismiss(c.moment)" />
                </div>
              </div>
            </template>
          </div>
        </div>
      </section>
    </Transition>
    <Transition name="story-news-tab">
      <button v-if="count > 0 && mode === 'collapsed'" type="button"
              class="btn-3d fixed right-4 z-[50] inline-flex min-h-11 items-center gap-2 rounded-full border-2 border-gray-900 bg-gray-900 px-4 text-[14px] font-bold text-white dark:border-edge dark:bg-gray-100 dark:text-gray-900"
              style="bottom: calc(3.5rem + env(safe-area-inset-bottom) + 12px)"
              data-testid="story-news-tab" @click="expand">
        <i class="block h-2 w-2 rounded-full bg-green-500" aria-hidden="true" />
        {{ t('story.news_tab', { n: count }, count) }}
        <ChevronUpIcon class="h-4 w-4" aria-hidden="true" />
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.story-news-enter-active, .story-news-leave-active { transition: transform 260ms cubic-bezier(0.32, 0.72, 0, 1); }
.story-news-enter-from, .story-news-leave-to { transform: translateY(100%); }
.story-news-dim-enter-active, .story-news-dim-leave-active { transition: opacity 200ms ease; }
.story-news-dim-enter-from, .story-news-dim-leave-to { opacity: 0; }
.story-news-tab-enter-active, .story-news-tab-leave-active { transition: opacity 200ms ease, transform 200ms ease; }
.story-news-tab-enter-from, .story-news-tab-leave-to { opacity: 0; transform: translateY(8px); }
/* Die nächste Karte rückt aus der Kante nach vorn */
.story-news-rise { animation: story-news-rise 220ms cubic-bezier(0.32, 0.72, 0, 1); }
@keyframes story-news-rise { from { transform: translateY(10px) scale(0.97); } }
@media (prefers-reduced-motion: reduce) {
  .story-news-enter-active, .story-news-leave-active, .story-news-dim-enter-active, .story-news-dim-leave-active, .story-news-tab-enter-active, .story-news-tab-leave-active { transition: none; }
  .story-news-rise { animation: none; }
}
</style>
