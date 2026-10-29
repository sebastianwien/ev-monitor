<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ChoiceChip from '../../shared/ChoiceChip.vue'

/**
 * Fahrt-Formular in der App-CI (Tagesblatt, Log-Feed, Fahrt-Sheet). Modell aus useLogList:
 * Zeiten als datetime-local, leere Felder als ''.
 * `title` setzt eine Überschrift, wenn das Formular nicht in einem Sheet mit eigenem Titel steht;
 * der Slot `extra` nimmt Zusätze vor den Knöpfen auf (Bewerten, Zusammenführen im Feed).
 */
defineProps<{ mode: 'add' | 'edit'; error?: string | null; saving?: boolean; distanceUnit: string; title?: string }>()
const emit = defineEmits<{ save: []; cancel: [] }>()
const form = defineModel<Record<string, any>>({ required: true })
const { t } = useI18n()

const ROUTES = ['CITY', 'COMBINED', 'HIGHWAY'] as const
const label = 'text-[12px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400'
const input = 'min-h-12 w-full min-w-0 rounded-xl border-2 border-edge bg-white px-3 text-[17px] font-semibold tabular-nums outline-none focus:border-green-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100'
</script>

<template>
  <form class="grid gap-4" data-testid="day-trip-form" @submit.prevent="emit('save')">
    <p v-if="title" class="text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ title }}</p>
    <p v-if="error" role="alert" class="rounded-xl bg-amber-50 px-3 py-2 text-[14px] text-amber-900 dark:bg-amber-400/10 dark:text-amber-200">{{ error }}</p>
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <label class="grid gap-1"><span :class="label">{{ t('storyday.form_start') }}</span>
        <input v-model="form.tripStartedAt" type="datetime-local" :class="input" /></label>
      <label class="grid gap-1"><span :class="label">{{ t('storyday.form_end') }}</span>
        <input v-model="form.tripEndedAt" type="datetime-local" :class="input" /></label>
    </div>
    <label class="grid gap-1"><span :class="label">{{ t('storyday.form_distance', { unit: distanceUnit }) }}</span>
      <input v-model="form.distanceKm" type="number" inputmode="decimal" min="0" max="9999" step="0.1" :class="input" /></label>
    <div class="grid gap-1.5">
      <span :class="label">{{ t('storyday.form_route') }}</span>
      <div role="group" :aria-label="t('storyday.form_route')" class="flex flex-wrap gap-2">
        <ChoiceChip v-for="r in ROUTES" :key="r" size="lg" :active="form.routeType === r" @click="form.routeType = form.routeType === r ? '' : r">
          {{ t(`dashboard.trip_route_${r.toLowerCase()}`) }}
        </ChoiceChip>
      </div>
    </div>
    <div v-if="mode === 'edit'" class="grid grid-cols-2 gap-4">
      <label class="grid gap-1"><span :class="label">{{ t('storyday.form_soc_start') }}</span>
        <span class="relative"><input v-model="form.socStart" type="number" inputmode="numeric" min="0" max="100" :class="[input, 'pr-9']" /><span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[15px] text-gray-500">%</span></span></label>
      <label class="grid gap-1"><span :class="label">{{ t('storyday.form_soc_end') }}</span>
        <span class="relative"><input v-model="form.socEnd" type="number" inputmode="numeric" min="0" max="100" :class="[input, 'pr-9']" /><span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[15px] text-gray-500">%</span></span></label>
    </div>
    <slot name="extra" />
    <div class="flex gap-2 pt-1">
      <button type="button" class="btn-3d cta-shadow-sm min-h-12 flex-1 rounded-xl border-2 border-edge bg-white px-3 text-[15px] font-semibold dark:bg-white/10" @click="emit('cancel')">
        {{ t('common.cancel') }}
      </button>
      <button type="submit" data-testid="day-trip-save" :disabled="saving"
        class="btn-3d cta-shadow-sm min-h-12 flex-1 rounded-xl border-2 border-edge bg-green-600 px-3 text-[15px] font-semibold text-white disabled:opacity-40">
        {{ t('logfields.save') }}
      </button>
    </div>
  </form>
</template>
