<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { spritMonitorService, SpritMonitorVehicle, ImportResult, RefreshRawResult, spritMonitorErrorKey } from '../../api/spritMonitorService';
import { carService, Car, BrandInfo, ModelInfo } from '../../api/carService';
import { useCarStore } from '../../stores/car';
import { useCoinStore } from '../../stores/coins';
import { TrashIcon, ExclamationTriangleIcon, ArrowPathIcon, XMarkIcon, LockClosedIcon, CheckCircleIcon, ArrowTopRightOnSquareIcon, ArrowDownTrayIcon } from '@heroicons/vue/24/outline';
import BottomSheet from '../shared/BottomSheet.vue';

const { t } = useI18n();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const coinStore = useCoinStore()
const carStore = useCarStore()

const enumToLabel = (v: string | null | undefined): string => {
  if (!v) return ''
  return v.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
}
const carDisplayName = (brand: string | null | undefined, model: string | null | undefined): string => {
  const b = enumToLabel(brand); const m = enumToLabel(model)
  return m.toLowerCase().startsWith(b.toLowerCase()) ? m : `${b} ${m}`.trim()
}
const carLabel = (car: Car) => {
  const base = carDisplayName(car.brand, car.model)
  const name = car.trim ? `${base} ${car.trim}` : base
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

type ImportStep = 'token' | 'mapping' | 'importing' | 'done' | 'refreshing' | 'refreshDone';

const importStep = ref<ImportStep>('token');
const token = ref('');
const spritMonitorVehicles = ref<SpritMonitorVehicle[]>([]);
const myCars = ref<Car[]>([]);
const brands = ref<BrandInfo[]>([]);
const vehicleMapping = ref<Record<number, string>>({});
const newCarData = ref<Record<number, { brand: string; model: string; year: number; availableModels: ModelInfo[] }>>({});
const importResults = ref<Record<number, ImportResult>>({});
const totalImported = ref(0);
const totalSkipped = ref(0);
const totalCoinsAwarded = ref(0);
const totalWithoutLocation = ref(0);
const totalErrors = ref<string[]>([]);
const currentVehicle = ref(0);
const totalVehicles = ref(0);
const error = ref('');
const loading = ref(false);
const showDeleteConfirm = ref(false);
const deleteLoading = ref(false);
const deleteError = ref('');
const refreshResult = ref<RefreshRawResult | null>(null);
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null);

const hasRefreshableVehicles = computed(() =>
  spritMonitorVehicles.value.some(v => !!vehicleMapping.value[v.id] && vehicleMapping.value[v.id] !== 'new')
);

onMounted(async () => {
  try {
    const [cars, brandList] = await Promise.all([
      carStore.getCars(),
      carStore.getBrands(),
    ]);
    myCars.value = cars;
    brands.value = brandList;
  } catch (e) {
    console.error('Failed to load data:', e);
  }
});

const fetchVehicles = async () => {
  if (!token.value.trim()) {
    error.value = t('spritmonitor.err_token_empty');
    return;
  }

  error.value = '';
  loading.value = true;
  try {
    const vehicles = await spritMonitorService.fetchVehicles(token.value);
    if (vehicles.length === 0) {
      error.value = t('spritmonitor.err_no_vehicles');
      return;
    }
    spritMonitorVehicles.value = vehicles;
    // Initialize newCarData for all vehicles upfront to prevent template crash
    // when user selects "Neues Auto anlegen" before a brand is chosen
    vehicles.forEach(v => {
      newCarData.value[v.id] = { brand: '', model: '', year: new Date().getFullYear(), availableModels: [] };
    });
    importStep.value = 'mapping';
  } catch (e: any) {
    error.value = t(spritMonitorErrorKey(e));
  } finally {
    loading.value = false;
  }
};

