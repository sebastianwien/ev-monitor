<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, BoltIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { analytics } from '../../services/analytics'
import type { CarTeaserKind } from '../../composables/useCarSetupTeaser'

/**
 * Hinweis-Streifen im Kopf einer Auto-Card: dieses Auto koennte seine Lade-Daten
 * automatisch liefern. Sitzt bewusst *in* der Card (nicht darueber), damit die
 * Zuordnung zum Auto keine Interpretation braucht - dasselbe Header-Motiv wie die
 * Dashboard-Cards (`border-b`-Streifen).
 *
 * Beide Varianten teilen sich denselben Gruen-Akzent; sie unterscheiden sich nur
 * durch Icon und Text. Zwei Farben lasen sich wie zwei Alarmstufen, obwohl es
 * dieselbe Botschaft ist. Gruen ist bewusst *nicht* das App-Indigo: in einer
 * indigolastigen View geht ein indigofarbener Streifen unter. Es ist die
 * Handlungsfarbe der Startseiten-CTAs.
 *
 * Welcher Teaser wann erscheint, entscheidet `useCarSetupTeaser`.
 */
const props = defineProps<{ kind: CarTeaserKind }>()
const emit = defineEmits<{ dismiss: []; setup: [] }>()

const { t } = useI18n()

const isTelemetry = computed(() => props.kind === 'TELEMETRY')

const onAction = () => {
  analytics.track('car_teaser_clicked', { kind: props.kind })
  if (isTelemetry.value) emit('setup')
}

const onDismiss = () => {
  analytics.track('car_teaser_dismissed', { kind: props.kind })
  emit('dismiss')
}
</script>

<template>
  <div class="relative flex items-start gap-2.5 border-b-2 border-l-4 border-gray-300 dark:border-gray-600 border-l-green-600 dark:border-l-green-500 bg-green-50 dark:bg-green-900/20 px-4 py-3 pr-10">
    <component
      :is="isTelemetry ? BoltIcon : ArrowPathIcon"
      class="h-5 w-5 shrink-0 text-green-600 dark:text-green-400"
      aria-hidden="true"
    />

    <div class="min-w-0 flex-1">
      <p class="text-sm font-semibold text-gray-800 dark:text-gray-200 leading-snug">
        {{ isTelemetry ? t('cars.teaser_telemetry_title') : t('cars.teaser_autosync_title') }}
      </p>
      <p class="text-xs text-gray-600 dark:text-gray-400 leading-snug mt-0.5">
        {{ isTelemetry ? t('cars.teaser_telemetry_desc') : t('cars.teaser_autosync_desc') }}
      </p>

      <div class="mt-1.5 text-center">
        <button
          v-if="isTelemetry"
          type="button"
          v-haptic
          class="text-xs font-semibold text-green-700 dark:text-green-400 underline decoration-dotted hover:decoration-solid"
          @click="onAction"
        >
          {{ t('cars.teaser_telemetry_cta') }}
        </button>
        <router-link
          v-else
          to="/upgrade"
          class="text-xs font-semibold text-green-700 dark:text-green-400 underline decoration-dotted hover:decoration-solid"
          @click="onAction"
        >
          {{ t('cars.teaser_autosync_cta') }}
        </router-link>
      </div>
    </div>

    <button
      type="button"
      class="absolute top-2 right-2 h-7 w-7 flex items-center justify-center rounded-sm text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-black/5 dark:hover:bg-white/10 transition"
      :aria-label="t('cars.teaser_dismiss')"
      :title="t('cars.teaser_dismiss')"
      @click="onDismiss"
    >
      <XMarkIcon class="h-4 w-4" />
    </button>
  </div>
</template>
