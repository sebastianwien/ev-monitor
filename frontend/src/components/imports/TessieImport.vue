<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/vue/24/outline';
import { tessieService, type TessieVehicle, type TessieImportResult } from '../../api/tessieService';

const { t } = useI18n();
const emit = defineEmits<{ (e: 'close'): void }>();

type Step = 'token' | 'vehicles' | 'importing' | 'done';
type VinResult = { vin: string; name: string; result: TessieImportResult; error?: string };

const step = ref<Step>('token');
const token = ref('');
const loading = ref(false);
const error = ref('');
const vehicles = ref<TessieVehicle[]>([]);
const selected = ref<Set<string>>(new Set());
const currentVin = ref('');
const currentIndex = ref(0);
const results = ref<VinResult[]>([]);

const totalDrives = () => results.value.reduce((s, r) => s + r.result.drivesImported, 0);
const totalCharges = () => results.value.reduce((s, r) => s + r.result.chargesImported, 0);
const totalSkipped = () => results.value.reduce((s, r) => s + r.result.skipped, 0);
const hasErrors = () => results.value.some(r => r.error);

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
  if (selected.value.has(vin)) selected.value.delete(vin);
  else selected.value.add(vin);
};

const allSelected = () => selected.value.size === vehicles.value.length;

const toggleAll = () => {
  if (allSelected()) selected.value = new Set();
  else selected.value = new Set(vehicles.value.map(v => v.vin));
};

const startImport = async () => {
  const toImport = vehicles.value.filter(v => selected.value.has(v.vin));
  if (toImport.length === 0) {
    error.value = t('tessie.err_none_selected');
    return;
  }
  error.value = '';
  results.value = [];
  step.value = 'importing';

  for (let i = 0; i < toImport.length; i++) {
    const v = toImport[i];
    currentIndex.value = i + 1;
    currentVin.value = v.displayName || v.vin;
    try {
      const result = await tessieService.importVin(token.value.trim(), v.vin);
      results.value.push({ vin: v.vin, name: v.displayName || v.vin, result });
    } catch (e: any) {
      results.value.push({
        vin: v.vin,
        name: v.displayName || v.vin,
        result: { drivesImported: 0, chargesImported: 0, skipped: 0 },
        error: e.response?.data?.error || e.message || t('tessie.err_import_failed'),
      });
    }
  }
  step.value = 'done';
};

const handleBackdropClick = () => {
  if (step.value === 'importing') return;
  emit('close');
};
</script>

