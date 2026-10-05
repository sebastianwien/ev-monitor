<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { MicrophoneIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import { useVoiceRecorder, pickMimeType } from '../../composables/useVoiceRecorder'
import { analytics } from '../../services/analytics'
import type { VoiceDraft } from './wizardLogic'
import { voiceProblem, type VoiceProblem } from './voiceProblem'
import { durationBucket, latencyBucket, filledCount, placeOutcome, type VoiceEntry } from './voiceAnalytics'
import VoiceSheet from './VoiceSheet.vue'

const props = withDefaults(defineProps<{
  carId: string; latitude?: number | null; longitude?: number | null
  /** Anlegen oder Bearbeiten - nur für die Messung und den Hinweis im Vollbild */
  entry?: VoiceEntry
  /** card: großer Einstieg; inline: schmale Zeile zum Ergänzen oder Korrigieren nach einer ersten Aufnahme */
  variant?: 'card' | 'inline'
  /** inline: Text statt "Ergänzen oder korrigieren", z. B. was noch fehlt */
  label?: string | null
}>(), { latitude: null, longitude: null, entry: 'create', variant: 'card', label: null })
const emit = defineEmits<{ draft: [draft: VoiceDraft] }>()
const { t, locale } = useI18n()

/** Der Transparenz-Satz kommt einmal, beim ersten Antippen - danach direkt aufnehmen. v2: Satz zur Adresssuche ergänzt. */
const CONSENT_KEY = 'voicelog_consent_seen_v2'
const consentSeen = () => { try { return localStorage.getItem(CONSENT_KEY) === 'true' } catch { return false } }
const showConsent = ref(false)

const problem = ref<VoiceProblem | null>(null)
const extension = (type: string) => type.startsWith('audio/mp4') ? 'm4a' : type.startsWith('audio/ogg') ? 'ogg' : 'webm'
const again = computed(() => props.variant === 'inline')

let uploadStartedAt = 0
const upload = async (audio: Blob) => {
  const body = new FormData()
  body.append('audio', audio, `voice.${extension(audio.type || pickMimeType() || '')}`)
  body.append('carId', props.carId)
  body.append('timeZone', Intl.DateTimeFormat().resolvedOptions().timeZone)
  // Position nur, wenn die Umkreissuche schon eine hat - beide oder keine
  if (props.latitude != null && props.longitude != null) {
    body.append('lat', String(props.latitude))
    body.append('lon', String(props.longitude))
  }
  try {
    // Ohne expliziten Typ macht axios aus FormData JSON (Standard-Header der Instanz)
    const res = await api.post<VoiceDraft>('/logs/voice-draft', body, { headers: { 'Content-Type': 'multipart/form-data' } })
    analytics.trackVoice('draft', {
      entry: props.entry, filled: filledCount(res.data.fields), uncertain: res.data.fields.uncertain.length,
      place: placeOutcome(res.data), latency: latencyBucket(Date.now() - uploadStartedAt),
    })
    emit('draft', res.data)
  } catch (e) {
    problem.value = voiceProblem(e)
    analytics.trackVoice('error', { entry: props.entry, kind: problem.value.kind })
    throw e
  }
}
const rec = useVoiceRecorder(upload)

// Ende der Aufnahme (Fertig, Loslassen oder 60 s): Länge messen, dann läuft der Upload
watch(rec.state, (s, prev) => {
  if (prev === 'recording' && s === 'uploading') {
    uploadStartedAt = Date.now()
    analytics.trackVoice('stop', { entry: props.entry, dur: durationBucket(rec.elapsedMs.value) })
  }
  if (s === 'denied') analytics.trackVoice('error', { entry: props.entry, kind: 'denied' })
  if (s === 'error' && !problem.value) analytics.trackVoice('error', { entry: props.entry, kind: 'mic' })
})

const begin = () => {
  analytics.trackVoice('open', { entry: props.entry, again: again.value })
  if (again.value) analytics.trackVoice('retry', { entry: props.entry })
  rec.press()
}
const onPress = (e?: PointerEvent) => {
  // Finger bleibt beim Halten auf dem Knopf, auch wenn das Vollbild darüber aufgeht
  if (e) (e.currentTarget as Element | null)?.setPointerCapture?.(e.pointerId)
  problem.value = null
  if (rec.state.value === 'recording') { rec.press(); return }
  if (rec.state.value !== 'idle' && rec.state.value !== 'done' && rec.state.value !== 'error') return
  if (!consentSeen()) { showConsent.value = true; return }
  begin()
}
const acceptConsent = () => {
  try { localStorage.setItem(CONSENT_KEY, 'true') } catch { /* privater Modus: dann eben jedes Mal */ }
  showConsent.value = false
  analytics.trackVoice('consent', { entry: props.entry })
  begin()
}
const cancel = () => {
  analytics.trackVoice('cancel', { entry: props.entry, dur: durationBucket(rec.elapsedMs.value) })
  rec.cancel()
}

const sheetOpen = computed(() => rec.state.value === 'recording' || rec.state.value === 'uploading')
const busy = computed(() => rec.state.value === 'requesting' || rec.state.value === 'uploading')
const resetsOn = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'long' })
const status = computed(() => {
  const p = problem.value
  if (p?.kind === 'limit') return t('voicelog.error_limit', { date: resetsOn(p.resetsOn) })
  if (p) return t(`voicelog.error_${p.kind}`)
  if (rec.state.value === 'error') return t('voicelog.error_mic')
  return null
})
</script>

