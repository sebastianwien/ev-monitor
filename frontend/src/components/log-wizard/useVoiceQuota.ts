import { computed, ref, watch } from 'vue'
import api from '../../api/axios'
import { analytics } from '../../services/analytics'
import { quotaNotice, type VoiceQuota } from './voiceQuota'

/**
 * Ein Stand für alle Mikrofone der Seite (Wizard-Box, Nachsprechen, Bearbeiten). Neu geladen, wenn
 * ein Einstieg erscheint (Nutzerwechsel, Monatswechsel), dazwischen aus jeder Antwort von voice-draft fortgeschrieben.
 */
const quota = ref<VoiceQuota | null>(null)
let loading: Promise<void> | null = null
const notice = computed(() => quotaNotice(quota.value))
/** Sprachlog für diesen Nutzer freigegeben: der Server liefert einen Stand (sonst 404 im Testbetrieb) */
const available = computed(() => quota.value != null)

// Einmal je Zustand messen, wie oft der Hinweis gesehen wird (Basis für die Upgrade-Quote)
watch(() => notice.value?.kind, kind => {
  if (kind && quota.value) analytics.trackVoice('quota', { kind, plan: quota.value.plan })
})

export function useVoiceQuota() {
  const load = () => {
    loading ??= api.get<VoiceQuota>('/logs/voice-quota', { params: { timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone } })
      .then(res => { quota.value = res.data })
      // Ohne Stand zeigt die Box einfach keinen Hinweis; das Backend deckelt trotzdem
      .catch(() => { quota.value = null })
      .finally(() => { loading = null })
    return loading
  }
  const set = (q: VoiceQuota) => { quota.value = q }
  return { quota, notice, available, load, set }
}
