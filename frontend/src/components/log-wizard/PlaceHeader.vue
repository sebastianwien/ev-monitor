<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { MINIMAP_MAX_CLASS, MINIMAP_AREA_ZOOM, MINIMAP_ZOOM, type MinimapArea } from './minimapTiles'
import { useI18n } from 'vue-i18n'
import { MapPinIcon, CreditCardIcon, ClockIcon } from '@heroicons/vue/24/outline'
const PlaceMinimap = defineAsyncComponent(() => import('./PlaceMinimap.vue'))

/** Was Schritt 1 ergeben hat - steht als Kopf über den Zahlen und in der Zusammenfassung, damit niemand zurückblättern muss. */
export interface NumbersContext { title: string; address: string | null; card: string | null; lat: number | null; lon: number | null
  /** Private Ladung: die gespeicherte Zelle als Fläche statt Punkt */
  area?: MinimapArea | null }

/**
 * Kopf mit Minimap der Säule, darauf schwebend Name, Adresse und Ladekarte. Ohne Position oder mit
 * map=false nur die Textzeile (Schritt 2: die Zahlen sind der Held, die Karte gehört in die Prüfung).
 * Die Höhe gibt der Aufrufer vor (die Zusammenfassung füllt damit den freien Platz).
 * `time` (Bearbeiten): Zeitpunkt als zweite Zeile mit eigenem Tipp; dann ist der Ortsname das Tastaturziel für den Ort,
 * nicht der ganze Kopf - sonst stünde ein Knopf im Knopf.
 */
const props = withDefaults(defineProps<{ context: NumbersContext; height?: number; clickable?: boolean; map?: boolean; time?: string | null }>(), { height: 0, map: true, time: null })
const emit = defineEmits<{ click: []; time: [] }>()
const hasMap = computed(() => props.map && props.context.lat != null && props.context.lon != null)
const wholeTarget = computed(() => props.clickable && !props.time)
const titleTarget = computed(() => props.clickable && !!props.time)
const { t } = useI18n()
</script>

<template>
    <!-- Klickbar (Prüfung: Tipp springt zum Ort) auch per Tastatur: role, tabindex und Enter/Leertaste -->
    <div data-testid="numbers-context" :class="['relative', clickable && 'cursor-pointer']" @click="emit('click')" class=" -mx-4 md:mx-0 md:rounded-xl overflow-hidden"
      :role="wholeTarget ? 'button' : undefined" :tabindex="wholeTarget ? 0 : undefined" @keydown.enter.self.prevent="wholeTarget && emit('click')" @keydown.space.self.prevent="wholeTarget && emit('click')"
      :style="hasMap ? { height: `${height}px` } : undefined">
      <!-- Die Karte ist immer so hoch wie der größte Kopf und mittig verankert: der Rahmen wächst und schrumpft animiert,
           die Karte wird nur beschnitten statt neu layoutet - sonst flackern die Kacheln bei jeder Zwischenhöhe. -->
      <PlaceMinimap v-if="hasMap" :lat="context.lat!" :lon="context.lon!" :area="context.area ?? null" :zoom="context.area ? MINIMAP_AREA_ZOOM : MINIMAP_ZOOM" :class="['absolute inset-x-0 top-1/2 -translate-y-1/2', MINIMAP_MAX_CLASS]" />
      <!-- Säule, Adresse, Karte stehen direkt auf der Karte; der hohe Verlauf nach unten macht den Text lesbar und führt zu den Schaltern -->
      <div :class="hasMap ? 'absolute inset-x-0 bottom-0 z-[500] px-4 pb-3 pt-14 bg-gradient-to-t from-white via-white/70 to-transparent dark:from-gray-900 dark:via-gray-900/70' : 'px-4'">
        <div class="flex items-end justify-between gap-3">
          <div class="min-w-0">
            <p class="flex items-center gap-1.5 rounded text-[15px] font-semibold text-gray-900 dark:text-gray-50 truncate focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600"
              :role="titleTarget ? 'button' : undefined" :tabindex="titleTarget ? 0 : undefined"
              @keydown.enter.prevent="titleTarget && emit('click')" @keydown.space.prevent="titleTarget && emit('click')"><MapPinIcon class="h-4 w-4 flex-shrink-0 text-indigo-600 dark:text-indigo-400" />{{ context.title }}</p>
            <p v-if="context.address" class="text-xs text-gray-500 dark:text-gray-400 truncate pl-[1.375rem]">{{ context.address }}</p>
            <!-- Sichtbar eine Textzeile, Trefferfläche 44 px über negative Ränder -->
            <button v-if="time" type="button" data-testid="summary-time" :aria-label="`${t('logfields.timestamp')}: ${time}`" @click.stop="emit('time')"
              class="-my-3 flex min-h-11 items-center gap-1.5 rounded text-[13px] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-green-600 font-semibold tabular-nums text-gray-700 dark:text-gray-200">
              <ClockIcon class="h-4 w-4 flex-shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />{{ time }}
            </button>
            <p v-if="hasMap && context.area" data-testid="place-area-hint" class="text-xs text-gray-500 dark:text-gray-400 pl-[1.375rem]">{{ t('logwizard.place_area_hint') }}</p>
          </div>
          <p v-if="context.card" class="flex items-center gap-1 flex-shrink-0 text-[13px] text-gray-600 dark:text-gray-300"><CreditCardIcon class="h-4 w-4" />{{ context.card }}</p>
        </div>
      </div>
    </div>

</template>
