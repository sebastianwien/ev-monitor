<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { spritMonitorService, SpritMonitorVehicle, ImportResult, RefreshRawResult, spritMonitorErrorKey } from '../../api/spritMonitorService';
import type { Car } from '../../api/carService';
import { carLabel } from '../../composables/useCarLabel';
import { useCarStore } from '../../stores/car';
import { useCoinStore } from '../../stores/coins';
import { TrashIcon, ExclamationTriangleIcon, ArrowPathIcon, ArrowLeftIcon, XMarkIcon, LockClosedIcon, CheckCircleIcon, ArrowTopRightOnSquareIcon, ArrowDownTrayIcon } from '@heroicons/vue/24/outline';
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_BTN_DANGER, CI_ICON_BTN, CI_INPUT, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses';
import BottomSheet from '../shared/BottomSheet.vue';

const { t } = useI18n();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const coinStore = useCoinStore()
const carStore = useCarStore()


type ImportStep = 'token' | 'mapping' | 'importing' | 'done' | 'refreshing' | 'refreshDone';

const importStep = ref<ImportStep>('token');
const token = ref('');
const spritMonitorVehicles = ref<SpritMonitorVehicle[]>([]);
const myCars = ref<Car[]>([]);
const vehicleMapping = ref<Record<number, string>>({});
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
  spritMonitorVehicles.value.some(v => !!vehicleMapping.value[v.id])
);

