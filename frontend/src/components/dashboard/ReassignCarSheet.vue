<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TruckIcon, CheckIcon } from '@heroicons/vue/24/outline'
import BottomSheet from '../shared/BottomSheet.vue'
import { useCarContext } from '../../composables/useCarContext'
import { carDisplayName } from '../../utils/enumLabel'

/**
 * Fahrzeug-Zuordnung: Sheet mit Kante wie die Story-Sheets (App-CI). Bucht die Ladung aus `reassignModalEntry`
 * um, bei einer Ladegruppe alle Teile. Liste und Logbuch teilen es; `saved` meldet den Erfolg.
 */
const emit = defineEmits<{ saved: [] }>()
const { t } = useI18n()
const {
  reassignModalEntry, reassignSelectedCarId, reassignSaving, reassignError, otherCars, saveReassign, fetchStatistics,
} = useCarContext()
const reassignSheet = ref<InstanceType<typeof BottomSheet> | null>(null)
async function doSaveReassign() {
  await saveReassign(fetchStatistics)
  if (!reassignModalEntry.value) emit('saved')
}
</script>

<template>
  <BottomSheet v-if="reassignModalEntry" ref="reassignSheet" edge :label="t('dashboard.reassign_car')" panel-class="sm:max-w-sm"
    testid="reassign-sheet" @close="reassignModalEntry = null">
    <div class="grid gap-5 p-5">
      <div>
        <h3 class="text-[17px] font-bold text-gray-900 dark:text-gray-50">{{ t('dashboard.reassign_car') }}</h3>
        <p class="mt-1 text-[14px] text-gray-600 dark:text-gray-300">
          <template v-if="reassignModalEntry?._isLadegruppe">{{ t('dashboard.reassign_car_hint_group', { count: reassignModalEntry._topUps?.length ?? 0 }) }}</template>
          <template v-else>{{ t('dashboard.reassign_car_hint') }}</template>
        </p>
      </div>
      <div role="radiogroup" :aria-label="t('dashboard.reassign_car')" class="grid gap-2">
        <button v-for="car in otherCars" :key="car.id" type="button" role="radio" :aria-checked="reassignSelectedCarId === car.id"
          @click="reassignSelectedCarId = car.id; reassignError = null"
          class="flex min-h-12 w-full items-center gap-3 rounded-xl border-2 px-3 text-left transition-colors"
          :class="reassignSelectedCarId === car.id ? 'border-gray-900 bg-gray-50 dark:border-gray-100 dark:bg-white/10' : 'border-gray-200 hover:border-gray-400 dark:border-gray-700 dark:hover:border-gray-500'">
          <TruckIcon class="h-5 w-5 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ carDisplayName(car) }}</span>
          <CheckIcon v-if="reassignSelectedCarId === car.id" class="ml-auto h-5 w-5 shrink-0 text-gray-900 dark:text-gray-100" aria-hidden="true" />
        </button>
      </div>
      <p v-if="reassignError" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ reassignError }}</p>
      <div class="flex gap-2">
        <button type="button" class="min-h-12 flex-1 rounded-xl px-3 text-[15px] font-semibold text-gray-600 dark:text-gray-300" @click="reassignSheet?.requestClose()">
          {{ t('common.cancel') }}
        </button>
        <button type="button" :disabled="!reassignSelectedCarId || reassignSaving" @click="doSaveReassign"
          class="btn-3d min-h-12 flex-1 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white disabled:opacity-40">
          {{ reassignSaving ? t('common.saving') : t('common.save') }}
        </button>
      </div>
    </div>
  </BottomSheet>
</template>
