<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { ChevronDownIcon } from '@heroicons/vue/24/outline';

// Erklaert was Smartcar ist und raeumt die Vertrauens-Bedenken (Passwort,
// Datenzugriff) aus. Wird auf der Upgrade-Seite und im Imports-Tab genutzt -
// deshalb zwei Varianten, damit der Block im jeweiligen Design-Kontext sitzt:
//   neo  - Imports-Tab (border-2, Offset-Schatten, uppercase Summary)
//   soft - Upgrade-Seite (weiche Karte, normal-case Summary)
const props = withDefaults(defineProps<{
  variant?: 'neo' | 'soft';
}>(), {
  variant: 'neo',
});

const { t } = useI18n();

const styles = computed(() => props.variant === 'soft'
  ? {
      details: 'border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-sm dark:shadow-none',
      summary: 'px-4 py-3 text-sm font-semibold text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800/50',
      body: 'px-4 pb-4 pt-3 border-t border-gray-200 dark:border-gray-700',
      question: 'text-sm font-semibold text-gray-800 dark:text-gray-200',
    }
  : {
      details: 'border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]',
      summary: 'px-4 py-3 text-[11px] font-bold uppercase tracking-[0.14em] text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800',
      body: 'px-4 pb-4 pt-3 border-t-2 border-gray-300 dark:border-gray-700',
      question: 'text-[11px] font-bold uppercase tracking-wider text-gray-900 dark:text-white',
    });
</script>

<template>
  <details class="group overflow-hidden" :class="styles.details">
    <summary
      class="flex items-center justify-between cursor-pointer list-none select-none"
      :class="styles.summary"
    >
      {{ t('imports.smartcar_how_title') }}
      <ChevronDownIcon class="h-4 w-4 text-gray-500 transition-transform group-open:rotate-180 shrink-0" />
    </summary>
    <div class="space-y-4" :class="styles.body">
      <div v-for="i in 5" :key="i">
        <!-- Erste Frage ("Was ist Smartcar?") weglassen: an dieser Stelle ist sie noch
             nicht aufgeworfen. Der Antworttext dient als Einleitung, die Frage-Ueberschrift
             erst ab dem zweiten Eintrag. -->
        <p v-if="i !== 1" :class="styles.question">{{ t(`imports.smartcar_how_q${i}`) }}</p>
        <p class="text-sm text-gray-600 dark:text-gray-400 mt-1 leading-relaxed">{{ t(`imports.smartcar_how_a${i}`) }}</p>
      </div>
    </div>
  </details>
</template>
