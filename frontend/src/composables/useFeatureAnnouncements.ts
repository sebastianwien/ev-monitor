import { ref, computed } from 'vue'
import { featureAnnouncements, type FeatureAnnouncement } from '../config/featureAnnouncements'
import { useWallboxStore } from '../stores/wallbox'
import { useAuthStore } from '../stores/auth'
import teslaFleetService from '../api/teslaFleetService'

const STORAGE_KEY = 'seen-announcements'

const seenKeys = ref<string[]>(getSeenKeys())
const hasTeslaConnection = ref<boolean>(false)
// true only once the connector has actually observed the vehicle_location OAuth scope on a
// token - false (not just "not yet loaded"/null) is what gates the reconnect announcement.
const teslaLocationScopeGranted = ref<boolean>(false)
let teslaStatusLoaded = false

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

const today = new Date().toISOString().split('T')[0]

const currentIndex = ref(0)

export const useFeatureAnnouncements = () => {
  const wallboxStore = useWallboxStore()
  const authStore = useAuthStore()

  // Lazy-load once on first composable use - ensures Tesla-only announcements
  // are gated by an actual connection rather than shown to every user.
  if (authStore.isAuthenticated()) {
    void loadTeslaConnectionStatus()
  }

  const pending = computed<FeatureAnnouncement[]>(() => {
    const ctx = {
      hasGoeConnection: wallboxStore.hasConnections,
      isPremium: authStore.isPremium,
      isAutoSyncLive: authStore.isAutoSyncLive,
      hasTeslaConnection: hasTeslaConnection.value,
      teslaLocationScopeGranted: teslaLocationScopeGranted.value,
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
  const total = computed(() => pending.value.length)
  const currentNumber = computed(() => total.value > 0 ? currentIndex.value + 1 : 0)

  const dismiss = () => {
    if (!announcement.value) return
    markSeen(announcement.value.key)
    currentIndex.value = 0
  }

  return { announcement, dismiss, total, currentNumber }
}
