<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { CheckCircleIcon, CheckIcon, ExclamationCircleIcon, BoltIcon, ArrowPathIcon, XMarkIcon } from '@heroicons/vue/24/outline';
import BottomSheet from '../shared/BottomSheet.vue';
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_ICON_BTN, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses';
import { tessieService, type TessieVehicle, type TessieImportResult } from '../../api/tessieService';
import CarSelectDropdown from '../car/CarSelectDropdown.vue';
import type { Car } from '../../api/carService';
import { isMappingComplete } from '../../composables/tessieMapping';

const { t } = useI18n();
const emit = defineEmits<{ (e: 'close'): void }>();

const props = defineProps<{
  cars: Car[];
}>();

type Step = 'token' | 'vehicles' | 'mapping' | 'importing' | 'done';
type VinResult = { vin: string; name: string; result: TessieImportResult; error?: string };

const step = ref<Step>('token');
const token = ref('');
const loading = ref(false);
const error = ref('');
const vehicles = ref<TessieVehicle[]>([]);
const selected = ref<Set<string>>(new Set());
const vinToCarId = ref<Record<string, string>>({});
const currentVin = ref('');
const currentIndex = ref(0);
const results = ref<VinResult[]>([]);

const totalEvLogs = () => results.value.reduce((s, r) => s + r.result.evLogsCreated, 0);
const totalEvTrips = () => results.value.reduce((s, r) => s + r.result.evTripsCreated, 0);
const totalSkipped = () => results.value.reduce((s, r) => s + r.result.skipped, 0);
const hasErrors = () => results.value.some(r => r.error);

const selectedVins = computed(() => vehicles.value.filter(v => selected.value.has(v.vin)));
const allMapped = computed(() => isMappingComplete(selectedVins.value.map(v => v.vin), vinToCarId.value));
const hasNoCars = computed(() => props.cars.length === 0);

const fetchVehicles = async () => {
  if (!token.value.trim()) {
    error.value = t('tessie.err_token_empty');
    return;
  }
  error.value = '';
  loading.value = true;
  try {
    vehicles.value = await tessieService.fetchVehicles(token.value.trim());
    if (vehicles.value.length === 0) {
      error.value = t('tessie.err_no_vehicles');
      return;
    }
    selected.value = new Set(vehicles.value.map(v => v.vin));
    step.value = 'vehicles';
  } catch (e: any) {
    error.value = e.response?.data?.error || t('tessie.err_token_invalid');
  } finally {
    loading.value = false;
  }
};

const toggleVin = (vin: string) => {
  if (selected.value.has(vin)) {
    selected.value.delete(vin);
    delete vinToCarId.value[vin];
  } else {
    selected.value.add(vin);
  }
  selected.value = new Set(selected.value);
};

const allSelected = () => selected.value.size === vehicles.value.length;

const toggleAll = () => {
  if (allSelected()) {
    selected.value = new Set();
    vinToCarId.value = {};
  } else {
    selected.value = new Set(vehicles.value.map(v => v.vin));
  }
};

const proceedToMapping = () => {
  if (selected.value.size === 0) {
    error.value = t('tessie.err_none_selected');
    return;
  }
  if (props.cars.length === 1) {
    const onlyCarId = props.cars[0].id;
    selectedVins.value.forEach(v => { vinToCarId.value[v.vin] = onlyCarId; });
  }
  error.value = '';
  step.value = 'mapping';
};

const startImport = async () => {
  if (!allMapped.value) return;
  error.value = '';
  results.value = [];
  step.value = 'importing';

  const toImport = selectedVins.value;
  for (let i = 0; i < toImport.length; i++) {
    const v = toImport[i];
    currentIndex.value = i + 1;
    currentVin.value = v.displayName || v.vin;
    try {
      const result = await tessieService.importVin(token.value.trim(), v.vin, vinToCarId.value[v.vin]);
      results.value.push({ vin: v.vin, name: v.displayName || v.vin, result });
    } catch (e: any) {
      results.value.push({
        vin: v.vin,
        name: v.displayName || v.vin,
        result: { drivesImported: 0, chargesImported: 0, skipped: 0, evLogsCreated: 0, evTripsCreated: 0 },
        error: e.response?.data?.error || e.message || t('tessie.err_import_failed'),
      });
    }
  }
  step.value = 'done';
};
</script>

