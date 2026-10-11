<template>
  <!-- The visitor's range band, one line that follows the reader through the four chapters:
       sticky at the bottom on the phone, a side card from md up (the parent places it). -->
  <div :key="glowKey" class="relative rounded-2xl bg-gray-900 text-white shadow-lg shadow-gray-900/20 dark:bg-gray-100 dark:text-gray-900 dark:shadow-black/40" :class="glowKey > 0 ? 'band-drop motion-safe:animate-[band-glow_1s_ease-out]' : ''" data-testid="range-band-bar">
    <div class="grid gap-2.5 px-4 py-3.5 md:gap-3.5 md:px-5 md:py-5">
      <div class="flex gap-1.5" :aria-label="t('model.story.band_progress', { n: answered, total })" role="img">
        <i v-for="n in total" :key="n" class="h-1 flex-1 rounded-full motion-safe:transition-colors motion-safe:duration-500" :class="n <= answered ? 'bg-green-500 dark:bg-green-600' : 'bg-white/25 dark:bg-gray-900/20'" aria-hidden="true"></i>
      </div>
      <p class="sr-only">{{ t('model.story.band_progress', { n: answered, total }) }}</p>

      <!-- The profile the page collects: one row per chapter question, from md up (the phone keeps the bar short) -->
      <dl v-if="profile.length" class="hidden gap-1 md:grid" data-testid="range-band-profile">
        <p class="text-[11.5px] font-semibold uppercase tracking-[0.08em] opacity-75 md:text-[12.5px]">{{ t('model.story.band_profile_title') }}</p>
        <div v-for="row in profile" :key="row.key" class="flex items-baseline justify-between gap-3 text-[13px] leading-snug md:text-[15px] md:leading-relaxed">
          <dt class="opacity-75">{{ row.label }}</dt>
          <dd class="text-right font-semibold" :class="row.value ? '' : 'font-normal opacity-50'">{{ row.value ?? t('model.story.band_open') }}</dd>
        </div>
      </dl>

      <div class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 md:grid-cols-1">
        <template v-if="band">
          <p class="text-[13px] leading-snug opacity-75 md:text-[15px]">{{ t(remote ? 'model.story.band_title' : 'model.story.band_title_model') }}</p>
          <button type="button" data-testid="range-band-cta"
                  class="row-span-2 inline-flex min-h-11 items-center justify-center rounded-xl bg-green-500 px-4 font-semibold text-gray-950 hover:bg-green-400 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-500 md:order-last md:row-span-1 md:mt-1 dark:bg-green-600 dark:text-white dark:hover:bg-green-700"
                  @click="emit('open')">
            {{ t('model.story.band_cta') }}
          </button>
          <p class="text-[24px] font-bold leading-none tracking-tight tabular-nums md:text-[32px]" :class="pulseKey > 0 ? 'motion-safe:animate-[band-pulse_0.6s_ease-out]' : ''" data-testid="range-band" aria-live="polite">
            {{ t('model.story.band_value', { min: formatDistance(band.minKm, { showUnit: false }), max: formatDistance(band.maxKm) }) }}
          </p>
          <p class="col-span-2 text-[13px] leading-snug opacity-75 md:col-span-1 md:text-[14px]" :class="degraded ? '' : 'hidden md:block'">
            <template v-if="degraded">{{ t('model.story.band_standard') }}. </template>{{ t('model.story.band_detail') }}
          </p>
        </template>
        <template v-else>
          <p class="text-[15px] font-medium leading-snug">{{ t('model.story.band_empty') }}</p>
          <button type="button" data-testid="range-band-cta"
                  class="inline-flex min-h-11 items-center justify-center rounded-xl bg-green-500 px-4 font-semibold text-gray-950 hover:bg-green-400 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-500 dark:bg-green-600 dark:text-white dark:hover:bg-green-700"
                  @click="emit('open')">
            {{ t('model.story.band_cta') }}
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import { analytics } from '../../services/analytics'
import { personalRangeService, type PersonalRangeInputs } from '../../api/personalRangeService'

/**
 * Winter-to-summer range for the visitor's answers. Before the first answer the model's own
 * season band (from the stats response) is shown without a request. Every change afterwards
 * asks the public preview endpoint, debounced; signed-in visitors ask their calculator
 * instead, which has no IP limit. When the preview is refused or fails the band stays and is
 * labelled as a standard profile, nothing pops up. The exact figures stay behind the gate.
 */
