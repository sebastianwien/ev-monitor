import { ref, computed } from 'vue'
import { featureAnnouncements, type FeatureAnnouncement } from '../config/featureAnnouncements'
import { useWallboxStore } from '../stores/wallbox'

const STORAGE_KEY = 'seen-announcements'

const seenKeys = ref<string[]>(getSeenKeys())

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

const today = new Date().toISOString().split('T')[0]

const currentIndex = ref(0)

export const useFeatureAnnouncements = () => {
  const wallboxStore = useWallboxStore()

  const pending = computed<FeatureAnnouncement[]>(() => {
    const ctx = { hasGoeConnection: wallboxStore.hasConnections }
    return featureAnnouncements.filter(a =>
      a.expiresAt >= today &&
      !seenKeys.value.includes(a.key) &&
      (!a.condition || a.condition(ctx))
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
