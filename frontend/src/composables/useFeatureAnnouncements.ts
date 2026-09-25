import { ref, computed } from 'vue'
import { featureAnnouncements, type FeatureAnnouncement } from '../config/featureAnnouncements'
import { useWallboxStore } from '../stores/wallbox'
import { useAuthStore } from '../stores/auth'
import { useCarStore } from '../stores/car'
import teslaFleetService from '../api/teslaFleetService'
import vwEudaSyncService, { eudaBrandOf, type VwEudaBrand } from '../api/vwEudaSyncService'

const STORAGE_KEY = 'seen-announcements'

const seenKeys = ref<string[]>(getSeenKeys())
const hasTeslaConnection = ref<boolean>(false)
// true only once the connector has actually observed the vehicle_location OAuth scope on a
// token - false (not just "not yet loaded"/null) is what gates the reconnect announcement.
const teslaLocationScopeGranted = ref<boolean>(false)
let teslaStatusLoaded = false
// Halter eines VW, Skoda, Audi, Seat oder Cupra - und ob dieser Halter das Portal schon
// verbunden hat. Beides false, solange nichts geladen ist: die Ankuendigung soll erst
// erscheinen, wenn wir es wirklich wissen, nicht schon beim ersten Render.
const hasVwEudaBrandCar = ref<boolean>(false)
// Anzeigename der Marke fuer den Titel der Ankuendigung - "Dein Skoda" trifft den Leser, "Dein
// Auto" nicht. Volkswagen kuerzen wir auf VW ab, so nennen die Halter ihr Auto selbst.
const EUDA_BRAND_LABEL: Record<VwEudaBrand, string> = {
  volkswagen: 'VW', skoda: 'Skoda', audi: 'Audi', seat: 'Seat', cupra: 'Cupra',
}
const eudaBrandLabel = ref<string | null>(null)
const hasVwEudaConnection = ref<boolean>(false)
let eudaStatusLoaded = false

function getSeenKeys(): string[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

const markSeen = (key: string) => {
  if (!seenKeys.value.includes(key)) {
    seenKeys.value = [...seenKeys.value, key]
    localStorage.setItem(STORAGE_KEY, JSON.stringify(seenKeys.value))
  }
}

async function loadTeslaConnectionStatus() {
  if (teslaStatusLoaded) return
  teslaStatusLoaded = true
  try {
    const status = await teslaFleetService.getStatus()
    hasTeslaConnection.value = status.connected === true
    teslaLocationScopeGranted.value = status.locationScopeGranted === true
  } catch {
    hasTeslaConnection.value = false
    teslaLocationScopeGranted.value = false
  }
}

/**
 * Der Portal-Status wird nur fuer Halter einer VW-Group-Marke abgefragt. Alle anderen sehen die
 * Ankuendigung ohnehin nie, fuer die waere der Request reine Last.
 */
async function loadVwEudaStatus(carStore: ReturnType<typeof useCarStore>) {
  if (eudaStatusLoaded) return
  eudaStatusLoaded = true
  try {
    const cars = await carStore.getCars()
    const brand = cars.map(car => eudaBrandOf(car.brand ?? '')).find(b => b !== null) ?? null
    if (!brand) return
    // Erst schreiben, wenn auch der Verbindungsstatus vorliegt: waere hasVwEudaBrandCar schon
    // vor dem Request true, wuerde die Ankuendigung einem laengst verbundenen Nutzer kurz
    // aufblitzen - und ein Klick darauf verbraucht sie dauerhaft.
    const connections = await vwEudaSyncService.getStatus()
    hasVwEudaConnection.value = connections.length > 0
    eudaBrandLabel.value = EUDA_BRAND_LABEL[brand]
    hasVwEudaBrandCar.value = true
  } catch {
    hasVwEudaBrandCar.value = false
    hasVwEudaConnection.value = false
    eudaBrandLabel.value = null
  }
}

const today = new Date().toISOString().split('T')[0]

const currentIndex = ref(0)

export const useFeatureAnnouncements = () => {
  const wallboxStore = useWallboxStore()
  const authStore = useAuthStore()
  const carStore = useCarStore()

  // Lazy-load once on first composable use - ensures Tesla-only announcements
  // are gated by an actual connection rather than shown to every user.
  if (authStore.isAuthenticated()) {
    void loadTeslaConnectionStatus()
    void loadVwEudaStatus(carStore)
  }

  const pending = computed<FeatureAnnouncement[]>(() => {
    const ctx = {
      hasGoeConnection: wallboxStore.hasConnections,
      isPremium: authStore.isPremium,
      isAutoSyncLive: authStore.isAutoSyncLive,
      hasTeslaConnection: hasTeslaConnection.value,
      teslaLocationScopeGranted: teslaLocationScopeGranted.value,
      hasVwEudaBrandCar: hasVwEudaBrandCar.value,
      hasVwEudaConnection: hasVwEudaConnection.value,
    }
    const registeredAt = authStore.user?.registeredAt
    return featureAnnouncements.filter(a =>
      a.expiresAt >= today &&
      !seenKeys.value.includes(a.key) &&
      (!a.condition || a.condition(ctx)) &&
      (!a.releasedAt || !registeredAt || registeredAt < a.releasedAt)
    )
  })

  const announcement = computed(() => pending.value[currentIndex.value] ?? null)
  /** Platzhalter fuer die i18n-Texte der aktuellen Ankuendigung, heute nur die Fahrzeugmarke. */
  const announcementParams = computed<Record<string, string>>(() =>
    eudaBrandLabel.value ? { brand: eudaBrandLabel.value } : ({} as Record<string, string>))
  const total = computed(() => pending.value.length)
  const currentNumber = computed(() => total.value > 0 ? currentIndex.value + 1 : 0)

  const dismiss = () => {
    if (!announcement.value) return
    markSeen(announcement.value.key)
    currentIndex.value = 0
  }

  return { announcement, announcementParams, dismiss, total, currentNumber }
}
