import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'

export type TimePick = 'now' | 'hour' | 'yesterday' | 'other'

const pad = (n: number) => String(n).padStart(2, '0')
const toLocal = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
export const nowLocal = () => toLocal(new Date())
const oneHourAgo = () => toLocal(new Date(Date.now() - 3_600_000))
const yesterdayEvening = () => { const d = new Date(); d.setDate(d.getDate() - 1); d.setHours(20, 0, 0, 0); return toLocal(d) }

/** Zeitpunkt als vier Wahlen: jetzt (null), vor einer Stunde, gestern Abend, frei - geteilt von Details-Formular und Pillen-Editor. */
export function useTimePick(form: Ref<LogFormData>) {
  const { t } = useI18n()
  const timeOther = ref(false)
  const timePick = computed<TimePick>(() => {
    if (timeOther.value) return 'other'
    if (!form.value.loggedAt) return 'now'
    if (form.value.loggedAt === oneHourAgo()) return 'hour'
    if (form.value.loggedAt === yesterdayEvening()) return 'yesterday'
    return 'other'
  })
  const pickTime = (p: TimePick) => {
    timeOther.value = p === 'other'
    if (p === 'now') form.value.loggedAt = null
    else if (p === 'hour') form.value.loggedAt = oneHourAgo()
    else if (p === 'yesterday') form.value.loggedAt = yesterdayEvening()
    else if (!form.value.loggedAt) form.value.loggedAt = nowLocal()
  }
  const timeChips: { value: TimePick; label: string }[] = [
    { value: 'now', label: t('logfields.timestamp_chip_now') },
    { value: 'hour', label: t('logfields.timestamp_chip_1h_ago') },
    { value: 'yesterday', label: t('logfields.timestamp_chip_yesterday_evening') },
    { value: 'other', label: t('logwizard.d_time_other') },
  ]
  return { timePick, pickTime, timeChips }
}
