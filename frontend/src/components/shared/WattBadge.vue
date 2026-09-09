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
  /** Tonale Variante: auf farbigem Grund (Button) hell, sonst indigo - bewusst NICHT das Warn-Amber der Chips. */
  onDark?: boolean
  size?: 'sm' | 'md'
}>(), { upTo: false, onDark: false, size: 'sm' })

const { t } = useI18n()
</script>

<template>
  <span v-if="props.amount > 0" data-testid="watt-badge"
    :aria-label="t('watt.preview_aria', { n: props.amount })"
    :class="['inline-flex items-center gap-0.5 rounded-full font-semibold leading-tight whitespace-nowrap',
             props.size === 'md' ? 'px-2 py-0.5 text-xs' : 'px-1.5 py-px text-[10px]',
             props.onDark ? 'bg-white/20 text-white' : 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/50 dark:text-indigo-200']">
    <BoltIcon :class="props.size === 'md' ? 'h-3.5 w-3.5' : 'h-3 w-3'" aria-hidden="true" />{{ props.upTo ? t('watt.up_to', { n: props.amount }) : `+${props.amount}` }}
  </span>
</template>
