<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LogFormData } from '../log-form/logFormData'
import { ROUTE_CHIPS } from './optionalChips'
import { chipClass } from './chipClass'

/**
 * Gezielte Nachfrage statt Pflichtfeld: erscheint nur, wenn seit der letzten Ladung viel Energie in kurzer
 * Zeit verbraucht wurde (routeTypeHint). "Ja" setzt Autobahn; "Nein" bietet Stadt oder Gemischt an, beides
 * überspringbar. Die Antwort landet als vom Nutzer gewählter Streckentyp im Payload.
 */
const form = defineModel<LogFormData>({ required: true })
const { t } = useI18n()
const declined = ref(false)
const pick = (v: NonNullable<LogFormData['routeType']>) => { form.value.routeType = v }
const otherChips = ROUTE_CHIPS.filter(c => c.value !== 'HIGHWAY')
</script>

<template>
  <div v-if="form.routeType == null" class="edge-box rounded-xl bg-white dark:bg-gray-700/50 p-3 space-y-3" data-testid="highway-question">
    <p class="text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t(declined ? 'logwizard.ask_highway_other' : 'logwizard.ask_highway') }}</p>
    <div v-if="!declined" class="grid grid-cols-2 gap-2">
      <button type="button" :class="chipClass(false)" data-testid="highway-yes" @click="pick('HIGHWAY')">{{ t('logwizard.ask_highway_yes') }}</button>
      <button type="button" :class="chipClass(false)" data-testid="highway-no" @click="declined = true">{{ t('logwizard.ask_highway_no') }}</button>
    </div>
    <div v-else class="grid grid-cols-2 gap-2" role="radiogroup" :aria-label="t('logwizard.d_route')">
      <button v-for="c in otherChips" :key="c.value" type="button" role="radio" :aria-checked="false" :class="chipClass(false)"
        :data-testid="`highway-other-${c.value}`" @click="pick(c.value)"><component :is="c.icon" class="h-4 w-4 flex-shrink-0 inline mr-1" />{{ t(c.key) }}</button>
    </div>
  </div>
</template>
