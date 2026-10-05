<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { MicrophoneIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import { useVoiceRecorder, pickMimeType } from '../../composables/useVoiceRecorder'
import { analytics } from '../../services/analytics'
import type { VoiceDraft } from './wizardLogic'
import { voiceProblem, type VoiceProblem } from './voiceProblem'
import { durationBucket, latencyBucket, filledCount, placeOutcome, type VoiceEntry } from './voiceAnalytics'
import VoiceSheet from './VoiceSheet.vue'
import { useVoiceQuota } from './useVoiceQuota'
import { quotaFromLimit, quotaFromUsage } from './voiceQuota'
import { getPricing } from '../../config/pricingConfig'
import { useCountryStore } from '../../stores/country'
import { recordVoiceUse, voiceFamiliar } from './voiceFamiliar'
import { useKeyboardOpen } from '../../composables/useKeyboardOpen'

const props = withDefaults(defineProps<{
  carId: string; latitude?: number | null; longitude?: number | null
  /** Anlegen oder Bearbeiten - nur für die Messung und den Hinweis im Vollbild */
  entry?: VoiceEntry
  /**
   * footer: Einstieg im Wizard - Knopf links neben "Weiter", Hinweise als Streifen darüber (beides in der WizardShell).
   * inline: schmale Zeile zum Ergänzen oder Korrigieren nach einer ersten Aufnahme.
   */
  variant?: 'footer' | 'inline'
  /** inline: Text statt "Ergänzen oder korrigieren", z. B. was noch fehlt */
  label?: string | null
}>(), { latitude: null, longitude: null, entry: 'create', variant: 'inline', label: null })
const emit = defineEmits<{ draft: [draft: VoiceDraft] }>()
const { t, locale } = useI18n()

/** Der Transparenz-Satz kommt einmal, beim ersten Antippen - danach direkt aufnehmen. v2: Satz zur Adresssuche ergänzt. */
const CONSENT_KEY = 'voicelog_consent_seen_v2'
const consentSeen = () => { try { return localStorage.getItem(CONSENT_KEY) === 'true' } catch { return false } }
const showConsent = ref(false)

const problem = ref<VoiceProblem | null>(null)
const { notice, load: loadQuota, set: setQuota } = useVoiceQuota()
onMounted(loadQuota)
const out = computed(() => notice.value?.kind === 'out')
const countryStore = useCountryStore()
const supporterPrice = computed(() => getPricing(countryStore.country).supporterMonthly)
const month = computed(() => new Date().toLocaleDateString(locale.value, { month: 'long' }))
const onUpsell = () => analytics.trackVoice('upsell', { entry: props.entry, kind: notice.value?.kind ?? 'low' })
const extension = (type: string) => type.startsWith('audio/mp4') ? 'm4a' : type.startsWith('audio/ogg') ? 'ogg' : 'webm'
const again = computed(() => props.variant === 'inline')
// Neu: Knopf mit Text und Beispielsatz; nach zwei Aufnahmen nur das Symbol. Beim Öffnen festgelegt, nichts springt mitten im Ablauf.
const stage: 'new' | 'familiar' = voiceFamiliar() ? 'familiar' : 'new'
const footer = props.variant === 'footer'
const keyboardOpen = useKeyboardOpen()

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
    if (res.data.usage) setQuota(quotaFromUsage(res.data.usage))
    recordVoiceUse()
    emit('draft', res.data)
  } catch (e) {
    problem.value = voiceProblem(e)
    // Aufgebraucht zeigt der Hinweis unter dem Mikrofon, nicht die Fehlerzeile
    if (problem.value.kind === 'limit') { setQuota(quotaFromLimit(problem.value)); problem.value = null }
    analytics.trackVoice('error', { entry: props.entry, kind: problem.value?.kind ?? 'limit' })
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
  analytics.trackVoice('open', { entry: props.entry, again: again.value, ...(footer ? { stage } : {}) })
  if (again.value) analytics.trackVoice('retry', { entry: props.entry })
  rec.press()
}
const onPress = (e?: PointerEvent) => {
  // Finger bleibt beim Halten auf dem Knopf, auch wenn das Vollbild darüber aufgeht
  if (e) (e.currentTarget as Element | null)?.setPointerCapture?.(e.pointerId)
  problem.value = null
  if (rec.state.value === 'recording') { rec.press(); return }
  if (rec.state.value !== 'idle' && rec.state.value !== 'done' && rec.state.value !== 'error') return
  if (out.value) return
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
const disabled = computed(() => busy.value || out.value)
/** Was der Streifen über dem Footer zeigt, wichtigstes zuerst. Das Beispiel nur für Neue und nicht bei offener Tastatur. */
const note = computed(() => {
  if (rec.state.value === 'denied') return 'denied'
  if (showConsent.value) return 'consent'
  if (status.value) return 'status'
  if (notice.value) return notice.value.kind
  if (stage === 'new' && !keyboardOpen.value) return 'example'
  return null
})
const resetsOn = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'long' })
const status = computed(() => {
  const p = problem.value
  // Aufgebraucht ist kein Fehler: das zeigt der Kontingent-Hinweis, auch wenn der Recorder nach dem 429 auf error steht
  if (out.value) return null
  if (p) return t(`voicelog.error_${p.kind}`)
  if (rec.state.value === 'error') return t('voicelog.error_mic')
  return null
})
</script>

