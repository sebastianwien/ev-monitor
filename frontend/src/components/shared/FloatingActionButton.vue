<script lang="ts">
/** Ab dieser Scrollhöhe darf die Pille einklappen; darüber steht sie immer ausgeklappt. */
const TOP_ZONE_PX = 80
/** Kleinere Bewegungen (Trackpad-Zittern) ändern nichts. */
const MIN_DELTA_PX = 6

/** Pille beim Runterscrollen eingeklappt, beim Hochscrollen und oben auf der Seite ausgeklappt. */
export function nextExpanded(prevY: number, y: number, expanded: boolean): boolean {
  if (y < TOP_ZONE_PX) return true
  if (y - prevY > MIN_DELTA_PX) return false
  if (prevY - y > MIN_DELTA_PX) return true
  return expanded
}
</script>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon } from '@heroicons/vue/24/outline'
import { useHaptic } from '@/composables/useHaptic'

/**
 * Schwebender Erfassen-Knopf, nur Desktop. Rund wie bisher; als Pille (extended) in der CI der
 * Modellseiten: grün mit Kante und hartem Schatten, "+ Erfassen", beim Runterscrollen nur noch
 * das Plus, damit er beim Lesen nichts verdeckt.
 */
const props = withDefaults(defineProps<{ extended?: boolean }>(), { extended: false })
const emit = defineEmits<{ click: [] }>()
const { haptic } = useHaptic()
const { t } = useI18n()

const expanded = ref(true)
let lastY = 0
function onScroll() {
  const y = window.scrollY
  expanded.value = nextExpanded(lastY, y, expanded.value)
  lastY = y
}
onMounted(() => {
  if (!props.extended) return
  lastY = window.scrollY
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function handleClick() {
  haptic()
  emit('click')
}
</script>

<template>
  <button
    v-if="extended"
    type="button"
    data-testid="fab"
    class="fab-pill fixed right-6 bottom-6 z-40 flex h-14 items-center rounded-full border-2 border-gray-900 bg-green-600 pl-[14px] pr-[14px] text-white shadow-[3px_3px_0_0_#111827] transition-[transform,box-shadow,background-color] duration-150 hover:bg-green-700 active:translate-x-[3px] active:translate-y-[3px] active:shadow-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-green-600 dark:border-transparent dark:shadow-[3px_3px_0_0_#000]"
    :aria-label="t('nav.bottom.record')"
    @click="handleClick"
  >
    <PlusIcon class="h-6 w-6 shrink-0" aria-hidden="true" />
    <span
      data-testid="fab-label"
      class="fab-label overflow-hidden whitespace-nowrap text-[15px] font-semibold"
      :class="expanded ? 'ml-2 max-w-[8rem] opacity-100' : 'ml-0 max-w-0 opacity-0'"
      aria-hidden="true">{{ t('nav.bottom.record') }}</span>
  </button>
  <button
    v-else
    @click="handleClick"
    class="fixed right-6 bottom-6 z-40 w-14 h-14 bg-green-600 hover:bg-green-700 text-white rounded-full shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)] hover:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:hover:shadow-[5px_5px_0_rgba(255,255,255,0.35)] transition-all duration-200 flex items-center justify-center group"
    aria-label="Ladevorgang erfassen"
  >
    <PlusIcon class="h-7 w-7 group-hover:scale-110 transition-transform" />
  </button>
</template>

<style scoped>
.fab-label {
  transition: max-width 260ms cubic-bezier(0.2, 0.7, 0.2, 1), opacity 180ms ease, margin 260ms cubic-bezier(0.2, 0.7, 0.2, 1);
}
@media (prefers-reduced-motion: reduce) {
  .fab-label { transition: none; }
}
</style>