onMounted(async () => {
  try {
    myCars.value = await carStore.getCars();
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
    importStep.value = 'mapping';
  } catch (e: any) {
    error.value = t(spritMonitorErrorKey(e));
  } finally {
    loading.value = false;
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

  importStep.value = 'importing';
  totalVehicles.value = vehiclesToImport.length;

  for (let i = 0; i < vehiclesToImport.length; i++) {
    currentVehicle.value = i + 1;
    const vehicle = vehiclesToImport[i];
    const mapping = vehicleMapping.value[vehicle.id];

    try {
      const carId = mapping;

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

  const vehiclesToRefresh = spritMonitorVehicles.value.filter(v => !!vehicleMapping.value[v.id]);

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
  <BottomSheet ref="sheet" edge :label="t('spritmonitor.title')" testid="spritmonitor-sheet" panel-class="sm:max-w-xl" @close="emit('close')">
    <!-- Zweite Ebene im selben Sheet: alle Importe löschen (nie Sheet auf Sheet) -->
    <template v-if="showDeleteConfirm">
      <div class="flex shrink-0 items-center gap-2 border-b border-gray-200 px-2 pb-3 pt-3 sm:px-3 dark:border-white/10" role="alertdialog" :aria-label="t('spritmonitor.delete_title')">
        <button type="button" :disabled="deleteLoading" :aria-label="t('common.back')"
          class="grid h-11 w-11 shrink-0 place-items-center rounded-full text-gray-600 hover:bg-black/10 dark:text-gray-300 dark:hover:bg-white/10"
          @click="showDeleteConfirm = false">
          <ArrowLeftIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <h2 class="text-base font-bold text-gray-900 dark:text-gray-100">{{ t('spritmonitor.delete_title') }}</h2>
      </div>
      <div class="grid gap-4 overflow-y-auto px-4 py-4 sm:px-5">
        <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-300" v-html="t('spritmonitor.delete_desc')" />
        <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('spritmonitor.delete_confirm') }}</p>
        <p v-if="deleteError" role="alert" :class="[CI_NOTE, 'flex gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
          <ExclamationTriangleIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ deleteError }}
        </p>
      </div>
      <div class="flex shrink-0 gap-2 border-t border-gray-200 px-4 py-3 sm:px-5 dark:border-white/10">
        <button type="button" :class="[CI_BTN_SECONDARY, 'flex-1']" :disabled="deleteLoading" @click="showDeleteConfirm = false">
          {{ t('spritmonitor.delete_cancel') }}
        </button>
        <button type="button" :class="[CI_BTN_DANGER, 'flex-1']" :disabled="deleteLoading" @click="deleteAllImports">
          <ArrowPathIcon v-if="deleteLoading" class="h-5 w-5 animate-spin" aria-hidden="true" />
          {{ deleteLoading ? t('spritmonitor.delete_btn_loading') : t('spritmonitor.delete_btn') }}
        </button>
      </div>
    </template>

    <template v-else>
    <!-- Kopf -->
    <div class="flex shrink-0 items-center justify-between gap-3 border-b border-gray-200 px-4 pb-3 pt-4 sm:px-5 dark:border-white/10">
      <div class="flex min-w-0 items-center gap-3">
        <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-200">
          <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />
        </span>
        <h2 class="min-w-0 text-base font-bold leading-tight text-gray-900 dark:text-gray-100">{{ t('spritmonitor.title') }}</h2>
      </div>
      <button type="button" :aria-label="t('common.close')" :class="CI_ICON_BTN" @click="close">
        <XMarkIcon class="h-5 w-5" aria-hidden="true" />
      </button>
    </div>

    <!-- Inhalt (scrollt) -->
    <div class="grid flex-1 content-start gap-4 overflow-y-auto px-4 py-4 sm:px-5">
      <p v-if="error" role="alert" :class="[CI_NOTE, 'flex gap-2.5 py-3 font-semibold text-gray-900 dark:text-gray-100']">
        <ExclamationTriangleIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />{{ error }}
      </p>

      <!-- Schritt 1: Token -->
      <template v-if="importStep === 'token'">
        <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-300">{{ t('spritmonitor.step1_intro') }}</p>

        <div class="grid gap-1.5">
          <label for="sm-token" :class="CI_LABEL">{{ t('spritmonitor.token_label') }}</label>
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
            :class="[CI_INPUT, 'font-mono']" />
          <p class="text-[13px] text-gray-500 dark:text-gray-400">
            {{ t('spritmonitor.token_hint_pre') }}
            <a href="https://www.spritmonitor.de/de/mein_account/passwort_aendern.html" target="_blank" rel="noopener noreferrer"
              class="inline-flex items-center gap-0.5 font-semibold text-gray-900 underline underline-offset-2 hover:no-underline dark:text-gray-100">
              {{ t('spritmonitor.token_hint_link') }}<ArrowTopRightOnSquareIcon class="h-3.5 w-3.5" aria-hidden="true" />
            </a>
          </p>
        </div>

        <p :class="[CI_NOTE, 'flex gap-2.5 py-3 text-[13px]']">
          <LockClosedIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />{{ t('spritmonitor.privacy_note') }}
        </p>

        <button type="button" data-testid="spritmonitor-load-vehicles" :class="[CI_BTN_PRIMARY, 'w-full']" :disabled="loading" @click="fetchVehicles">
          <ArrowPathIcon v-if="loading" class="h-5 w-5 animate-spin" aria-hidden="true" />
          {{ loading ? t('spritmonitor.load_btn_loading') : t('spritmonitor.load_btn') }}
        </button>

        <!-- Bisherige Importe löschen: bewusst leise, rot erst beim Bestätigen -->
        <div class="border-t border-gray-200 pt-3 dark:border-white/10">
          <p class="text-[13px] leading-relaxed text-gray-500 dark:text-gray-400">{{ t('spritmonitor.danger_desc') }}</p>
          <button type="button"
            class="mt-1 inline-flex min-h-11 items-center gap-1.5 text-[13px] font-semibold text-gray-700 underline-offset-2 hover:underline dark:text-gray-300"
            @click="showDeleteConfirm = true">
            <TrashIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('spritmonitor.danger_title') }}
          </button>
        </div>
      </template>

      <!-- Schritt 2: Zuordnung -->
      <template v-if="importStep === 'mapping'">
        <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-300" v-html="t('spritmonitor.step2_intro', { n: spritMonitorVehicles.length })" />

        <div class="grid gap-3">
          <div v-for="vehicle in spritMonitorVehicles" :key="vehicle.id" :class="[CI_CARD, 'grid gap-2.5']">
            <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ vehicle.make }} {{ vehicle.model }}</h3>
            <div class="grid gap-1.5">
              <label :for="`sm-map-${vehicle.id}`" :class="CI_LABEL">{{ t('spritmonitor.mapping_label') }}</label>
              <select :id="`sm-map-${vehicle.id}`" v-model="vehicleMapping[vehicle.id]" :class="CI_INPUT">
                <option value="">{{ t('spritmonitor.mapping_placeholder') }}</option>
                <option v-for="car in myCars" :key="car.id" :value="car.id">{{ carLabel(car) }}</option>
              </select>
            </div>
          </div>
        </div>

        <p class="text-[13px] text-gray-500 dark:text-gray-400">
          {{ t('spritmonitor.car_missing_hint') }}
          <router-link to="/cars" class="font-semibold text-gray-900 underline dark:text-gray-100">{{ t('spritmonitor.car_missing_link') }}</router-link>
        </p>
        <p class="text-[13px] text-gray-500 dark:text-gray-400">{{ t('spritmonitor.skip_hint') }}</p>

        <!-- Rohdaten aktualisieren: sekundär -->
        <div class="grid gap-2 border-t border-gray-200 pt-3 dark:border-white/10">
          <h3 :class="[CI_LABEL, 'flex items-center gap-2']">
            <ArrowPathIcon class="h-4 w-4" aria-hidden="true" />{{ t('spritmonitor.refresh_raw_title') }}
          </h3>
          <p class="text-[13px] leading-relaxed text-gray-500 dark:text-gray-400" v-html="t('spritmonitor.refresh_raw_desc')" />
          <button type="button" :class="[CI_BTN_SECONDARY, 'justify-self-start']" :disabled="!hasRefreshableVehicles"
            :title="!hasRefreshableVehicles ? t('spritmonitor.err_no_mapping') : undefined" @click="startRefresh">
            <ArrowPathIcon class="h-5 w-5" aria-hidden="true" />
            {{ t('spritmonitor.refresh_raw_btn') }}
          </button>
        </div>
      </template>

      <!-- Laufender Import / Refresh -->
      <div v-if="importStep === 'importing' || importStep === 'refreshing'" class="grid justify-items-center gap-4 py-6 text-center" aria-live="polite">
        <ArrowPathIcon class="h-10 w-10 animate-spin text-gray-400" aria-hidden="true" />
        <div>
          <h3 class="text-base font-bold text-gray-900 dark:text-gray-100">{{ importStep === 'importing' ? t('spritmonitor.step3_title') : t('spritmonitor.refresh_raw_loading') }}</h3>
          <p class="mt-1 text-sm tabular-nums text-gray-500 dark:text-gray-400">{{ t('spritmonitor.step3_progress', { current: currentVehicle, total: totalVehicles }) }}</p>
        </div>
        <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700"
          role="progressbar" :aria-valuenow="currentVehicle" :aria-valuemin="0" :aria-valuemax="totalVehicles">
          <div class="h-full rounded-full bg-gray-900 transition-all duration-300 dark:bg-gray-100" :style="{ width: `${(currentVehicle / totalVehicles) * 100}%` }" />
        </div>
      </div>

      <!-- Ergebnis Import -->
      <div v-if="importStep === 'done'" class="grid gap-4">
        <div class="flex items-center gap-3">
          <CheckCircleIcon class="h-10 w-10 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
          <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('spritmonitor.step4_title') }}</h3>
        </div>
        <ul :class="[CI_CARD, 'divide-y divide-gray-200 p-0 text-sm dark:divide-white/10']">
          <li class="px-4 py-2.5 font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ t('spritmonitor.step4_imported', { n: totalImported }) }}</li>
          <li v-if="totalSkipped > 0" class="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">{{ t('spritmonitor.step4_skipped', { n: totalSkipped }) }}</li>
          <li v-if="totalCoinsAwarded > 0" class="px-4 py-2.5 font-semibold tabular-nums text-gray-900 dark:text-gray-100">{{ t('spritmonitor.step4_coins', { n: totalCoinsAwarded }) }}</li>
          <li v-if="totalWithoutLocation > 0" class="px-4 py-2.5 text-[13px] text-gray-600 dark:text-gray-400">{{ t('spritmonitor.step4_no_location', { n: totalWithoutLocation }) }}</li>
        </ul>
        <div v-if="totalErrors.length > 0" :class="CI_NOTE">
          <p :class="[CI_LABEL, 'mb-1.5']">{{ t('spritmonitor.step4_errors_title') }}</p>
          <ul class="list-inside list-disc space-y-0.5 text-[13px] text-gray-900 dark:text-gray-100">
            <li v-for="(err, idx) in totalErrors" :key="idx">{{ err }}</li>
          </ul>
        </div>
      </div>

      <!-- Ergebnis Refresh -->
      <div v-if="importStep === 'refreshDone' && refreshResult" class="grid gap-4">
        <div class="flex items-center gap-3">
          <CheckCircleIcon class="h-10 w-10 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
          <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('spritmonitor.refresh_done_title') }}</h3>
        </div>
        <ul :class="[CI_CARD, 'divide-y divide-gray-200 p-0 text-sm dark:divide-white/10']">
          <li class="px-4 py-2.5 font-bold tabular-nums text-gray-900 dark:text-gray-100">{{ t('spritmonitor.refresh_done_refreshed', { n: refreshResult.refreshed }) }}</li>
          <li v-if="refreshResult.skipped > 0" class="px-4 py-2.5 tabular-nums text-gray-700 dark:text-gray-300">{{ t('spritmonitor.refresh_done_skipped', { n: refreshResult.skipped }) }}</li>
        </ul>
        <div v-if="refreshResult.errors.length > 0" :class="CI_NOTE">
          <p :class="[CI_LABEL, 'mb-1.5']">{{ t('spritmonitor.refresh_done_errors_title') }}</p>
          <ul class="list-inside list-disc space-y-0.5 text-[13px] text-gray-900 dark:text-gray-100">
            <li v-for="(err, idx) in refreshResult.errors" :key="idx">{{ err }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Fuß: Primäraktion je Schritt -->
    <div v-if="importStep === 'mapping' || importStep === 'done' || importStep === 'refreshDone'"
      class="flex shrink-0 gap-2 border-t border-gray-200 bg-white px-4 py-3 sm:px-5 dark:border-white/10 dark:bg-gray-800">
      <button v-if="importStep === 'refreshDone'" type="button" :class="[CI_BTN_SECONDARY, 'flex-1']" @click="importStep = 'mapping'">
        {{ t('spritmonitor.back_to_mapping') }}
      </button>
      <button type="button" data-testid="spritmonitor-primary"
        :class="[importStep === 'mapping' ? CI_BTN_PRIMARY : CI_BTN_SECONDARY, 'flex-1']"
        @click="importStep === 'mapping' ? startImport() : close()">
        <ArrowDownTrayIcon v-if="importStep === 'mapping'" class="h-5 w-5" aria-hidden="true" />
        {{ importStep === 'mapping' ? t('spritmonitor.start_import_btn') : (importStep === 'done' ? t('spritmonitor.done_btn') : t('spritmonitor.refresh_done_btn')) }}
      </button>
    </div>
    </template>
  </BottomSheet>
</template>
