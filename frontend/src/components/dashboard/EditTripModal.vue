<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../../composables/useLocaleFormat'
import BottomSheet from '../shared/BottomSheet.vue'
import TripForm from './TripForm.vue'

/**
 * Sheet um das TripForm. Im Log-Feed wird dieselbe Fahrt inline in ihrer Zeile
 * bearbeitet - auf dem Dashboard fehlt diese Zeile, also bekommt das Formular hier ein
 * eigenes Overlay. Formular-Model, Speichern und Fehlerbehandlung liegen unveraendert
 * beim Aufrufer (useLogList), diese Komponente ist nur die Huelle.
 *
 * `save` meldet nur den Klick - ob gespeichert werden konnte, weiss der Aufrufer. Er
 * faehrt das Sheet danach ueber `requestClose()` aus; `close` kommt, wenn es draussen
 * ist und der Aufrufer uns entfernen darf.
 */
const emit = defineEmits<{ save: []; close: [] }>()

defineProps<{
  /** Validierungs-/Serverfehler aus useLogList. */
  error?: string | null
  /** Speichern laeuft - Buttons sperren. */
  saving?: boolean
}>()

const form = defineModel<Record<string, any>>({ required: true })
const { t } = useI18n()
const { distanceUnitLabel } = useLocaleFormat()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
defineExpose({ requestClose: () => sheet.value?.requestClose() })
</script>

<template>
  <BottomSheet
    ref="sheet"
    :label="t('dashboard.trip_edit')"
    testid="edit-trip-modal"
    @close="emit('close')">
    <template #default="{ close }">
      <div class="flex-1 overflow-y-auto p-4">
        <TripForm
          v-model="form"
          mode="edit"
          :error="error"
          :saving="saving"
          :distance-unit="distanceUnitLabel()"
          @save="emit('save')"
          @cancel="close" />
      </div>
    </template>
  </BottomSheet>
</template>
