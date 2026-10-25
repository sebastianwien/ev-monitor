<template>
  <BottomSheet
    ref="sheet"
    :label="t('dashboard.edit_title')"
    testid="edit-log-modal"
    panel-class="sm:max-w-xl"
    :stacked="stacked"
    @close="onClosed">
    <LogEditPanel :log="log" :start-section="startSection" @cancel="sheet?.requestClose()" @saved="onSaved" @deleted="onDeleted" />
  </BottomSheet>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import LogEditPanel, { type EvLogResponse } from './LogEditPanel.vue'
import type { SummarySection } from '../log-wizard/LogSummary.vue'

export type { EvLogResponse }

defineProps<{
  log: EvLogResponse
  /** Direkt in einer Unteransicht öffnen, z. B. "place" für "Betreiber eintragen" auf der Ladekarte. */
  startSection?: SummarySection
  /** Über einem anderen Sheet geöffnet (Ladekarte): leichter abdunkeln. */
  stacked?: boolean
}>()
const emit = defineEmits<{ close: []; saved: [log: EvLogResponse]; deleted: [logId: string] }>()
const { t } = useI18n()

// Das Sheet faehrt erst aus, dann meldet es sich - der Aufrufer entfernt uns daraufhin
// per v-if, was eine noch laufende Animation abschneiden wuerde. Ein erfolgreicher
// Speichervorgang parkt hier sein Ergebnis, bis das Sheet draussen ist.
const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const savedLog = ref<EvLogResponse | null>(null)
const deletedLogId = ref<string | null>(null)
function onSaved(log: EvLogResponse) { savedLog.value = log; sheet.value?.requestClose() }
function onDeleted(id: string) { deletedLogId.value = id; sheet.value?.requestClose() }
function onClosed() {
  if (deletedLogId.value) emit('deleted', deletedLogId.value)
  else if (savedLog.value) emit('saved', savedLog.value)
  else emit('close')
}
</script>
