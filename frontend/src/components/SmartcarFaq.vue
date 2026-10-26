<template>
  <div class="overflow-hidden" :class="styles.box">
    <button type="button" :aria-expanded="open"
      class="flex min-h-11 w-full items-center justify-between gap-3 text-left"
      :class="styles.summary" @click="open = !open">
      {{ t('imports.smartcar_how_title') }}
      <ChevronDownIcon class="h-4 w-4 shrink-0 text-gray-500 transition-transform duration-300 motion-reduce:transition-none"
        :class="open ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
    </button>
    <Collapse :open="open">
      <div class="space-y-4" :class="styles.body">
        <div v-for="i in 5" :key="i">
          <!-- Erste Frage ("Was ist Smartcar?") weglassen: an dieser Stelle ist sie noch
               nicht aufgeworfen. Der Antworttext dient als Einleitung, die Frage-Ueberschrift
               erst ab dem zweiten Eintrag. -->
          <p v-if="i !== 1" :class="styles.question">{{ t(`imports.smartcar_how_q${i}`) }}</p>
          <p class="text-sm text-gray-600 dark:text-gray-400 mt-1 leading-relaxed">{{ t(`imports.smartcar_how_a${i}`) }}</p>
        </div>
      </div>
    </Collapse>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ChevronDownIcon } from '@heroicons/vue/24/outline';
import Collapse from './shared/Collapse.vue';

// Erklaert was Smartcar ist und raeumt die Vertrauens-Bedenken (Passwort,
// Datenzugriff) aus. Wird auf der Upgrade-Seite und im Imports-Tab genutzt -
// deshalb zwei Varianten, damit der Block im jeweiligen Design-Kontext sitzt:
//   ci   - Imports-Tab (App-CI: ruhige graue Fläche ohne Kante)
//   soft - Upgrade-Seite (weiche Karte, normal-case Summary)
const props = withDefaults(defineProps<{
  variant?: 'ci' | 'soft';
}>(), {
  variant: 'ci',
});

const { t } = useI18n();
const open = ref(false);

const styles = computed(() => props.variant === 'soft'
  ? {
      box: 'border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 rounded-sm shadow-sm dark:shadow-none',
      summary: 'px-4 py-3 text-sm font-semibold text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800/50',
      body: 'px-4 pb-4 pt-3 border-t border-gray-200 dark:border-gray-700',
      question: 'text-sm font-semibold text-gray-800 dark:text-gray-200',
    }
  : {
      box: 'rounded-2xl bg-gray-100 dark:bg-gray-800/60',
      summary: 'px-4 py-3 text-sm font-semibold text-gray-900 dark:text-gray-100',
      body: 'px-4 pb-4',
      question: 'text-sm font-semibold text-gray-900 dark:text-gray-100',
    });
</script>