const props = defineProps<{
  brand: string
  model: string
  modelName: string
  inputs: PersonalRangeInputs
  /** answered questions; the band starts asking the server from the first one */
  answered: number
  /** number of questions on the page, one progress stroke each */
  total: number
  fallbackBand: { minKm: number; maxKm: number } | null
  /** one row per chapter question: what the reader answered, null while open */
  profile?: { key: string; label: string; value: string | null }[]
}>()
const emit = defineEmits<{ open: [] }>()

const DEBOUNCE_MS = 300

const { t } = useI18n()
const { formatDistance } = useLocaleFormat()
const authStore = useAuthStore()

const remote = ref<{ minKm: number; maxKm: number } | null>(null)
const degraded = ref(false)
const band = computed(() => remote.value ?? props.fallbackBand)
const profile = computed(() => props.profile ?? [])
/** Glow right on the answer (re-mounts the card), pulse on the figure when the new value arrives; 0 on first paint, no animation then */
const glowKey = ref(0)
const pulseKey = ref(0)
watch(() => props.answered, (n, old) => { if (n > 0 && old !== undefined) glowKey.value++ })
watch(() => ({ ...props.inputs }), () => { if (props.answered > 0) glowKey.value++ }, { deep: true })
watch(band, (b, old) => { if (old && b && (b.minKm !== old.minKm || b.maxKm !== old.maxKm)) pulseKey.value++ })
let timer: ReturnType<typeof setTimeout> | null = null
let requestNo = 0
let shownTracked = false

function trackShown(level: string) {
  if (shownTracked) return
  shownTracked = true
  analytics.track('range_band_shown', { model: props.modelName, data_level: level })
}

async function refresh() {
  const no = ++requestNo
  try {
    let next: { minKm: number; maxKm: number; dataLevel: string }
    if (authStore.token) {
      const r = await personalRangeService.calculate(props.brand, props.model, props.inputs)
      next = { minKm: r.winterRangeKm, maxKm: r.summerRangeKm, dataLevel: r.dataLevel }
    } else {
      next = await personalRangeService.preview(props.brand, props.model, props.inputs)
    }
    if (no !== requestNo) return
    remote.value = { minKm: next.minKm, maxKm: next.maxKm }
    degraded.value = false
    trackShown(next.dataLevel)
  } catch {
    if (no !== requestNo) return
    degraded.value = true
  }
}

watch(() => [props.answered, { ...props.inputs }] as const, () => {
  if (props.answered === 0) return
  if (timer) clearTimeout(timer)
  timer = setTimeout(refresh, DEBOUNCE_MS)
}, { deep: true })

watch(() => props.fallbackBand, b => { if (b && !remote.value) trackShown('MODEL') }, { immediate: true })

onBeforeUnmount(() => { if (timer) clearTimeout(timer) })
</script>

<style>
/* Water drop on every answer: the card bumps once, two rings run outwards from its edge and fade.
   Only without prefers-reduced-motion; the glow below runs alongside. */
@media (prefers-reduced-motion: no-preference) {
  .band-drop { animation: band-glow 1s ease-out, band-bump 0.5s cubic-bezier(0.2, 0.7, 0.2, 1); }
  .band-drop::before, .band-drop::after {
    content: ''; position: absolute; inset: 0; border-radius: inherit; pointer-events: none;
    border: 2px solid rgba(74, 222, 128, 0.9);
    animation: band-ring 1.1s ease-out forwards;
  }
  .band-drop::after { animation-delay: 0.18s; border-width: 1.5px; }
}
@keyframes band-bump {
  0% { transform: scale(1); }
  35% { transform: scale(1.025); }
  100% { transform: scale(1); }
}
@keyframes band-ring {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.18); opacity: 0; }
}
/* A short green glow around the card when the band moves: the chips sit on the left, the card on the right */
@keyframes band-glow {
  0% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0.9), 0 0 0 0 rgba(74, 222, 128, 0.6), 0 10px 15px -3px rgba(17, 24, 39, 0.2); }
  30% { box-shadow: 0 0 0 5px rgba(74, 222, 128, 0.8), 0 0 36px 10px rgba(74, 222, 128, 0.45), 0 10px 15px -3px rgba(17, 24, 39, 0.2); }
  100% { box-shadow: 0 0 0 14px rgba(74, 222, 128, 0), 0 0 48px 16px rgba(74, 222, 128, 0), 0 10px 15px -3px rgba(17, 24, 39, 0.2); }
}
@keyframes band-pulse {
  0% { transform: scale(1); opacity: 0.4; }
  40% { transform: scale(1.06); opacity: 1; }
  100% { transform: scale(1); opacity: 1; }
}
</style>
