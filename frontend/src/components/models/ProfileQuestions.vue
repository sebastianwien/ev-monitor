<template>
  <!-- The profile questions of the list and the model page: five sliders, stacked or as one row
       (compact), and optionally "Was ist dir am wichtigsten?" below them -->
  <div class="grid gap-5">
    <div :class="compact ? 'grid items-end gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 xl:gap-x-5' : 'grid gap-5'">
      <ProfileSlider v-for="q in sliders" :key="q.key" :id="`${idPrefix}-${q.key}`" :test-key="`${testPrefix}${q.key}`" :label="q.label"
                     :min="q.min" :max="q.max" :step="q.step" :start="q.start" :value="q.value" :format="q.format" :detail="q.detail"
                     :compact="compact" :highlight="highlightKey === q.key" @commit="v => emit('answer', q.key, v)" />
    </div>
    <div v-if="priorities?.length" class="grid gap-2 md:flex md:items-center md:gap-4" data-testid="profile-priority">
      <span :id="priorityId" class="text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ t('profile.priority_question') }}</span>
      <div class="grid grid-flow-col gap-1 rounded-xl bg-gray-100 p-1 md:w-max dark:bg-gray-800" role="group" :aria-labelledby="priorityId">
        <button v-for="p in priorities" :key="p" type="button" :aria-pressed="priority === p" :data-testid="`priority-${p}`"
                class="min-h-11 rounded-lg px-4 text-[14px] font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
                :class="priority === p ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-950 dark:text-gray-100' : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100'"
                @click="emit('update:priority', p)">
          {{ t(`profile.priority_${p}`) }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import ProfileSlider from './ProfileSlider.vue'
import type { ProfileSliderItem } from '../../composables/useProfileSliders'
import type { ProfileKey } from '../../utils/drivingProfile'
import type { RankingPriority } from '../../utils/rankingAnswers'

withDefaults(defineProps<{
  sliders: ProfileSliderItem[]
  /** the sliders' ids are `${idPrefix}-${key}`, the page scrolls to them */
  idPrefix: string
  testPrefix?: string
  /** one row from xl, two or three columns below */
  compact?: boolean
  /** the slider the reader is sent back to */
  highlightKey?: ProfileKey | null
  /** the answers of "Was ist dir am wichtigsten?"; none, no question */
  priorities?: RankingPriority[]
  priority?: RankingPriority | null
}>(), { testPrefix: '', compact: false, highlightKey: null, priorities: () => [], priority: null })
const emit = defineEmits<{ answer: [key: ProfileKey, value: number]; 'update:priority': [value: RankingPriority] }>()

const { t } = useI18n()
const priorityId = useId()
</script>
