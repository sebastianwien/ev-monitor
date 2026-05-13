<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { PlusIcon } from '@heroicons/vue/24/outline'

interface TripFormModel {
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  distanceKm?: number | null
  routeType?: 'CITY' | 'COMBINED' | 'HIGHWAY' | string
  socStart?: number | null
  socEnd?: number | null
  // Other fields the parent owns (feedback, etc.) are passed through untouched.
  [key: string]: any
}

const props = defineProps<{
  /** 'add' shows the "Fahrt"/+ headline, 'edit' shows the "Fahrt bearbeiten" headline plus the SoC fields. */
  mode: 'add' | 'edit'
  /** Validation/server error to display above the form. */
  error?: string | null
  /** Disables save while persisting. */
  saving?: boolean
  /** Localized distance unit label (km / mi). */
  distanceUnit: string
}>()

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'cancel'): void
}>()

const form = defineModel<TripFormModel>({ required: true })
const { t } = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <span v-if="mode === 'add'"
        class="text-[11px] font-bold uppercase tracking-[0.14em] text-emerald-700 dark:text-emerald-400 flex items-center gap-1.5">
        <PlusIcon class="w-4 h-4" aria-hidden="true" />{{ t('dashboard.trip_add') }}
      </span>
      <span v-else class="text-[11px] font-bold uppercase tracking-[0.14em] text-emerald-700 dark:text-emerald-400">
        {{ t('dashboard.trip_edit') }}
      </span>
      <div class="flex gap-2">
        <button type="button" @click="emit('save')" :disabled="saving"
          class="inline-flex items-center justify-center bg-emerald-600 hover:bg-emerald-500 text-white font-bold uppercase tracking-wider text-[11px] px-3 py-1.5 rounded-sm border-2 border-emerald-600 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 disabled:opacity-50 disabled:cursor-not-allowed">
          {{ t('dashboard.trip_save') }}
        </button>
        <button type="button" @click="emit('cancel')"
          class="inline-flex items-center justify-center bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 font-bold uppercase tracking-wider text-[11px] px-3 py-1.5 rounded-sm border-2 border-gray-300 dark:border-gray-700 shadow-[2px_2px_0_0_#9ca3af] dark:shadow-[2px_2px_0_0_#4b5563] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75">
          {{ t('dashboard.trip_cancel') }}
        </button>
      </div>
    </div>
    <p v-if="error" class="text-xs font-medium border-l-2 border-red-500 bg-red-50 dark:bg-red-950/30 text-red-700 dark:text-red-300 px-3 py-2 rounded-r-sm">{{ error }}</p>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
      <div>
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_started_at') }}</label>
        <input v-model="form!.tripStartedAt" type="datetime-local"
          class="w-full px-2 py-1.5 text-sm border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-medium focus:outline-none focus:border-emerald-500 transition-colors" />
      </div>
      <div>
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_ended_at') }}</label>
        <input v-model="form!.tripEndedAt" type="datetime-local"
          class="w-full px-2 py-1.5 text-sm border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-medium focus:outline-none focus:border-emerald-500 transition-colors" />
      </div>
      <div>
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_distance', { unit: distanceUnit }) }}</label>
        <input v-model="form!.distanceKm" type="number" min="0" max="9999" step="0.1"
          class="w-full px-2 py-1.5 text-sm border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-medium focus:outline-none focus:border-emerald-500 transition-colors" />
      </div>
      <div>
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_route_type') }}</label>
        <div class="flex gap-1">
          <button v-for="rt in ['CITY','COMBINED','HIGHWAY']" :key="rt" type="button"
            @click="form!.routeType = rt"
            :class="['flex-1 px-1 py-1.5 text-xs font-bold uppercase tracking-wider rounded-sm border-2 transition focus:outline-none',
                     form!.routeType === rt
                       ? 'bg-emerald-600 border-emerald-600 text-white shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#ffffff]'
                       : 'bg-white dark:bg-gray-900 border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-400 hover:border-emerald-400']">
            {{ t('dashboard.trip_route_' + rt.toLowerCase()) }}
          </button>
        </div>
      </div>
      <template v-if="mode === 'edit'">
        <div>
          <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_soc_start') }}</label>
          <input v-model="form!.socStart" type="number" min="0" max="100"
            class="w-full px-2 py-1.5 text-sm border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-medium focus:outline-none focus:border-emerald-500 transition-colors" />
        </div>
        <div>
          <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-1">{{ t('dashboard.trip_soc_end') }}</label>
          <input v-model="form!.socEnd" type="number" min="0" max="100"
            class="w-full px-2 py-1.5 text-sm border-2 border-gray-300 dark:border-gray-600 rounded-sm bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-medium focus:outline-none focus:border-emerald-500 transition-colors" />
        </div>
      </template>
    </div>
    <!-- Slot for advanced sections (mobile edit uses this for feedback + merge preview) -->
    <slot name="extra" />
  </div>
</template>
