<template>
  <div class="tesla-integration">
    <div class="card">
      <h3 class="text-lg font-semibold mb-4">🚗 Tesla Integration</h3>

      <!-- Not Connected State -->
      <div v-if="!status.connected && !showTokenInput" class="space-y-4">
        <p class="text-gray-600">
          Verbinde deinen Tesla Account um Ladevorgänge automatisch zu importieren.
        </p>
        <button
          @click="showTokenInput = true"
          class="btn-primary"
        >
          🔗 Tesla verbinden
        </button>
      </div>

      <!-- Token Input Form -->
      <div v-if="!status.connected && showTokenInput" class="space-y-4">
        <div class="bg-yellow-50 border border-yellow-200 rounded p-4 text-sm">
          <p class="font-semibold mb-2">⚠️ So funktioniert's:</p>
          <ol class="list-decimal list-inside space-y-1 text-gray-700">
            <li>Führe das <code class="bg-gray-100 px-1 rounded">tesla_explorer.py</code> Script im Projektordner aus</li>
            <li>Das Script öffnet automatisch einen Browser für den Tesla Login</li>
            <li>Nach erfolgreicher Authentifizierung zeigt das Script beide Werte an:</li>
            <li class="ml-6">→ <strong>Access Token</strong> (beginnt mit "eyJ...")</li>
            <li class="ml-6">→ <strong>Tesla ID</strong> (lange Zahl wie 1492931379485066, steht unter "id")</li>
            <li>Kopiere beide Werte aus der Console oder aus <code class="bg-gray-100 px-1 rounded">~/tesla_token_*.txt</code></li>
            <li>Füge sie hier ein</li>
          </ol>
          <p class="mt-2 text-xs text-gray-600">
            💡 Tipp: Der Fahrzeugname wird automatisch von Tesla abgerufen.
          </p>
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Access Token *</label>
          <input
            v-model="tokenInput.accessToken"
            type="password"
            placeholder="eyJhbGciOiJSUzI1NiIs..."
            class="input w-full font-mono text-xs"
          />
          <p class="text-xs text-gray-500 mt-1">Langer Token beginnend mit "eyJ..."</p>
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Tesla ID *</label>
          <input
            v-model="tokenInput.vehicleId"
            type="text"
            placeholder="1492931379485066"
            class="input w-full"
          />
          <p class="text-xs text-gray-500 mt-1">Tesla API ID (lange Zahl, z.B. 1492931379485066) - findest du im tesla_explorer.py Output unter "id"</p>
        </div>

        <div class="flex gap-2">
          <button
            @click="handleConnect"
            :disabled="!tokenInput.accessToken || !tokenInput.vehicleId || isLoading"
            class="btn-primary"
          >
            {{ isLoading ? '⏳ Verbinde...' : '✅ Verbinden' }}
          </button>
          <button
            @click="showTokenInput = false"
            class="btn-secondary"
          >
            Abbrechen
          </button>
        </div>

        <div v-if="error" class="alert-error">
          {{ error }}
        </div>
      </div>

      <!-- Connected State -->
      <div v-if="status.connected" class="space-y-4">
        <div class="bg-green-50 border border-green-200 rounded p-4">
          <div class="flex items-center justify-between">
            <div>
              <p class="font-semibold text-green-800">
                ✅ Verbunden: {{ status.vehicleName || 'Tesla' }}
              </p>
              <p v-if="status.lastSyncAt" class="text-sm text-gray-600 mt-1">
                Letzter Sync: {{ formatDate(status.lastSyncAt) }}
              </p>
            </div>
            <button
              @click="handleDisconnect"
              class="text-sm text-red-600 hover:text-red-800"
            >
              Trennen
            </button>
          </div>
        </div>

        <div class="flex gap-2">
          <button
            @click="handleSync"
            :disabled="isLoading"
            class="btn-primary flex-1"
          >
            {{ isLoading ? '⏳ Synchronisiere...' : '⚡ Jetzt synchronisieren' }}
          </button>
        </div>

        <div v-if="syncResult" class="alert-success">
          <p class="font-semibold">
            🎉 Sync erfolgreich!
          </p>
          <p class="text-sm mt-1">
            {{ syncResult.logsImported }} Ladevorgang(e) importiert
            • Batteriestand: {{ syncResult.batteryLevel }}%
          </p>
        </div>

        <div v-if="error" class="alert-error">
          {{ error }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import teslaService, { type TeslaConnectionStatus, type TeslaSyncResult } from '@/api/teslaService';

const status = ref<TeslaConnectionStatus>({
  connected: false,
  vehicleName: null,
  lastSyncAt: null,
  autoImportEnabled: false,
});

const showTokenInput = ref(false);
const tokenInput = ref({
  accessToken: '',
  vehicleId: '',
});

const isLoading = ref(false);
const error = ref<string | null>(null);
const syncResult = ref<TeslaSyncResult | null>(null);

onMounted(async () => {
  await loadStatus();
});

async function loadStatus() {
  try {
    status.value = await teslaService.getStatus();
  } catch (err) {
    console.error('Failed to load Tesla status:', err);
  }
}

async function handleConnect() {
  if (!tokenInput.value.accessToken || !tokenInput.value.vehicleId) {
    return;
  }

  isLoading.value = true;
  error.value = null;

  try {
    const response = await teslaService.connect({
      accessToken: tokenInput.value.accessToken,
      vehicleId: tokenInput.value.vehicleId,
      vehicleName: 'Tesla', // Default name, will be updated from API
    });

    if (response.success) {
      showTokenInput.value = false;
      tokenInput.value = { accessToken: '', vehicleId: '' };
      await loadStatus();
    } else {
      error.value = response.message;
    }
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Verbindung fehlgeschlagen';
  } finally {
    isLoading.value = false;
  }
}

async function handleSync() {
  isLoading.value = true;
  error.value = null;
  syncResult.value = null;

  try {
    syncResult.value = await teslaService.sync();
    await loadStatus();
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Synchronisierung fehlgeschlagen';
  } finally {
    isLoading.value = false;
  }
}

async function handleDisconnect() {
  if (!confirm('Tesla Account wirklich trennen?')) {
    return;
  }

  try {
    await teslaService.disconnect();
    await loadStatus();
    showTokenInput.value = false;
    tokenInput.value = { accessToken: '', vehicleId: '' };
  } catch (err: any) {
    error.value = 'Trennen fehlgeschlagen';
  }
}

function formatDate(dateString: string): string {
  const date = new Date(dateString);
  return date.toLocaleString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
</script>

<style scoped>
.tesla-integration {
  margin-top: 2rem;
}

.card {
  background: white;
  border-radius: 0.5rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.btn-primary {
  background: #3b82f6;
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 0.375rem;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #e5e7eb;
  color: #374151;
  padding: 0.5rem 1rem;
  border-radius: 0.375rem;
  font-weight: 500;
  transition: background 0.2s;
}

.btn-secondary:hover {
  background: #d1d5db;
}

.input {
  border: 1px solid #d1d5db;
  padding: 0.5rem;
  border-radius: 0.375rem;
  font-size: 0.875rem;
}

.input:focus {
  outline: none;
  border-color: #3b82f6;
  ring: 2px;
  ring-color: #3b82f620;
}

.alert-success {
  background: #d1fae5;
  border: 1px solid #10b981;
  color: #065f46;
  padding: 0.75rem;
  border-radius: 0.375rem;
}

.alert-error {
  background: #fee2e2;
  border: 1px solid #ef4444;
  color: #991b1b;
  padding: 0.75rem;
  border-radius: 0.375rem;
}
</style>
