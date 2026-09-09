<script setup lang="ts">
/**
 * "+n Watt"-Vorschau an einem Ausloeser (Chip, Button, Nudge). Rendert nichts bei 0 -
 * ein "+0" waere schlimmer als kein Badge. Immer dieselbe Optik, damit es als Waehrung lesbar ist.
 */
import { BoltIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  amount: number
  /** "upTo": Obergrenze statt fester Zusage (z.B. Banner mit N Ladungen). */
  upTo?: boolean
  /** Tonale Variante: auf farbigem Grund (Button) hell, sonst amber. */
  onDark?: boolean
}>(), { upTo: false, onDark: false })

const { t } = useI18n()
</script>

<template>
  <span v-if="props.amount > 0" data-testid="watt-badge"
    :aria-label="t('watt.preview_aria', { n: props.amount })"
    :class="['inline-flex items-center gap-0.5 rounded-full px-1.5 py-px text-[10px] font-semibold leading-tight whitespace-nowrap',
             props.onDark ? 'bg-white/20 text-white' : 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200']">
    <BoltIcon class="h-3 w-3" aria-hidden="true" />{{ props.upTo ? t('watt.up_to', { n: props.amount }) : `+${props.amount}` }}
  </span>
</template>