<template>
  <!-- Wizard: Knopf und Streifen leben in der WizardShell (Daumenreichweite), hier bleibt nichts sichtbar stehen.
       defer: die Ziele im Footer stehen erst nach diesem Schritt im DOM. -->
  <div v-if="variant === 'footer'" class="contents" data-testid="voice-capture" data-variant="footer">
    <Teleport defer to="#wizard-footer-note">
      <div aria-live="polite">
        <div v-if="note" data-testid="voice-note" :data-note="note"
          class="border-t border-indigo-100 dark:border-indigo-900/60 bg-indigo-50 dark:bg-indigo-950/60 px-4 py-2.5 md:px-6 text-sm text-gray-700 dark:text-gray-200">
          <p v-if="note === 'denied'" data-testid="voice-denied">{{ t('voicelog.denied') }}</p>
          <div v-else-if="note === 'consent'" data-testid="voice-consent" class="space-y-2">
            <!-- Kurz halten, Details in der DSE. Neuer Tab, damit die Eingaben im Wizard bleiben. -->
            <i18n-t keypath="voicelog.consent_text" tag="p">
              <template #privacy>
                <a href="/datenschutz#spracheingabe" target="_blank" rel="noopener" data-testid="voice-consent-privacy"
                  class="underline underline-offset-2 text-indigo-700 dark:text-indigo-300">{{ t('voicelog.consent_privacy') }}</a>
              </template>
            </i18n-t>
            <button type="button" data-testid="voice-consent-ok" @click="acceptConsent"
              class="btn-3d min-h-11 px-4 rounded-sm bg-indigo-600 text-white font-semibold hover:bg-indigo-700">{{ t('voicelog.consent_ok') }}</button>
          </div>
          <p v-else-if="note === 'status'" data-testid="voice-problem" class="text-amber-700 dark:text-amber-300">{{ status }}</p>
          <div v-else-if="notice?.kind === 'out'" data-testid="voice-quota-out">
            <p class="font-semibold text-gray-800 dark:text-gray-100">
              {{ notice.limit != null ? t('voicelog.quota_out_title', { limit: notice.limit, month }) : t('voicelog.quota_out_title_plain') }}</p>
            <p class="mt-0.5">
              {{ notice.upsell ? t('voicelog.quota_out_upsell', { date: resetsOn(notice.resetsOn), price: supporterPrice }) : t('voicelog.quota_out_body', { date: resetsOn(notice.resetsOn) }) }}</p>
            <div v-if="notice.upsell" class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2">
              <router-link :to="{ name: 'supporter', query: { from: 'voice' } }" data-testid="voice-upsell" @click="onUpsell"
                class="btn-3d inline-flex items-center min-h-11 px-4 rounded-sm bg-indigo-600 text-white font-semibold hover:bg-indigo-700">{{ t('voicelog.quota_cta') }}</router-link>
              <span>{{ t('voicelog.quota_type_on') }}</span>
            </div>
          </div>
          <p v-else-if="notice?.kind === 'low'" data-testid="voice-quota-low">
            {{ t(notice.upsell ? 'voicelog.quota_low_free' : 'voicelog.quota_low_paid', { n: notice.remaining, limit: notice.limit, month }, notice.remaining) }}<template v-if="notice.upsell"> ·
              <router-link :to="{ name: 'supporter', query: { from: 'voice' } }" data-testid="voice-upsell" @click="onUpsell"
                class="whitespace-nowrap font-semibold text-indigo-700 dark:text-indigo-300 underline underline-offset-2">{{ t('voicelog.quota_low_cta') }}</router-link></template>
          </p>
          <!-- Ein kurzes Beispiel statt Bedienanleitung: ein lockerer Halbsatz reicht. Die volle Liste steht im Vollbild. -->
          <p v-else-if="note === 'example'"><span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('voicelog.title') }}</span> {{ t('voicelog.card_example') }}</p>
        </div>
      </div>
    </Teleport>
    <Teleport defer to="#wizard-footer-lead">
      <!-- Tippen startet, "Fertig" im Vollbild beendet; Halten nimmt auf, bis der Finger loslässt. Kein click-Handler, der würde doppelt auslösen.
           Neu mit Text, damit klar ist, was er tut; vertraut nur das Symbol, "Weiter" bekommt die Breite. -->
      <button v-if="!out && rec.state.value !== 'denied'" type="button" data-testid="voice-mic" :disabled="busy" :aria-label="t('voicelog.mic_label')"
        :class="['flex-shrink-0 inline-flex items-center justify-center gap-2 h-12 border-2 border-indigo-600 dark:border-indigo-400 text-indigo-700 dark:text-indigo-200 bg-white dark:bg-gray-800 font-semibold hover:bg-indigo-50 dark:hover:bg-indigo-900/40 transition select-none touch-none [-webkit-touch-callout:none] disabled:opacity-70',
                 stage === 'new' ? 'flex-1 px-3 rounded-sm' : 'w-12 rounded-full']"
        @pointerdown.prevent="onPress" @pointerup="rec.release()" @pointercancel="rec.release()" @contextmenu.prevent
        @keydown.enter.prevent="onPress()" @keydown.space.prevent="onPress()">
        <ArrowPathIcon v-if="busy" class="h-6 w-6 shrink-0 animate-spin" />
        <MicrophoneIcon v-else class="h-6 w-6 shrink-0" />
        <span v-if="stage === 'new'" aria-hidden="true">{{ t('voicelog.footer_cta') }}</span>
      </button>
    </Teleport>
    <VoiceSheet v-if="sheetOpen" :processing="rec.state.value === 'uploading'" :elapsed-ms="rec.elapsedMs.value" :level="rec.level.value"
      :entry="entry" @stop="rec.press()" @cancel="cancel" />
  </div>
  <div v-else data-testid="voice-capture" data-variant="inline">
    <p v-if="rec.state.value === 'denied'" data-testid="voice-denied" class="text-sm text-gray-700 dark:text-gray-200">{{ t('voicelog.denied') }}</p>
    <div v-else class="flex items-center gap-3">
      <button type="button" data-testid="voice-mic" :disabled="disabled" :aria-label="label ?? t('voicelog.again')"
        :class="['relative flex-shrink-0 grid place-items-center h-11 w-11 rounded-full text-white bg-indigo-600 hover:bg-indigo-700 transition select-none touch-none [-webkit-touch-callout:none] disabled:opacity-70',
                 out ? '!bg-gray-400 dark:!bg-gray-600 !opacity-100' : '']"
        @pointerdown.prevent="onPress" @pointerup="rec.release()" @pointercancel="rec.release()" @contextmenu.prevent
        @keydown.enter.prevent="onPress()" @keydown.space.prevent="onPress()">
        <ArrowPathIcon v-if="busy" class="h-5 w-5 animate-spin" />
        <MicrophoneIcon v-else class="h-5 w-5" />
      </button>
      <div class="flex-1 min-w-0" aria-live="polite">
        <p v-if="status" data-testid="voice-problem" class="text-sm text-amber-700 dark:text-amber-300">{{ status }}</p>
        <div v-else-if="notice?.kind === 'out'" data-testid="voice-quota-out">
          <p class="text-sm font-semibold text-gray-800 dark:text-gray-100">
            {{ notice.limit != null ? t('voicelog.quota_out_title', { limit: notice.limit, month }) : t('voicelog.quota_out_title_plain') }}</p>
          <p class="text-sm text-gray-600 dark:text-gray-300 mt-0.5">
            {{ notice.upsell ? t('voicelog.quota_out_upsell', { date: resetsOn(notice.resetsOn), price: supporterPrice }) : t('voicelog.quota_out_body', { date: resetsOn(notice.resetsOn) }) }}</p>
          <div v-if="notice.upsell" class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2">
            <router-link :to="{ name: 'supporter', query: { from: 'voice' } }" data-testid="voice-upsell" @click="onUpsell"
              class="btn-3d inline-flex items-center min-h-11 px-4 rounded-sm bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700">{{ t('voicelog.quota_cta') }}</router-link>
            <span class="text-sm text-gray-600 dark:text-gray-300">{{ t('voicelog.quota_type_on') }}</span>
          </div>
        </div>
        <template v-else>
          <p class="text-sm font-semibold text-indigo-700 dark:text-indigo-300">{{ label ?? t('voicelog.again') }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('voicelog.again_hint') }}</p>
        </template>
        <p v-if="notice?.kind === 'low' && !status" data-testid="voice-quota-low" class="text-xs text-gray-600 dark:text-gray-300 mt-1">
          {{ t(notice.upsell ? 'voicelog.quota_low_free' : 'voicelog.quota_low_paid', { n: notice.remaining, limit: notice.limit, month }, notice.remaining) }}<template v-if="notice.upsell"> ·
            <router-link :to="{ name: 'supporter', query: { from: 'voice' } }" data-testid="voice-upsell" @click="onUpsell"
              class="whitespace-nowrap font-semibold text-indigo-700 dark:text-indigo-300 underline underline-offset-2">{{ t('voicelog.quota_low_cta') }}</router-link></template>
        </p>
      </div>
    </div>
    <div v-if="showConsent" data-testid="voice-consent" class="mt-3 space-y-2 text-sm text-gray-700 dark:text-gray-200">
      <i18n-t keypath="voicelog.consent_text" tag="p">
        <template #privacy>
          <a href="/datenschutz#spracheingabe" target="_blank" rel="noopener" data-testid="voice-consent-privacy"
            class="underline underline-offset-2 text-indigo-700 dark:text-indigo-300">{{ t('voicelog.consent_privacy') }}</a>
        </template>
      </i18n-t>
      <button type="button" data-testid="voice-consent-ok" @click="acceptConsent"
        class="btn-3d min-h-11 px-4 rounded-sm bg-indigo-600 text-white font-semibold hover:bg-indigo-700">{{ t('voicelog.consent_ok') }}</button>
    </div>
    <VoiceSheet v-if="sheetOpen" :processing="rec.state.value === 'uploading'" :elapsed-ms="rec.elapsedMs.value" :level="rec.level.value"
      :entry="entry" @stop="rec.press()" @cancel="cancel" />
  </div>
</template>
