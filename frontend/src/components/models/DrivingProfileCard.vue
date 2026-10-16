<template>
  <!-- The question-answer card: green frame with title and subtitle, then white surfaces. Open: the
       questions. Answered: the profile in one line, "Angaben ändern" folds the questions out. Then the
       answers and the footer (gate or top 5). Knows neither model nor list. -->
  <section ref="root" :aria-labelledby="titleId"
           class="grid min-w-0 gap-4 bg-green-50 px-4 py-6 md:gap-5 md:rounded-3xl md:p-7 md:edge-box dark:bg-green-950/40">
    <div class="flex items-start gap-3">
      <span v-if="unlocked" class="grid h-11 w-11 flex-none place-items-center rounded-full bg-green-600 text-white"><LockOpenIcon class="h-6 w-6" aria-hidden="true" /></span>
      <div class="grid gap-1">
        <h2 :id="titleId" class="text-[21px] font-bold tracking-tight text-gray-900 md:text-[24px] dark:text-gray-100">{{ title }}</h2>
        <p v-if="sub" class="max-w-[70ch] text-[14.5px] leading-snug text-gray-600 dark:text-gray-400">{{ sub }}</p>
      </div>
    </div>

    <div class="rounded-2xl bg-white shadow-[0_1px_2px_rgba(0,0,0,0.05)] max-md:edge-card dark:bg-gray-900" data-testid="profile-questions">
      <template v-if="answered && summary">
        <div class="flex items-start justify-between gap-3 px-4 py-3 md:items-center md:px-5">
          <p class="min-w-0 text-[13.5px] leading-snug text-gray-700 md:text-[14px] dark:text-gray-300" data-testid="profile-summary">{{ summary }}</p>
          <button type="button" :aria-expanded="open" :aria-controls="foldId" data-testid="profile-toggle"
                  class="-my-2 inline-flex min-h-11 flex-none items-center gap-1 text-[14px] font-semibold text-green-700 underline-offset-4 hover:underline dark:text-green-400"
                  @click="emit('update:open', !open)">
            {{ t('personal_range.profile_change') }}
            <ChevronDownIcon class="h-4 w-4 transition-transform" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
          </button>
        </div>
        <div :id="foldId" class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none" :class="open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'" :inert="open ? undefined : true">
          <div class="min-h-0 overflow-hidden">
            <div class="border-t border-gray-100 px-4 pb-5 pt-4 md:px-5 dark:border-gray-800"><slot name="questions" /></div>
          </div>
        </div>
      </template>
      <div v-else class="px-4 py-4 md:px-5 md:py-5"><slot name="questions" /></div>
    </div>

    <slot name="answers" />
    <slot name="footer" />
  </section>
</template>

<script setup lang="ts">
import { ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, LockOpenIcon } from '@heroicons/vue/24/outline'

defineProps<{
  title: string
  sub?: string
  /** answered: the questions fold behind the profile line */
  answered: boolean
  /** the reader's own figures are open: the open lock next to the title */
  unlocked?: boolean
  /** the profile in one line, "15.000 km im Jahr · ..." */
  summary?: string
  /** the questions unfolded under the profile line */
  open: boolean
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { t } = useI18n()
const titleId = useId()
const foldId = useId()
const root = ref<HTMLElement | null>(null)
defineExpose({ root })
</script>
