<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { MicrophoneIcon, StopIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import { useVoiceRecorder, pickMimeType } from '../../composables/useVoiceRecorder'
import type { VoiceDraft } from './wizardLogic'
import { voiceProblem, type VoiceProblem } from './voiceProblem'

const props = defineProps<{ carId: string; latitude: number | null; longitude: number | null }>()
const emit = defineEmits<{ draft: [draft: VoiceDraft] }>()
const { t, locale } = useI18n()

/** Der Transparenz-Satz kommt einmal, beim ersten Antippen - danach direkt aufnehmen. */
const CONSENT_KEY = 'voicelog_consent_seen'
const consentSeen = () => { try { return localStorage.getItem(CONSENT_KEY) === 'true' } catch { return false } }
const showConsent = ref(false)

const problem = ref<VoiceProblem | null>(null)
const extension = (type: string) => type.startsWith('audio/mp4') ? 'm4a' : type.startsWith('audio/ogg') ? 'ogg' : 'webm'

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
    emit('draft', res.data)
  } catch (e) {
    problem.value = voiceProblem(e)
    throw e
  }
}
const rec = useVoiceRecorder(upload)

const onPress = () => {
  problem.value = null
  if (rec.state.value === 'idle' && !consentSeen()) { showConsent.value = true; return }
  rec.press()
}
const acceptConsent = () => {
  try { localStorage.setItem(CONSENT_KEY, 'true') } catch { /* privater Modus: dann eben jedes Mal */ }
  showConsent.value = false
  rec.press()
}

const recording = computed(() => rec.state.value === 'recording')
const busy = computed(() => rec.state.value === 'requesting' || rec.state.value === 'uploading')
const time = computed(() => {
  const s = Math.floor(rec.elapsedMs.value / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
})
const resetsOn = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'long' })
const status = computed(() => {
  if (recording.value) return t('voicelog.recording', { time: time.value })
  if (rec.state.value === 'uploading') return t('voicelog.uploading')
  const p = problem.value
  if (p?.kind === 'limit') return t('voicelog.error_limit', { date: resetsOn(p.resetsOn) })
  if (p) return t(`voicelog.error_${p.kind}`)
  if (rec.state.value === 'error') return t('voicelog.error_mic')
  return null
})
const isProblem = computed(() => !!problem.value || rec.state.value === 'error')
</script>

<template>
  <div class="p-3 rounded-sm bg-indigo-50 dark:bg-indigo-900/30" data-testid="voice-capture">
    <p v-if="rec.state.value === 'denied'" data-testid="voice-denied" class="text-sm text-gray-700 dark:text-gray-200">{{ t('voicelog.denied') }}</p>
    <div v-else class="flex items-center gap-3">
      <!-- Tippen startet und beendet; Halten nimmt auf, bis der Finger loslässt. Kein click-Handler, der würde doppelt auslösen. -->
      <button type="button" data-testid="voice-mic" :disabled="busy"
        :aria-label="recording ? t('voicelog.mic_stop') : t('voicelog.mic_label')" :aria-pressed="recording"
        class="relative flex-shrink-0 grid place-items-center h-16 w-16 rounded-full text-white transition select-none touch-none [-webkit-touch-callout:none] disabled:opacity-70"
        :class="recording ? 'bg-red-600' : 'bg-indigo-600 hover:bg-indigo-700'"
        @pointerdown.prevent="onPress" @pointerup="rec.release()" @pointercancel="rec.release()" @contextmenu.prevent
        @keydown.enter.prevent="onPress" @keydown.space.prevent="onPress">
        <!-- Pegel-Ring: wächst mit der Lautstärke, zeigt "ich höre dich" -->
        <span v-if="recording" aria-hidden="true" class="absolute inset-0 rounded-full ring-4 ring-red-400/60 transition-transform duration-75"
          :style="{ transform: `scale(${1 + rec.level.value * 0.35})` }" />
        <ArrowPathIcon v-if="busy" class="h-7 w-7 animate-spin" />
        <StopIcon v-else-if="recording" class="h-7 w-7" />
        <MicrophoneIcon v-else class="h-7 w-7" />
      </button>
      <div class="flex-1 min-w-0" aria-live="polite">
        <p v-if="status" data-testid="voice-status"
          :class="['text-sm', isProblem ? 'text-amber-700 dark:text-amber-300' : 'font-semibold text-gray-800 dark:text-gray-100 tabular-nums']">{{ status }}</p>
        <template v-else>
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-100">{{ t('voicelog.title') }} <span class="font-normal text-gray-500 dark:text-gray-400">{{ t('voicelog.hint') }}</span></p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('voicelog.example') }}</p>
        </template>
        <button v-if="recording" type="button" data-testid="voice-cancel" class="mt-1 text-xs font-semibold text-indigo-600 dark:text-indigo-300 hover:underline"
          @click="rec.cancel()">{{ t('voicelog.cancel') }}</button>
      </div>
    </div>
    <div v-if="showConsent" data-testid="voice-consent" class="mt-3 space-y-2 text-sm text-gray-700 dark:text-gray-200">
      <p>{{ t('voicelog.consent_text') }}</p>
      <button type="button" data-testid="voice-consent-ok" @click="acceptConsent"
        class="btn-3d min-h-11 px-4 rounded-sm bg-indigo-600 text-white font-semibold hover:bg-indigo-700">{{ t('voicelog.consent_ok') }}</button>
    </div>
  </div>
</template>