<template>
  <div :class="variant === 'card' ? 'p-3 rounded-sm bg-indigo-50 dark:bg-indigo-900/30' : ''" data-testid="voice-capture" :data-variant="variant">
    <p v-if="rec.state.value === 'denied'" data-testid="voice-denied" class="text-sm text-gray-700 dark:text-gray-200">{{ t('voicelog.denied') }}</p>
    <div v-else class="flex items-center gap-3">
      <!-- Tippen startet, "Fertig" im Vollbild beendet; Halten nimmt auf, bis der Finger loslässt. Kein click-Handler, der würde doppelt auslösen. -->
      <button type="button" data-testid="voice-mic" :disabled="busy"
        :aria-label="variant === 'inline' ? (label ?? t('voicelog.again')) : t('voicelog.mic_label')"
        :class="['relative flex-shrink-0 grid place-items-center rounded-full text-white bg-indigo-600 hover:bg-indigo-700 transition select-none touch-none [-webkit-touch-callout:none] disabled:opacity-70',
                 variant === 'card' ? 'h-16 w-16' : 'h-11 w-11']"
        @pointerdown.prevent="onPress" @pointerup="rec.release()" @pointercancel="rec.release()" @contextmenu.prevent
        @keydown.enter.prevent="onPress()" @keydown.space.prevent="onPress()">
        <ArrowPathIcon v-if="busy" :class="variant === 'card' ? 'h-7 w-7 animate-spin' : 'h-5 w-5 animate-spin'" />
        <MicrophoneIcon v-else :class="variant === 'card' ? 'h-7 w-7' : 'h-5 w-5'" />
      </button>
      <div class="flex-1 min-w-0" aria-live="polite">
        <p v-if="status" data-testid="voice-problem" class="text-sm text-amber-700 dark:text-amber-300">{{ status }}</p>
        <template v-else-if="variant === 'card'">
          <!-- Ein kurzes Beispiel statt Bedienanleitung: zeigt, dass ein lockerer Halbsatz reicht. Die volle Liste steht im Vollbild. -->
          <p class="text-base font-semibold text-gray-800 dark:text-gray-100">{{ t('voicelog.title') }}</p>
          <p class="text-sm text-gray-600 dark:text-gray-300 mt-0.5">{{ t('voicelog.card_example') }}</p>
        </template>
        <template v-else>
          <p class="text-sm font-semibold text-indigo-700 dark:text-indigo-300">{{ label ?? t('voicelog.again') }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('voicelog.again_hint') }}</p>
        </template>
      </div>
    </div>
    <div v-if="showConsent" data-testid="voice-consent" class="mt-3 space-y-2 text-sm text-gray-700 dark:text-gray-200">
      <p>{{ t('voicelog.consent_text') }}</p>
      <button type="button" data-testid="voice-consent-ok" @click="acceptConsent"
        class="btn-3d min-h-11 px-4 rounded-sm bg-indigo-600 text-white font-semibold hover:bg-indigo-700">{{ t('voicelog.consent_ok') }}</button>
    </div>
    <VoiceSheet v-if="sheetOpen" :processing="rec.state.value === 'uploading'" :elapsed-ms="rec.elapsedMs.value" :level="rec.level.value"
      :entry="entry" @stop="rec.press()" @cancel="cancel" />
  </div>
</template>
