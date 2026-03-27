import { ref, computed } from 'vue'
import { featureAnnouncements, type FeatureAnnouncement } from '../config/featureAnnouncements'

const STORAGE_KEY = 'seen-announcements'

const getSeenKeys = (): string[] => {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

const markSeen = (key: string) => {
  const seen = getSeenKeys()
  if (!seen.includes(key)) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...seen, key]))
  }
}

const today = new Date().toISOString().split('T')[0]

const pending = ref<FeatureAnnouncement[]>(
  featureAnnouncements.filter(a => a.expiresAt >= today && !getSeenKeys().includes(a.key))
)

const currentIndex = ref(0)

export const useFeatureAnnouncements = () => {
  const announcement = computed(() => pending.value[currentIndex.value] ?? null)
  const total = computed(() => pending.value.length)
  const currentNumber = computed(() => total.value > 0 ? currentIndex.value + 1 : 0)

  const dismiss = () => {
    if (!announcement.value) return
    markSeen(announcement.value.key)
    if (currentIndex.value < pending.value.length - 1) {
      currentIndex.value++
    } else {
      pending.value = []
      currentIndex.value = 0
    }
  }

  return { announcement, dismiss, total, currentNumber }
}
