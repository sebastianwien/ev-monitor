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
      <PlaceMinimap v-if="hasMap" :lat="context.lat!" :lon="context.lon!" class="absolute inset-0" />
      <!-- Säule, Adresse, Karte: schwebt als Kärtchen unten links auf der Karte; ohne Karte als schlichte Zeile -->
      <div :class="hasMap ? 'absolute inset-x-0 bottom-0 z-[500] px-3 pb-3 pt-8 bg-gradient-to-t from-white dark:from-gray-900 to-transparent' : 'px-4'">
        <div :class="hasMap ? 'inline-flex max-w-full items-end gap-3 rounded-sm bg-white/90 dark:bg-gray-900/85 backdrop-blur px-3 py-2 shadow-sm' : 'flex items-end justify-between gap-3'">
          <div class="min-w-0">
            <p class="flex items-center gap-1.5 text-sm font-semibold text-gray-800 dark:text-gray-100 truncate"><MapPinIcon class="h-4 w-4 flex-shrink-0 text-indigo-600 dark:text-indigo-400" />{{ context.title }}</p>
            <p v-if="context.address" class="text-xs text-gray-500 dark:text-gray-400 truncate pl-[1.375rem]">{{ context.address }}</p>
          </div>
          <p v-if="context.card" class="flex items-center gap-1 flex-shrink-0 text-xs text-gray-600 dark:text-gray-300"><CreditCardIcon class="h-4 w-4" />{{ context.card }}</p>
        </div>
      </div>
    </div>

</template>