const onBrandChange = async (vehicleId: number, brandValue: string) => {
  if (!newCarData.value[vehicleId]) {
    newCarData.value[vehicleId] = {
      brand: brandValue,
      model: '',
      year: new Date().getFullYear(),
      availableModels: []
    };
  } else {
    newCarData.value[vehicleId].brand = brandValue;
    newCarData.value[vehicleId].model = '';
  }

  // Load models for this brand
  try {
    const models = await carStore.getModelsForBrand(brandValue);
    newCarData.value[vehicleId].availableModels = models;
  } catch (e) {
    console.error('Failed to load models:', e);
  }
};

const startImport = async () => {
  error.value = '';
  totalImported.value = 0;
  totalSkipped.value = 0;
  totalCoinsAwarded.value = 0;
  totalWithoutLocation.value = 0;
  totalErrors.value = [];
  importResults.value = {};

  // Validate mappings - empty = skip vehicle (not an error)
  const vehiclesToImport = spritMonitorVehicles.value.filter(v => !!vehicleMapping.value[v.id]);

  if (vehiclesToImport.length === 0) {
    error.value = t('spritmonitor.err_no_mapping');
    return;
  }

  for (const vehicle of vehiclesToImport) {
    const mapping = vehicleMapping.value[vehicle.id];
    if (mapping === 'new') {
      const newCar = newCarData.value[vehicle.id];
      if (!newCar || !newCar.brand || !newCar.model || !newCar.year) {
        error.value = t('spritmonitor.err_missing_data', { vehicle: `${vehicle.make} ${vehicle.model}` });
        return;
      }
    }
  }

  importStep.value = 'importing';
  totalVehicles.value = vehiclesToImport.length;

  for (let i = 0; i < vehiclesToImport.length; i++) {
    currentVehicle.value = i + 1;
    const vehicle = vehiclesToImport[i];
    const mapping = vehicleMapping.value[vehicle.id];

    try {
      let carId: string;

      // Create new car if needed
      if (mapping === 'new') {
        const newCar = newCarData.value[vehicle.id];
        const created = await carService.createCar({
          model: newCar.model,
          year: newCar.year,
          customNetCapacityKwh: 50, // Default placeholder (user can edit later)
          powerKw: null,
          batteryDegradationPercent: null,
          hasHeatPump: false,
          licensePlate: '', // Empty string (user can add later)
          trim: null,
          vehicleSpecificationId: null,
        });
        carId = created.car.id;
        myCars.value.push(created.car);
        carStore.invalidateCars();
      } else {
        carId = mapping;
      }

      // Import fuelings
      const result = await spritMonitorService.importFuelings(token.value, vehicle.id, vehicle.mainTank, carId);
      importResults.value[vehicle.id] = result;
      totalImported.value += result.imported;
      totalSkipped.value += result.skipped;
      totalCoinsAwarded.value += result.coinsAwarded ?? 0;
      totalWithoutLocation.value += result.withoutLocation ?? 0;
      totalErrors.value.push(...result.errors);
    } catch (e: any) {
      const errorMsg = `${vehicle.make} ${vehicle.model}: ${e.response?.data?.error || e.message}`;
      totalErrors.value.push(errorMsg);
    }
  }

  importStep.value = 'done';
  if (totalCoinsAwarded.value > 0) coinStore.refresh();
};

const startRefresh = async () => {
  error.value = '';
  refreshResult.value = null;

  const vehiclesToRefresh = spritMonitorVehicles.value.filter(v => !!vehicleMapping.value[v.id] && vehicleMapping.value[v.id] !== 'new');

  if (vehiclesToRefresh.length === 0) {
    error.value = t('spritmonitor.err_no_mapping');
    return;
  }

  importStep.value = 'refreshing';
  totalVehicles.value = vehiclesToRefresh.length;

  let totalRefreshed = 0;
  let totalSkipped = 0;
  const allErrors: string[] = [];

  for (let i = 0; i < vehiclesToRefresh.length; i++) {
    currentVehicle.value = i + 1;
    const vehicle = vehiclesToRefresh[i];
    const carId = vehicleMapping.value[vehicle.id];

    try {
      const result = await spritMonitorService.refreshRawImportData(token.value, vehicle.id, vehicle.mainTank, carId);
      totalRefreshed += result.refreshed;
      totalSkipped += result.skipped;
      allErrors.push(...result.errors);
    } catch (e: any) {
      allErrors.push(`${vehicle.make} ${vehicle.model}: ${e.response?.data?.error || e.message}`);
    }
  }

  refreshResult.value = { refreshed: totalRefreshed, skipped: totalSkipped, errors: allErrors };
  importStep.value = 'refreshDone';
};

