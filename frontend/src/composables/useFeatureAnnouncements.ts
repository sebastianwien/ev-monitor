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

const pendingAnnouncement = ref<FeatureAnnouncement | null>(
  featureAnnouncements.find(a => {
    if (a.expiresAt < today) return false
    if (getSeenKeys().includes(a.key)) return false
    return true
  }) ?? null
)

export const useFeatureAnnouncements = () => {
  const dismiss = () => {
    if (pendingAnnouncement.value) {
      markSeen(pendingAnnouncement.value.key)
      pendingAnnouncement.value = null
    }
  }

  return {
    announcement: computed(() => pendingAnnouncement.value),
    dismiss,
  }
}