<template>
  <div
    class="fixed inset-0 flex items-center justify-center z-50 p-4"
    style="backdrop-filter: blur(8px); background-color: rgba(0,0,0,0.3);"
    @click.self="handleBackdropClick"
  >
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-lg w-full max-h-[90vh] overflow-y-auto" @click.stop>
      <!-- Header -->
      <div class="sticky top-0 bg-gray-900 text-white px-6 py-4 rounded-t-xl flex justify-between items-center">
        <h2 class="text-xl font-bold">{{ t('tessie.title') }}</h2>
        <button
          v-if="step !== 'importing'"
          @click="emit('close')"
          class="text-white hover:text-gray-300 text-2xl font-bold leading-none"
        >&times;</button>
      </div>

      <div class="p-6 space-y-4">
        <!-- Error -->
        <div v-if="error" class="p-3 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 text-red-700 dark:text-red-300 rounded-lg text-sm">
          {{ error }}
        </div>

        <!-- Step 1: Token -->
        <div v-if="step === 'token'" class="space-y-4">
          <p class="text-sm text-gray-600 dark:text-gray-400" v-html="t('tessie.step1_intro')" />
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('tessie.token_label') }}</label>
            <input
              v-model="token"
              type="password"
              :placeholder="t('tessie.token_placeholder')"
              @keyup.enter="fetchVehicles"
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg focus:ring-2 focus:ring-gray-500 focus:border-gray-500 font-mono text-sm"
            />
            <p class="mt-1.5 text-xs text-gray-500 dark:text-gray-400">
              {{ t('tessie.token_hint_pre') }}
              <a href="https://dash.tessie.com/settings/api" target="_blank" rel="noopener noreferrer" class="text-indigo-600 dark:text-indigo-400 underline">dash.tessie.com/settings/api</a>
              {{ t('tessie.token_hint_post') }}
            </p>
          </div>
          <button
            @click="fetchVehicles"
            :disabled="loading"
            class="btn-3d w-full flex items-center justify-center gap-2 bg-gray-900 text-white px-5 py-3 rounded-lg font-semibold text-sm hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            <svg v-if="loading" class="animate-spin h-4 w-4 text-white shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/>
            </svg>
            {{ loading ? t('tessie.load_btn_loading') : t('tessie.load_btn') }}
          </button>
        </div>

        <!-- Step 2: Fahrzeugauswahl -->
        <div v-if="step === 'vehicles'" class="space-y-4">
          <div class="flex items-center justify-between">
            <p class="text-sm text-gray-700 dark:text-gray-300 font-medium">
              {{ t('tessie.step2_found', { n: vehicles.length }) }}
            </p>
            <button @click="toggleAll" class="text-xs text-indigo-600 dark:text-indigo-400 hover:underline">
              {{ allSelected() ? t('tessie.deselect_all') : t('tessie.select_all') }}
            </button>
          </div>

          <!-- VIN-Kacheln -->
          <div class="space-y-2">
            <button
              v-for="v in vehicles"
              :key="v.vin"
              @click="toggleVin(v.vin)"
              :class="[
                'w-full text-left px-4 py-3 rounded-lg border-2 transition-all',
                selected.has(v.vin)
                  ? 'border-gray-900 dark:border-gray-300 bg-gray-50 dark:bg-gray-700'
                  : 'border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 opacity-60'
              ]"
            >
              <div class="flex items-center gap-3">
                <div :class="['w-5 h-5 rounded-full border-2 shrink-0 flex items-center justify-center transition-colors',
                  selected.has(v.vin) ? 'border-gray-900 dark:border-gray-200 bg-gray-900 dark:bg-gray-200' : 'border-gray-300 dark:border-gray-500']">
                  <div v-if="selected.has(v.vin)" class="w-2 h-2 rounded-full bg-white dark:bg-gray-900" />
                </div>
                <div class="min-w-0">
                  <p class="font-semibold text-sm text-gray-900 dark:text-gray-100 truncate">{{ v.displayName }}</p>
                  <p class="text-xs font-mono text-gray-500 dark:text-gray-400 mt-0.5">{{ v.vin }}</p>
                  <span v-if="!v.isActive" class="text-[10px] bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300 px-1.5 py-0.5 rounded-full font-semibold">{{ t('tessie.inactive') }}</span>
                </div>
              </div>
            </button>
          </div>

          <div class="flex gap-2 pt-2">
            <button @click="step = 'token'" class="flex-1 px-4 py-2.5 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg text-sm font-medium hover:bg-gray-200 dark:hover:bg-gray-600 transition">
              {{ t('tessie.back_btn') }}
            </button>
            <button
              @click="startImport"
              :disabled="selected.size === 0"
              class="btn-3d flex-1 px-4 py-2.5 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-800 disabled:opacity-40 disabled:cursor-not-allowed transition"
            >
              {{ t('tessie.import_btn', { n: selected.size }) }}
            </button>
          </div>
        </div>

        <!-- Step 3: Importing -->
        <div v-if="step === 'importing'" class="text-center space-y-4 py-4">
          <svg class="animate-spin h-14 w-14 text-gray-700 dark:text-gray-300 mx-auto" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/>
          </svg>
          <p class="font-semibold text-gray-800 dark:text-gray-200">{{ t('tessie.importing_title') }}</p>
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ currentVin }}</p>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('tessie.importing_progress', { current: currentIndex, total: selected.size }) }}</p>
          <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
            <div class="bg-gray-900 dark:bg-gray-300 h-2 rounded-full transition-all duration-300" :style="{ width: `${(currentIndex / selected.size) * 100}%` }" />
          </div>
        </div>

        <!-- Step 4: Done -->
        <div v-if="step === 'done'" class="space-y-4">
          <div class="text-center">
            <CheckCircleIcon class="h-14 w-14 text-green-500 mx-auto mb-3" />
            <h3 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ t('tessie.done_title') }}</h3>
          </div>

          <!-- Gesamtzusammenfassung -->
          <div class="bg-gray-50 dark:bg-gray-900 rounded-lg p-4 space-y-1.5">
            <p class="text-sm font-semibold text-green-700 dark:text-green-400">{{ t('tessie.done_drives', { n: totalDrives() }) }}</p>
            <p class="text-sm font-semibold text-green-700 dark:text-green-400">{{ t('tessie.done_charges', { n: totalCharges() }) }}</p>
            <p v-if="totalSkipped() > 0" class="text-sm text-amber-600 dark:text-amber-400">{{ t('tessie.done_skipped', { n: totalSkipped() }) }}</p>
          </div>

          <!-- Pro-VIN-Details -->
          <div v-if="results.length > 1" class="space-y-2">
            <p class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">{{ t('tessie.done_per_vehicle') }}</p>
            <div
              v-for="r in results"
              :key="r.vin"
              :class="['px-3 py-2 border rounded-lg', r.error
                ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-700'
                : 'bg-white dark:bg-gray-700 border-gray-200 dark:border-gray-600']"
            >
              <div class="flex items-start gap-2">
                <ExclamationCircleIcon v-if="r.error" class="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{{ r.name }}</p>
                  <p class="text-xs font-mono text-gray-400 dark:text-gray-500">{{ r.vin }}</p>
                  <p v-if="r.error" class="text-xs text-red-600 dark:text-red-400 mt-0.5">{{ r.error }}</p>
                  <p v-else class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                    {{ t('tessie.done_drives', { n: r.result.drivesImported }) }} · {{ t('tessie.done_charges', { n: r.result.chargesImported }) }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- Fehler-Hinweis wenn mind. eine VIN gescheitert ist -->
          <div v-if="hasErrors()" class="flex items-start gap-2 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg">
            <ExclamationCircleIcon class="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
            <p class="text-xs text-red-700 dark:text-red-300">{{ t('tessie.done_partial_error') }}</p>
          </div>

          <p class="text-xs text-gray-500 dark:text-gray-400 text-center">{{ t('tessie.done_hint') }}</p>

          <button
            @click="emit('close')"
            class="btn-3d w-full px-5 py-3 bg-gray-900 text-white rounded-lg font-semibold text-sm hover:bg-gray-800 transition"
          >
            {{ t('tessie.done_btn') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
