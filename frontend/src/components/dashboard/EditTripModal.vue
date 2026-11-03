<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import DayTripPanel from '../story/day/DayTripPanel.vue'
import type { DayTrip } from '../../composables/storyDay'

/**
 * Sheet um dieselbe Fahrt-Ansicht wie Tagesblatt und Logbuch (DayTripPanel): Karte, Formular,
 * Löschen. Speichern und Löschen erledigt das Panel selbst.
 *
 * Das Sheet fährt erst aus, dann meldet es sich - der Aufrufer entfernt uns daraufhin per v-if.
 * `changed` kommt deshalb erst mit dem Ausfahren, wenn gespeichert oder gelöscht wurde.
 */
defineProps<{ trip: DayTrip; carId: string
  /** Über einem anderen Sheet (Tagesblatt): leichter abdunkeln */
  stacked?: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const { t } = useI18n()

const sheet = ref<InstanceType<typeof BottomSheet> | null>(null)
const changed = ref(false)
function done() { changed.value = true; sheet.value?.requestClose() }
function onClosed() {
  if (changed.value) emit('changed')
  emit('close')
}
</script>

<template>
  <BottomSheet
    ref="sheet"
    :label="t('dashboard.trip_edit')"
    testid="edit-trip-modal"
    edge
    panel-class="sm:max-w-xl"
    :stacked="stacked"
    @close="onClosed">
    <DayTripPanel :trip="trip" :car-id="carId" :new-end="new Date()" dismiss="none"
      @back="sheet?.requestClose()" @saved="done" @deleted="done" @changed="changed = true" />
  </BottomSheet>
</template>
