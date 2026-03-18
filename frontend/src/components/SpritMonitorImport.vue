<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { spritMonitorService, SpritMonitorVehicle, ImportResult } from '../api/spritMonitorService';
import { carService, Car, BrandInfo, ModelInfo } from '../api/carService';
import { useCarStore } from '../stores/car';
import { useCoinStore } from '../stores/coins';
import { TrashIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline';

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const coinStore = useCoinStore()
const carStore = useCarStore()

const enumToLabel = (v: string) => v.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
const carLabel = (car: Car) => {
  const name = `${enumToLabel(car.brand)} ${enumToLabel(car.model)}`
  return car.licensePlate ? `${name} · ${car.licensePlate}` : name
}

type ImportStep = 'token' | 'mapping' | 'importing' | 'done';

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
    error.value = 'Bitte gib deinen Sprit-Monitor API Token ein!';
    return;
  }

  error.value = '';
  loading.value = true;
  try {
    const vehicles = await spritMonitorService.fetchVehicles(token.value);
    if (vehicles.length === 0) {
      error.value = 'Keine Elektrofahrzeuge gefunden! Hast du welche auf Sprit-Monitor?';
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
    error.value = e.response?.data?.error || 'Token ungültig oder API nicht erreichbar. Check mal deinen Token!';
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

  // Validate mappings
  for (const vehicle of spritMonitorVehicles.value) {
    const mapping = vehicleMapping.value[vehicle.id];
    if (!mapping) {
      error.value = `Bitte ordne alle Fahrzeuge zu! (${vehicle.make} ${vehicle.model} fehlt noch)`;
      return;
    }

    if (mapping === 'new') {
      const newCar = newCarData.value[vehicle.id];
      if (!newCar || !newCar.brand || !newCar.model || !newCar.year) {
        error.value = `Für "${vehicle.make} ${vehicle.model}" fehlen noch Daten (Brand/Model/Jahr)!`;
        return;
      }
    }
  }

  importStep.value = 'importing';
  totalVehicles.value = spritMonitorVehicles.value.length;

  for (let i = 0; i < spritMonitorVehicles.value.length; i++) {
    currentVehicle.value = i + 1;
    const vehicle = spritMonitorVehicles.value[i];
    const mapping = vehicleMapping.value[vehicle.id];

    try {
      let carId: string;

      // Create new car if needed
      if (mapping === 'new') {
        const newCar = newCarData.value[vehicle.id];
        const created = await carService.createCar({
          model: newCar.model,
          year: newCar.year,
          batteryCapacityKwh: 50, // Default placeholder (user can edit later)
          powerKw: null,
          batteryDegradationPercent: null,
          licensePlate: '', // Empty string (user can add later)
          trim: null,
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

const deleteAllImports = async () => {
  deleteError.value = '';
  deleteLoading.value = true;
  try {
    await spritMonitorService.deleteAllImports();
    showDeleteConfirm.value = false;
    // Reload page or emit event to parent
    window.location.reload();
  } catch (e: any) {
    deleteError.value = e.response?.data?.error || 'Löschen fehlgeschlagen. Bitte versuche es erneut.';
  } finally {
    deleteLoading.value = false;
  }
};

const close = () => {
  emit('close');
};
</script>

<template>
  <div
    class="fixed inset-0 flex items-center justify-center z-50 p-4"
    style="backdrop-filter: blur(8px); background-color: rgba(0, 0, 0, 0.3);"
    @click.self="close">
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-y-auto" @click.stop>
      <!-- Header -->
      <div class="sticky top-0 bg-indigo-600 text-white px-6 py-4 rounded-t-xl flex justify-between items-center">
        <h2 class="text-2xl font-bold">Sprit-Monitor Import</h2>
        <button @click="close" class="text-white hover:text-gray-200 text-2xl font-bold">&times;</button>
      </div>

      <div class="p-6">
        <!-- Error Message -->
        <div v-if="error" class="mb-4 p-4 bg-red-100 text-red-800 rounded-lg border border-red-300">
          ⚠️ {{ error }}
        </div>

        <!-- Step 1: Token Input -->
        <div v-if="importStep === 'token'">
          <p class="text-gray-700 dark:text-gray-300 mb-4">
            Gib deinen <strong>Sprit-Monitor API Token</strong> ein um deine Ladevorgänge zu importieren.
            Der Token wird nach dem Import sofort wieder verworfen. Er wird <strong>nicht gespeichert</strong>!
          </p>
          <input
            v-model="token"
            type="text"
            placeholder="z.B. abc123xyz..."
            class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 mb-2" />
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-4">
            Den Token findest du auf Sprit-Monitor unter
            <a href="https://www.spritmonitor.de/de/mein_account/passwort_aendern.html" target="_blank" rel="noopener noreferrer"
              class="text-indigo-600 underline hover:text-indigo-800">Mein Konto → Passwort ändern</a>.
          </p>
          <button
            @click="fetchVehicles"
            :disabled="loading"
            class="btn-3d w-full px-6 py-3 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 transition disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            <svg v-if="loading" class="animate-spin h-5 w-5 text-white flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            {{ loading ? 'Wird geladen…' : 'Fahrzeuge laden' }}
          </button>

          <!-- Danger Zone: Delete Imports -->
          <div class="mt-8 pt-6 border-t border-gray-200 dark:border-gray-700">
            <div class="flex items-center gap-2 mb-2">
              <TrashIcon class="w-5 h-5 text-red-600" />
              <h3 class="text-lg font-semibold text-red-600">Bisherige Importe löschen</h3>
            </div>
            <p class="text-sm text-gray-600 dark:text-gray-400 mb-3">
              Wenn du alle bisherigen Sprit-Monitor Importe löschen möchtest, klicke hier.
              <strong>Manuell eingetragene Ladevorgänge werden NICHT gelöscht.</strong>
            </p>
            <button
              @click="showDeleteConfirm = true"
              class="btn-3d px-4 py-2 bg-red-600 text-white font-semibold rounded-lg hover:bg-red-700 transition">
              Alle Sprit-Monitor Importe löschen
            </button>
          </div>
        </div>

        <!-- Step 2: Vehicle Mapping -->
        <div v-if="importStep === 'mapping'">
          <p class="text-gray-700 dark:text-gray-300 mb-4">
            Wir haben <strong>{{ spritMonitorVehicles.length }} Elektrofahrzeuge</strong> gefunden!
            Ordne sie deinen EV Monitor Autos zu oder lege neue an.
          </p>

          <div class="space-y-4">
            <div v-for="vehicle in spritMonitorVehicles" :key="vehicle.id" class="border border-gray-300 dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-900">
              <h3 class="font-bold text-lg text-gray-800 dark:text-gray-200 mb-2">
                {{ vehicle.make }} {{ vehicle.model }}
              </h3>

              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Zuordnung:</label>
              <select
                v-model="vehicleMapping[vehicle.id]"
                class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg mb-3 focus:ring-2 focus:ring-indigo-500">
                <option value="">-- Bitte wählen --</option>
                <option value="new">➕ Neues Auto anlegen</option>
                <option v-for="car in myCars" :key="car.id" :value="car.id">
                  {{ carLabel(car) }}
                </option>
              </select>

              <!-- New Car Form -->
              <div v-if="vehicleMapping[vehicle.id] === 'new'" class="mt-3 p-3 bg-white dark:bg-gray-800 rounded-lg border border-indigo-200 space-y-2">
                <p class="text-sm text-gray-600 dark:text-gray-400 mb-2">Neues Auto anlegen:</p>

                <!-- Brand Selection -->
                <select
                  :value="newCarData[vehicle.id]?.brand || ''"
                  @change="(e) => onBrandChange(vehicle.id, (e.target as HTMLSelectElement).value)"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg focus:ring-2 focus:ring-indigo-500">
                  <option value="">-- Marke wählen --</option>
                  <option v-for="brand in brands" :key="brand.value" :value="brand.value">
                    {{ brand.label }}
                  </option>
                </select>

                <!-- Model Selection -->
                <select
                  v-model="newCarData[vehicle.id].model"
                  :disabled="!newCarData[vehicle.id]?.brand"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg focus:ring-2 focus:ring-indigo-500 disabled:bg-gray-100 dark:disabled:bg-gray-600">
                  <option value="">{{ newCarData[vehicle.id]?.brand ? '-- Modell wählen --' : 'Erst Marke wählen' }}</option>
                  <option v-for="model in newCarData[vehicle.id]?.availableModels || []" :key="model.value" :value="model.value">
                    {{ model.label }}
                  </option>
                </select>

                <!-- Year Input -->
                <input
                  v-model.number="newCarData[vehicle.id].year"
                  type="number"
                  placeholder="Baujahr (z.B. 2023)"
                  min="2000"
                  max="2030"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 rounded-lg focus:ring-2 focus:ring-indigo-500" />
              </div>
            </div>
          </div>

          <button
            @click="startImport"
            class="w-full mt-6 px-6 py-3 bg-green-600 text-white font-semibold rounded-lg hover:bg-green-700 transition">
            Import starten
          </button>
        </div>

        <!-- Step 3: Importing Progress -->
        <div v-if="importStep === 'importing'" class="text-center">
          <div class="mb-4">
            <svg class="animate-spin h-16 w-16 text-indigo-600 mx-auto" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
          <h3 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-2">Importiere Daten...</h3>
          <p class="text-gray-600 dark:text-gray-400">
            Fahrzeug {{ currentVehicle }} / {{ totalVehicles }}
          </p>
          <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3 mt-4">
            <div
              class="bg-indigo-600 h-3 rounded-full transition-all duration-300"
              :style="{ width: `${(currentVehicle / totalVehicles) * 100}%` }">
            </div>
          </div>
        </div>

        <!-- Step 4: Done -->
        <div v-if="importStep === 'done'" class="text-center">
          <div class="text-6xl mb-4">✅</div>
          <h3 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-4">Import abgeschlossen!</h3>
          <div class="text-left bg-gray-50 dark:bg-gray-900 rounded-lg p-4 mb-4">
            <p class="text-lg mb-2">
              <strong class="text-green-600">{{ totalImported }}</strong> Ladevorgänge importiert
            </p>
            <p v-if="totalSkipped > 0" class="text-lg mb-2">
              <strong class="text-yellow-600">{{ totalSkipped }}</strong> bereits vorhanden, übersprungen
            </p>
            <p v-if="totalWithoutLocation > 0" class="text-sm mb-2 text-gray-600 dark:text-gray-400">
              <strong class="text-gray-700 dark:text-gray-300">{{ totalWithoutLocation }}</strong> Einträge haben einen Ortsnamen (z.B. "SuC Asten"), aber keine GPS-Koordinaten von Sprit-Monitor - diese erscheinen nicht auf der Heatmap.
            </p>
            <p v-if="totalCoinsAwarded > 0" class="text-lg mb-2">
              <strong class="text-indigo-600">+{{ totalCoinsAwarded }} Watt</strong> erhalten!
            </p>
            <div v-if="totalErrors.length > 0" class="mt-3">
              <p class="text-red-600 font-semibold mb-2">Fehler:</p>
              <ul class="list-disc list-inside text-sm text-gray-700 dark:text-gray-300">
                <li v-for="(err, idx) in totalErrors" :key="idx">{{ err }}</li>
              </ul>
            </div>
          </div>
          <button
            @click="close"
            class="w-full px-6 py-3 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 transition">
            Fertig
          </button>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div
      v-if="showDeleteConfirm"
      class="fixed inset-0 flex items-center justify-center z-[60] p-4"
      style="backdrop-filter: blur(12px); background-color: rgba(0, 0, 0, 0.5);"
      @click.self="showDeleteConfirm = false">
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-md w-full p-6" @click.stop>
        <div class="flex items-center gap-2 mb-4">
          <ExclamationTriangleIcon class="w-8 h-8 text-red-600" />
          <h3 class="text-2xl font-bold text-red-600">Alle Importe löschen?</h3>
        </div>
        <p class="text-gray-700 dark:text-gray-300 mb-4">
          Du bist dabei, <strong>ALLE bisherigen Sprit-Monitor Importe</strong> zu löschen.
          Dieser Vorgang kann <strong>nicht rückgängig gemacht werden</strong>.
        </p>

        <!-- Error Message -->
        <div v-if="deleteError" class="mb-4 p-3 bg-red-100 text-red-800 rounded-lg border border-red-300 text-sm">
          <ExclamationTriangleIcon class="w-4 h-4 inline-block mr-1" />
          {{ deleteError }}
        </div>

        <p class="text-sm text-gray-700 dark:text-gray-300 mb-4">
          Ich habe verstanden, dass all meine bisher importierten Sprit-Monitor Ladevorgänge gelöscht werden.
        </p>

        <div class="flex gap-3">
          <button
            @click="showDeleteConfirm = false"
            :disabled="deleteLoading"
            class="flex-1 px-4 py-3 bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 font-semibold rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition disabled:opacity-50">
            Abbrechen
          </button>
          <button
            @click="deleteAllImports"
            :disabled="deleteLoading"
            class="flex-1 px-4 py-3 bg-red-600 text-white font-semibold rounded-lg hover:bg-red-700 transition disabled:opacity-50 flex items-center justify-center gap-2">
            <svg v-if="deleteLoading" class="animate-spin h-5 w-5 text-white flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            {{ deleteLoading ? 'Wird gelöscht…' : 'Ja, alle löschen' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
