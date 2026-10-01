<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { MapPinIcon, CreditCardIcon } from '@heroicons/vue/24/outline'
const PlaceMinimap = defineAsyncComponent(() => import('./PlaceMinimap.vue'))

/** Was Schritt 1 ergeben hat - steht als Kopf über den Zahlen und in der Zusammenfassung, damit niemand zurückblättern muss. */
export interface NumbersContext { title: string; address: string | null; card: string | null; lat: number | null; lon: number | null }

/**
 * Kopf mit Minimap der Säule, darauf schwebend Name, Adresse und Ladekarte. Ohne Position nur die Textzeile.
 * Die Höhe gibt der Aufrufer vor (Schritt 2 füllt den freien Platz, die Zusammenfassung nimmt eine feste).
 */
const props = defineProps<{ context: NumbersContext; height: number; clickable?: boolean }>()
const emit = defineEmits<{ click: [] }>()
const hasMap = computed(() => props.context.lat != null && props.context.lon != null)
</script>

<template>
    <div data-testid="numbers-context" :class="['relative', clickable && 'cursor-pointer']" @click="emit('click')" class=" -mx-4 md:mx-0 md:rounded-sm overflow-hidden transition-[height] duration-300 ease-out motion-reduce:transition-none"
      :style="hasMap ? { height: `${height}px` } : undefined">
      <!-- Die Karte ist immer so hoch wie der größte Kopf und mittig verankert: der Rahmen wächst und schrumpft animiert,
           die Karte wird nur beschnitten statt neu layoutet - sonst flackern die Kacheln bei jeder Zwischenhöhe. -->
      <PlaceMinimap v-if="hasMap" :lat="context.lat!" :lon="context.lon!" class="absolute inset-x-0 top-1/2 -translate-y-1/2 h-[640px]" />
      <!-- Säule, Adresse, Karte stehen direkt auf der Karte; der hohe Verlauf nach unten macht den Text lesbar und führt zu den Schaltern -->
      <div :class="hasMap ? 'absolute inset-x-0 bottom-0 z-[500] px-4 pb-1 pt-14 bg-gradient-to-t from-white via-white/80 to-transparent dark:from-gray-900 dark:via-gray-900/80' : 'px-4'">
        <div class="flex items-end justify-between gap-3">
          <div class="min-w-0">
            <p class="flex items-center gap-1.5 text-sm font-semibold text-gray-800 dark:text-gray-100 truncate"><MapPinIcon class="h-4 w-4 flex-shrink-0 text-indigo-600 dark:text-indigo-400" />{{ context.title }}</p>
            <p v-if="context.address" class="text-xs text-gray-500 dark:text-gray-400 truncate pl-[1.375rem]">{{ context.address }}</p>
          </div>
          <p v-if="context.card" class="flex items-center gap-1 flex-shrink-0 text-xs text-gray-600 dark:text-gray-300"><CreditCardIcon class="h-4 w-4" />{{ context.card }}</p>
        </div>
      </div>
    </div>

</template>
