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
      <div class="flex flex-wrap gap-2" role="group" :aria-labelledby="priorityId">
        <ChoiceChip v-for="p in priorities" :key="p" :active="priority === p" :data-testid="`priority-${p}`" size="lg"
                    @click="emit('update:priority', p)">
          {{ t(`profile.priority_${p}`) }}
        </ChoiceChip>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'
import ProfileSlider from './ProfileSlider.vue'
import ChoiceChip from '../shared/ChoiceChip.vue'
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