const deleteAllImports = async () => {
  deleteError.value = '';
  deleteLoading.value = true;
  try {
    await spritMonitorService.deleteAllImports();
    showDeleteConfirm.value = false;
    // Reload page or emit event to parent
    window.location.reload();
  } catch (e: any) {
    deleteError.value = e.response?.data?.error || t('spritmonitor.err_delete');
  } finally {
    deleteLoading.value = false;
  }
};

const close = () => {
  sheet.value?.requestClose();
};
</script>


<template>
  <BottomSheet ref="sheet" :label="t('spritmonitor.title')" testid="spritmonitor-sheet" panel-class="sm:max-w-xl" @close="emit('close')">
    <!-- Kopf -->
    <div class="flex items-center justify-between gap-3 px-4 sm:px-5 pt-4 pb-3 border-b-2 border-gray-200 dark:border-gray-700 shrink-0">
      <div class="flex items-center gap-3 min-w-0">
        <span class="shrink-0 w-9 h-9 rounded-sm bg-sky-700 text-white flex items-center justify-center">
          <ArrowDownTrayIcon class="w-5 h-5" aria-hidden="true" />
        </span>
        <div class="min-w-0">
          <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 leading-tight">{{ t('spritmonitor.title') }}</h2>
        </div>
      </div>
      <button type="button" @click="close" :aria-label="t('common.close')"
        class="-mr-2 p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center text-gray-500 hover:text-gray-900 dark:hover:text-gray-100">
        <XMarkIcon class="w-5 h-5" />
      </button>
    </div>

    <!-- Inhalt (scrollt) -->
    <div class="flex-1 overflow-y-auto px-4 sm:px-5 py-4 space-y-4">
      <div v-if="error" role="alert" class="flex gap-2.5 p-3 rounded-sm border-2 border-red-300 dark:border-red-800 bg-red-50 dark:bg-red-900/30 text-sm text-red-800 dark:text-red-200">
        <ExclamationTriangleIcon class="w-5 h-5 shrink-0 mt-0.5" aria-hidden="true" />
        <p>{{ error }}</p>
      </div>

      <!-- Schritt 1: Token -->
      <template v-if="importStep === 'token'">
        <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">{{ t('spritmonitor.step1_intro') }}</p>

        <div class="space-y-1.5">
          <label for="sm-token" class="block text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('spritmonitor.token_label') }}</label>
          <input
            id="sm-token"
            v-model="token"
            type="text"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
            inputmode="text"
            :placeholder="t('spritmonitor.token_placeholder')"
            @keyup.enter="fetchVehicles"
            class="w-full min-h-[48px] px-3 py-2.5 font-mono text-sm rounded-sm border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:outline-none focus:border-sky-600 transition-colors" />
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ t('spritmonitor.token_hint_pre') }}
            <a href="https://www.spritmonitor.de/de/mein_account/passwort_aendern.html" target="_blank" rel="noopener noreferrer"
              class="inline-flex items-center gap-0.5 font-semibold text-sky-700 dark:text-sky-400 underline underline-offset-2 hover:text-sky-600">
              {{ t('spritmonitor.token_hint_link') }}<ArrowTopRightOnSquareIcon class="w-3.5 h-3.5" aria-hidden="true" />
            </a>
          </p>
        </div>

        <div class="flex gap-2.5 p-3 rounded-sm bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 text-xs text-gray-600 dark:text-gray-400">
          <LockClosedIcon class="w-4 h-4 shrink-0 mt-0.5 text-gray-400" aria-hidden="true" />
          <p>{{ t('spritmonitor.privacy_note') }}</p>
        </div>

        <button
          type="button"
          @click="fetchVehicles"
          :disabled="loading"
          data-testid="spritmonitor-load-vehicles"
          class="flex w-full items-center justify-center gap-2 min-h-[48px] bg-sky-700 hover:bg-sky-600 text-white font-bold uppercase tracking-wider text-xs px-5 py-3 rounded-sm border-2 border-sky-700 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#e5e7eb] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75 disabled:opacity-60 disabled:cursor-not-allowed disabled:active:translate-x-0 disabled:active:translate-y-0">
          <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" aria-hidden="true" />
          {{ loading ? t('spritmonitor.load_btn_loading') : t('spritmonitor.load_btn') }}
        </button>

        <!-- Bisherige Importe löschen: bewusst leise -->
        <div class="pt-3 mt-2 border-t border-dashed border-gray-200 dark:border-gray-700">
          <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed">{{ t('spritmonitor.danger_desc') }}</p>
          <button
            type="button"
            @click="showDeleteConfirm = true"
            class="mt-1.5 inline-flex items-center gap-1.5 min-h-[44px] text-xs font-semibold text-red-700 dark:text-red-400 hover:underline underline-offset-2">
            <TrashIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('spritmonitor.danger_title') }}
          </button>
        </div>
      </template>

      <!-- Schritt 2: Zuordnung -->
      <template v-if="importStep === 'mapping'">
        <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed" v-html="t('spritmonitor.step2_intro', { n: spritMonitorVehicles.length })" />

        <div class="space-y-3">
          <div v-for="vehicle in spritMonitorVehicles" :key="vehicle.id"
            class="rounded-sm border-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-3 sm:p-4 space-y-2.5">
            <h3 class="font-bold text-sm text-gray-900 dark:text-gray-100">{{ vehicle.make }} {{ vehicle.model }}</h3>

            <div class="space-y-1">
              <label :for="`sm-map-${vehicle.id}`" class="block text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">{{ t('spritmonitor.mapping_label') }}</label>
              <select
                :id="`sm-map-${vehicle.id}`"
                v-model="vehicleMapping[vehicle.id]"
                class="w-full min-h-[44px] border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 dark:text-gray-100 rounded-sm px-3 py-2 text-sm font-medium focus:outline-none focus:border-sky-600 transition-colors">
                <option value="">{{ t('spritmonitor.mapping_placeholder') }}</option>
                <option value="new">{{ t('spritmonitor.mapping_new') }}</option>
                <option v-for="car in myCars" :key="car.id" :value="car.id">{{ carLabel(car) }}</option>
              </select>
            </div>

            <div v-if="vehicleMapping[vehicle.id] === 'new'" class="rounded-sm border-2 border-dashed border-sky-300 dark:border-sky-800 bg-sky-50/60 dark:bg-sky-900/20 p-3 space-y-2">
              <p class="text-xs font-bold uppercase tracking-wider text-sky-800 dark:text-sky-300">{{ t('spritmonitor.new_car_label') }}</p>
              <select
                :value="newCarData[vehicle.id]?.brand || ''"
                :aria-label="t('spritmonitor.brand_placeholder')"
                @change="(e) => onBrandChange(vehicle.id, (e.target as HTMLSelectElement).value)"
                class="w-full min-h-[44px] border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 dark:text-gray-100 rounded-sm px-3 py-2 text-sm focus:outline-none focus:border-sky-600">
                <option value="">{{ t('spritmonitor.brand_placeholder') }}</option>
                <option v-for="brand in brands" :key="brand.value" :value="brand.value">{{ brand.label }}</option>
              </select>
              <select
                v-if="newCarData[vehicle.id]"
                v-model="newCarData[vehicle.id].model"
                :disabled="!newCarData[vehicle.id]?.brand"
                :aria-label="t('spritmonitor.model_placeholder_brand_selected')"
                class="w-full min-h-[44px] border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 dark:text-gray-100 rounded-sm px-3 py-2 text-sm focus:outline-none focus:border-sky-600 disabled:bg-gray-100 dark:disabled:bg-gray-700 disabled:text-gray-400">
                <option value="">{{ newCarData[vehicle.id]?.brand ? t('spritmonitor.model_placeholder_brand_selected') : t('spritmonitor.model_placeholder_no_brand') }}</option>
                <option v-for="model in newCarData[vehicle.id]?.availableModels || []" :key="model.value" :value="model.value">{{ model.label }}</option>
              </select>
              <input
                v-if="newCarData[vehicle.id]"
                v-model.number="newCarData[vehicle.id].year"
                type="number"
                inputmode="numeric"
                :placeholder="t('spritmonitor.year_placeholder')"
                :aria-label="t('spritmonitor.year_placeholder')"
                min="2000"
                max="2030"
                class="w-full min-h-[44px] border-2 border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 dark:text-gray-100 rounded-sm px-3 py-2 text-sm focus:outline-none focus:border-sky-600" />
            </div>
          </div>
        </div>

        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('spritmonitor.skip_hint') }}</p>

        <!-- Rohdaten aktualisieren: sekundär -->
        <div class="pt-3 border-t border-dashed border-gray-200 dark:border-gray-700 space-y-2">
          <div class="flex items-center gap-2">
            <ArrowPathIcon class="w-4 h-4 text-gray-500" aria-hidden="true" />
            <h3 class="text-xs font-bold uppercase tracking-wider text-gray-600 dark:text-gray-300">{{ t('spritmonitor.refresh_raw_title') }}</h3>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed" v-html="t('spritmonitor.refresh_raw_desc')" />
          <button
            type="button"
            @click="startRefresh"
            :disabled="!hasRefreshableVehicles"
            :title="!hasRefreshableVehicles ? t('spritmonitor.err_no_mapping') : undefined"
            class="inline-flex items-center gap-1.5 min-h-[44px] px-3 text-xs font-semibold text-sky-700 dark:text-sky-400 border-2 border-sky-200 dark:border-sky-900 rounded-sm hover:bg-sky-50 dark:hover:bg-sky-900/30 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
            <ArrowPathIcon class="w-4 h-4" aria-hidden="true" />
            {{ t('spritmonitor.refresh_raw_btn') }}
          </button>
        </div>
      </template>

      <!-- Laufender Import / Refresh -->
      <div v-if="importStep === 'importing' || importStep === 'refreshing'" class="py-6 text-center space-y-4" aria-live="polite">
        <span class="mx-auto block w-12 h-12 border-4 border-sky-200 dark:border-sky-900 border-t-sky-700 rounded-full animate-spin" aria-hidden="true" />
        <div>
          <h3 class="text-base font-bold text-gray-900 dark:text-gray-100">{{ importStep === 'importing' ? t('spritmonitor.step3_title') : t('spritmonitor.refresh_raw_loading') }}</h3>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('spritmonitor.step3_progress', { current: currentVehicle, total: totalVehicles }) }}</p>
        </div>
        <div class="w-full h-2.5 rounded-sm border border-gray-300 dark:border-gray-600 bg-gray-100 dark:bg-gray-900 overflow-hidden"
          role="progressbar" :aria-valuenow="currentVehicle" :aria-valuemin="0" :aria-valuemax="totalVehicles">
          <div class="h-full bg-sky-700 transition-all duration-300" :style="{ width: `${(currentVehicle / totalVehicles) * 100}%` }" />
        </div>
      </div>

      <!-- Ergebnis Import -->
      <div v-if="importStep === 'done'" class="space-y-4">
        <div class="flex items-center gap-3">
          <CheckCircleIcon class="w-10 h-10 text-green-600 shrink-0" aria-hidden="true" />
          <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('spritmonitor.step4_title') }}</h3>
        </div>
        <ul class="rounded-sm border-2 border-gray-200 dark:border-gray-700 divide-y divide-gray-200 dark:divide-gray-700 text-sm">
          <li class="px-3 py-2.5 font-bold text-green-700 dark:text-green-400">{{ t('spritmonitor.step4_imported', { n: totalImported }) }}</li>
          <li v-if="totalSkipped > 0" class="px-3 py-2.5 text-amber-700 dark:text-amber-400">{{ t('spritmonitor.step4_skipped', { n: totalSkipped }) }}</li>
          <li v-if="totalCoinsAwarded > 0" class="px-3 py-2.5 font-bold text-sky-700 dark:text-sky-400">{{ t('spritmonitor.step4_coins', { n: totalCoinsAwarded }) }}</li>
          <li v-if="totalWithoutLocation > 0" class="px-3 py-2.5 text-xs text-gray-600 dark:text-gray-400">{{ t('spritmonitor.step4_no_location', { n: totalWithoutLocation }) }}</li>
        </ul>
        <div v-if="totalErrors.length > 0" class="rounded-sm border-2 border-red-200 dark:border-red-900 bg-red-50 dark:bg-red-900/20 p-3">
          <p class="text-xs font-bold uppercase tracking-wider text-red-700 dark:text-red-300 mb-1.5">{{ t('spritmonitor.step4_errors_title') }}</p>
          <ul class="list-disc list-inside text-xs text-red-800 dark:text-red-200 space-y-0.5">
            <li v-for="(err, idx) in totalErrors" :key="idx">{{ err }}</li>
          </ul>
        </div>
      </div>

      <!-- Ergebnis Refresh -->
      <div v-if="importStep === 'refreshDone' && refreshResult" class="space-y-4">
        <div class="flex items-center gap-3">
          <CheckCircleIcon class="w-10 h-10 text-sky-600 shrink-0" aria-hidden="true" />
          <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('spritmonitor.refresh_done_title') }}</h3>
        </div>
        <ul class="rounded-sm border-2 border-gray-200 dark:border-gray-700 divide-y divide-gray-200 dark:divide-gray-700 text-sm">
          <li class="px-3 py-2.5 font-bold text-sky-700 dark:text-sky-400">{{ t('spritmonitor.refresh_done_refreshed', { n: refreshResult.refreshed }) }}</li>
          <li v-if="refreshResult.skipped > 0" class="px-3 py-2.5 text-amber-700 dark:text-amber-400">{{ t('spritmonitor.refresh_done_skipped', { n: refreshResult.skipped }) }}</li>
        </ul>
        <div v-if="refreshResult.errors.length > 0" class="rounded-sm border-2 border-red-200 dark:border-red-900 bg-red-50 dark:bg-red-900/20 p-3">
          <p class="text-xs font-bold uppercase tracking-wider text-red-700 dark:text-red-300 mb-1.5">{{ t('spritmonitor.refresh_done_errors_title') }}</p>
          <ul class="list-disc list-inside text-xs text-red-800 dark:text-red-200 space-y-0.5">
            <li v-for="(err, idx) in refreshResult.errors" :key="idx">{{ err }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Fuß: Primäraktion je Schritt -->
    <div v-if="importStep === 'mapping' || importStep === 'done' || importStep === 'refreshDone'"
      class="shrink-0 px-4 sm:px-5 py-3 border-t-2 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex gap-2">
      <button
        v-if="importStep === 'refreshDone'"
        type="button"
        @click="importStep = 'mapping'"
        class="flex-1 min-h-[48px] px-4 text-xs font-bold uppercase tracking-wider text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 border-2 border-gray-300 dark:border-gray-600 rounded-sm hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors">
        {{ t('spritmonitor.back_to_mapping') }}
      </button>
      <button
        type="button"
        data-testid="spritmonitor-primary"
        @click="importStep === 'mapping' ? startImport() : close()"
        class="flex-1 flex items-center justify-center gap-2 min-h-[48px] px-5 text-white font-bold uppercase tracking-wider text-xs rounded-sm border-2 shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#e5e7eb] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none transition-[transform,box-shadow] duration-75"
        :class="importStep === 'mapping' ? 'bg-green-600 hover:bg-green-500 border-green-600' : 'bg-sky-700 hover:bg-sky-600 border-sky-700'">
        <ArrowDownTrayIcon v-if="importStep === 'mapping'" class="w-4 h-4" aria-hidden="true" />
        {{ importStep === 'mapping' ? t('spritmonitor.start_import_btn') : (importStep === 'done' ? t('spritmonitor.done_btn') : t('spritmonitor.refresh_done_btn')) }}
      </button>
    </div>

    <!-- Bestätigung: alle Importe löschen -->
    <div
      v-if="showDeleteConfirm"
      class="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-black/50 sm:p-4"
      role="alertdialog"
      aria-modal="true"
      :aria-label="t('spritmonitor.delete_title')"
      @click.self="!deleteLoading && (showDeleteConfirm = false)">
      <div class="w-full sm:max-w-md bg-white dark:bg-gray-800 rounded-t-2xl sm:rounded-sm sm:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:sm:shadow-[5px_5px_0_rgba(255,255,255,0.35)] p-4 sm:p-5 pb-[max(1rem,env(safe-area-inset-bottom))] space-y-4" @click.stop>
        <div class="flex items-center gap-3">
          <span class="shrink-0 w-9 h-9 rounded-sm bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-300 flex items-center justify-center">
            <TrashIcon class="w-5 h-5" aria-hidden="true" />
          </span>
          <h3 class="text-base font-bold text-gray-900 dark:text-gray-100">{{ t('spritmonitor.delete_title') }}</h3>
        </div>
        <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed" v-html="t('spritmonitor.delete_desc')" />
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('spritmonitor.delete_confirm') }}</p>
        <div v-if="deleteError" role="alert" class="flex gap-2 p-3 rounded-sm border-2 border-red-300 dark:border-red-800 bg-red-50 dark:bg-red-900/30 text-sm text-red-800 dark:text-red-200">
          <ExclamationTriangleIcon class="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
          <span>{{ deleteError }}</span>
        </div>
        <div class="flex gap-2">
          <button
            type="button"
            @click="showDeleteConfirm = false"
            :disabled="deleteLoading"
            class="flex-1 min-h-[48px] px-4 text-xs font-bold uppercase tracking-wider text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 border-2 border-gray-300 dark:border-gray-600 rounded-sm hover:bg-gray-50 dark:hover:bg-gray-600 disabled:opacity-50 transition-colors">
            {{ t('spritmonitor.delete_cancel') }}
          </button>
          <button
            type="button"
            @click="deleteAllImports"
            :disabled="deleteLoading"
            class="flex-1 flex items-center justify-center gap-2 min-h-[48px] px-4 text-xs font-bold uppercase tracking-wider text-white bg-red-600 hover:bg-red-500 border-2 border-red-600 rounded-sm shadow-[2px_2px_0_0_#030712] dark:shadow-[2px_2px_0_0_#e5e7eb] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none disabled:opacity-50 transition-[transform,box-shadow] duration-75">
            <span v-if="deleteLoading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" aria-hidden="true" />
            {{ deleteLoading ? t('spritmonitor.delete_btn_loading') : t('spritmonitor.delete_btn') }}
          </button>
        </div>
      </div>
    </div>
  </BottomSheet>
</template>