<template>
  <BottomSheet edge :locked="step === 'importing'" :label="t('tessie.title')" @close="emit('close')">
    <template #default="{ close }">
      <!-- Header -->
      <div class="flex shrink-0 items-center justify-between gap-3 border-b border-gray-200 px-4 pb-3 pt-4 sm:px-6 dark:border-white/10">
        <h2 class="text-xl font-extrabold text-gray-900 dark:text-gray-50">{{ t('tessie.title') }}</h2>
        <button v-if="step !== 'importing'" type="button" :class="CI_ICON_BTN" :aria-label="t('common.close')" @click="close">
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <div class="grid content-start gap-4 overflow-y-auto px-4 py-4 sm:px-6">
        <p v-if="error" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
          <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ error }}
        </p>

        <!-- Step 1: Token -->
        <div v-if="step === 'token'" class="grid gap-4">
          <p class="text-sm text-gray-600 dark:text-gray-400" v-html="t('tessie.step1_intro')" />
          <div class="grid gap-1.5">
            <label for="tessie-token" :class="CI_LABEL">{{ t('tessie.token_label') }}</label>
            <input id="tessie-token" v-model="token" type="password" :placeholder="t('tessie.token_placeholder')"
              :class="[CI_INPUT, 'font-mono']" @keyup.enter="fetchVehicles" />
            <p class="text-[13px] text-gray-500 dark:text-gray-400">
              {{ t('tessie.token_hint_pre') }}
              <a href="https://dash.tessie.com/settings/api" target="_blank" rel="noopener noreferrer" class="font-semibold text-gray-900 underline dark:text-gray-100">dash.tessie.com/settings/api</a>
              {{ t('tessie.token_hint_post') }}
            </p>
          </div>
          <button type="button" :class="[CI_BTN_PRIMARY, 'w-full']" :disabled="loading" @click="fetchVehicles">
            <ArrowPathIcon v-if="loading" class="h-5 w-5 animate-spin" aria-hidden="true" />
            {{ loading ? t('tessie.load_btn_loading') : t('tessie.load_btn') }}
          </button>
        </div>

        <!-- Step 2: VIN selection -->
        <div v-if="step === 'vehicles'" class="grid gap-4">
          <div class="flex items-center justify-between gap-3">
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ t('tessie.step2_found', { n: vehicles.length }) }}
            </p>
            <button type="button" class="inline-flex min-h-11 items-center text-[13px] font-semibold text-gray-700 underline-offset-2 hover:underline dark:text-gray-300" @click="toggleAll">
              {{ allSelected() ? t('tessie.deselect_all') : t('tessie.select_all') }}
            </button>
          </div>

          <div class="grid gap-2">
            <button v-for="v in vehicles" :key="v.vin" type="button" :aria-pressed="selected.has(v.vin)"
              :class="['w-full rounded-2xl px-4 py-3 text-left transition-colors',
                selected.has(v.vin) ? 'bg-white edge-card dark:bg-gray-700/50' : 'bg-gray-100 text-gray-500 dark:bg-gray-800/60']"
              @click="toggleVin(v.vin)">
              <span class="flex items-center gap-3">
                <span :class="['flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition-colors',
                  selected.has(v.vin) ? 'border-gray-900 bg-gray-900 dark:border-gray-100 dark:bg-gray-100' : 'border-gray-400 dark:border-gray-500']" aria-hidden="true">
                  <CheckIcon v-if="selected.has(v.vin)" class="h-3 w-3 text-white dark:text-gray-900" stroke-width="3" />
                </span>
                <span class="min-w-0">
                  <span class="block truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ v.displayName }}</span>
                  <span class="mt-0.5 block font-mono text-[12px] text-gray-500 dark:text-gray-400">{{ v.vin }}</span>
                  <span v-if="!v.isActive" class="mt-1 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-[11.5px] font-semibold text-gray-600 dark:bg-gray-800 dark:text-gray-300">{{ t('tessie.inactive') }}</span>
                </span>
              </span>
            </button>
          </div>

          <div class="flex gap-2 pt-2">
            <button type="button" :class="[CI_BTN_SECONDARY, 'flex-1']" @click="step = 'token'">
              {{ t('tessie.back_btn') }}
            </button>
            <button type="button" :class="[CI_BTN_PRIMARY, 'flex-1']" :disabled="selected.size === 0" @click="proceedToMapping">
              {{ t('tessie.continue_btn', { n: selected.size }) }}
            </button>
          </div>
        </div>

        <!-- Step 3: VIN -> car mapping -->
        <div v-if="step === 'mapping'" class="grid gap-4">
          <div>
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('tessie.step3_title') }}</p>
            <p class="mt-1 text-[13px] text-gray-500 dark:text-gray-400">{{ t('tessie.step3_intro') }}</p>
          </div>

          <p :class="[CI_NOTE, 'flex items-start gap-2 py-3 text-[13px]']">
            <BoltIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />{{ t('tessie.ac_hint') }}
          </p>

          <!-- Ohne Auto fehlt etwas: Amber mit Handlung -->
          <p v-if="hasNoCars" class="rounded-2xl bg-amber-50 p-4 text-sm text-gray-800 dark:bg-amber-900/20 dark:text-gray-100">
            {{ t('tessie.mapping_no_cars') }}
            <router-link to="/cars" class="font-semibold underline">{{ t('tessie.mapping_no_cars_link') }}</router-link>
          </p>

          <div v-else class="grid gap-3">
            <div v-for="v in selectedVins" :key="v.vin" :class="[CI_CARD, 'grid gap-2 py-3']">
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ v.displayName }}</p>
                <p class="mt-0.5 font-mono text-[12px] text-gray-500 dark:text-gray-400">{{ v.vin }}</p>
              </div>
              <CarSelectDropdown
                :cars="cars"
                :model-value="vinToCarId[v.vin] ?? null"
                @update:model-value="(value: string) => (vinToCarId[v.vin] = value)"
                :aria-label="t('tessie.mapping_select_aria', { vin: v.vin })"
              />
            </div>
          </div>

          <div class="flex gap-2 pt-2">
            <button type="button" :class="[CI_BTN_SECONDARY, 'flex-1']" @click="step = 'vehicles'">
              {{ t('tessie.back_btn') }}
            </button>
            <button type="button" :class="[CI_BTN_PRIMARY, 'flex-1']" :disabled="!allMapped || hasNoCars" data-test="tessie-start-import" @click="startImport">
              {{ t('tessie.import_btn', { n: selectedVins.length }) }}
            </button>
          </div>
        </div>

        <!-- Step 4: Importing -->
        <div v-if="step === 'importing'" class="grid justify-items-center gap-3 py-4 text-center" aria-live="polite">
          <ArrowPathIcon class="h-12 w-12 animate-spin text-gray-400" aria-hidden="true" />
          <p class="font-semibold text-gray-900 dark:text-gray-100">{{ t('tessie.importing_title') }}</p>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ currentVin }}</p>
          <p class="text-[13px] tabular-nums text-gray-500 dark:text-gray-400">{{ t('tessie.importing_progress', { current: currentIndex, total: selectedVins.length }) }}</p>
          <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div class="h-full rounded-full bg-gray-900 transition-all duration-300 dark:bg-gray-100" :style="{ width: `${(currentIndex / selectedVins.length) * 100}%` }" />
          </div>
        </div>

        <!-- Step 5: Done -->
        <div v-if="step === 'done'" class="grid gap-4">
          <div class="grid justify-items-center gap-3 text-center">
            <CheckCircleIcon class="h-12 w-12 text-green-700 dark:text-green-400" aria-hidden="true" />
            <h3 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ t('tessie.done_title') }}</h3>
          </div>

          <!-- Total summary -->
          <div :class="[CI_CARD, 'grid gap-1.5']">
            <p class="text-sm font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ t('tessie.done_charges', { n: totalEvLogs() }) }}</p>
            <p class="text-sm font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ t('tessie.done_drives', { n: totalEvTrips() }) }}</p>
            <p v-if="totalSkipped() > 0" class="text-sm tabular-nums text-gray-600 dark:text-gray-400">{{ t('tessie.done_skipped', { n: totalSkipped() }) }}</p>
          </div>

          <!-- Per-vehicle breakdown -->
          <div v-if="results.length > 1" class="grid gap-2">
            <p :class="CI_LABEL">{{ t('tessie.done_per_vehicle') }}</p>
            <div v-for="r in results" :key="r.vin" :class="r.error ? [CI_NOTE, 'py-3'] : [CI_CARD, 'py-3']">
              <div class="flex items-start gap-2">
                <ExclamationCircleIcon v-if="r.error" class="mt-0.5 h-4 w-4 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{{ r.name }}</p>
                  <p class="font-mono text-[12px] text-gray-500 dark:text-gray-400">{{ r.vin }}</p>
                  <p v-if="r.error" class="mt-0.5 text-[13px] font-semibold text-gray-900 dark:text-gray-100">{{ r.error }}</p>
                  <p v-else class="mt-0.5 text-[13px] tabular-nums text-gray-500 dark:text-gray-400">
                    {{ t('tessie.done_charges', { n: r.result.evLogsCreated }) }} · {{ t('tessie.done_drives', { n: r.result.evTripsCreated }) }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <p v-if="hasErrors()" role="alert" :class="[CI_NOTE, 'flex items-start gap-2 py-3 text-[13px] font-semibold text-gray-900 dark:text-gray-100']">
            <ExclamationCircleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ t('tessie.done_partial_error') }}
          </p>

          <p class="text-center text-[13px] text-gray-500 dark:text-gray-400">{{ t('tessie.done_hint') }}</p>

          <div class="flex flex-col gap-2 sm:flex-row">
            <button type="button" :class="[CI_BTN_SECONDARY, 'flex-1']" @click="close">
              {{ t('tessie.done_btn') }}
            </button>
            <router-link to="/dashboard" :class="[CI_BTN_PRIMARY, 'flex-1']" @click="emit('close')">
              {{ t('tessie.done_to_dashboard') }}
            </router-link>
          </div>
        </div>
      </div>
    </template>
  </BottomSheet>
</template>
