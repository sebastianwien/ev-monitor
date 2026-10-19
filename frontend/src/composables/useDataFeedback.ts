import { reactive, ref } from 'vue'
import api from '../api/axios'

/**
 * "Korrekt erkannt?" für automatisch erfasste Ladevorgänge und Fahrten. Ein gemeinsamer Zustand
 * für alle Karten: die Bewertungen des Nutzers werden einmal geladen, Klicks optimistisch gezeigt
 * und verzögert gespeichert, damit schnelles Umklicken nur einen Aufruf erzeugt.
 */
export type FeedbackSubjectType = 'LOG' | 'TRIP'
export type FeedbackRating = 'POSITIVE' | 'NEGATIVE'

export const FEEDBACK_TAGS: Record<FeedbackSubjectType, readonly string[]> = {
  LOG: ['KWH_WRONG', 'TIME_WRONG', 'SOC_WRONG', 'DUPLICATE', 'OTHER'],
  TRIP: ['DISTANCE_WRONG', 'TIME_WRONG', 'DUPLICATE', 'OTHER'],
}

/** Automatisch erfasste Ladequellen (Kanal SYNC oder LIVE im Backend, DataSource.channel()). */
const RATEABLE_LOG_SOURCES = new Set([
  'TESLA_IMPORT', 'TESLA_FLEET_IMPORT', 'TESLA_LIVE', 'WALLBOX_OCPP', 'WALLBOX_GOE', 'SMARTCAR_LIVE',
  'VWGROUP_LIVE', 'XPENG_LIVE', 'EU_DATA_ACT_SYNC', 'POLESTAR_DATA_PORTAL',
])

export const isRateableLogSource = (dataSource: string | null | undefined) =>
  !!dataSource && RATEABLE_LOG_SOURCES.has(dataSource)

interface FeedbackEntry {
  subjectType: FeedbackSubjectType
  subjectId: string
  rating: FeedbackRating
  tags: string[]
  comment: string | null
}

const SAVE_DELAY_MS = 600

const ratings = reactive(new Map<string, FeedbackRating>())
const openPanel = ref<string | null>(null)
const draftTags = ref<string[]>([])
const draftComment = ref('')
const timers = new Map<string, ReturnType<typeof setTimeout>>()
let loading: Promise<void> | null = null

const keyOf = (type: FeedbackSubjectType, id: string) => `${type}:${id}`

function cancelPending(key: string) {
  const timer = timers.get(key)
  if (timer) clearTimeout(timer)
  timers.delete(key)
}

export function resetDataFeedback() {
  ratings.clear()
  openPanel.value = null
  draftTags.value = []
  draftComment.value = ''
  timers.forEach(clearTimeout)
  timers.clear()
  loading = null
}

export function useDataFeedback() {
  const load = () => {
    loading ??= api.get('/feedback')
      .then(res => {
        for (const f of res.data as FeedbackEntry[]) ratings.set(keyOf(f.subjectType, f.subjectId), f.rating)
      })
      .catch(() => { loading = null })
    return loading
  }

  const ratingOf = (type: FeedbackSubjectType, id: string): FeedbackRating | null =>
    ratings.get(keyOf(type, id)) ?? null

  const isPanelOpen = (type: FeedbackSubjectType, id: string) => openPanel.value === keyOf(type, id)

  const toggle = (type: FeedbackSubjectType, id: string, rating: FeedbackRating) => {
    const key = keyOf(type, id)
    const next = ratingOf(type, id) === rating ? null : rating
    if (next) ratings.set(key, next)
    else ratings.delete(key)

    if (next === 'NEGATIVE') {
      openPanel.value = key
      draftTags.value = []
      draftComment.value = ''
    } else if (openPanel.value === key) {
      openPanel.value = null
    }

    cancelPending(key)
    timers.set(key, setTimeout(() => {
      timers.delete(key)
      if (next) api.put(`/feedback/${type}/${id}`, { rating: next, tags: [], comment: null })
      else api.delete(`/feedback/${type}/${id}`)
    }, SAVE_DELAY_MS))
  }

  const toggleTag = (tag: string) => {
    const idx = draftTags.value.indexOf(tag)
    if (idx === -1) draftTags.value.push(tag)
    else draftTags.value.splice(idx, 1)
  }

  const sendNegative = async (type: FeedbackSubjectType, id: string) => {
    const key = keyOf(type, id)
    cancelPending(key)
    ratings.set(key, 'NEGATIVE')
    const comment = draftComment.value.trim()
    await api.put(`/feedback/${type}/${id}`, { rating: 'NEGATIVE', tags: [...draftTags.value], comment: comment || null })
    openPanel.value = null
    draftTags.value = []
    draftComment.value = ''
  }

  return { load, ratingOf, isPanelOpen, toggle, toggleTag, sendNegative, draftTags, draftComment }
}
