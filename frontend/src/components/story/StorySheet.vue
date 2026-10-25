<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BottomSheet from '../shared/BottomSheet.vue'
import SheetCloseButton from './SheetCloseButton.vue'
import SheetGeneric from './sheets/SheetGeneric.vue'
import SheetCost from './sheets/SheetCost.vue'
import SheetFlow from './sheets/SheetFlow.vue'
import SheetRange from './sheets/SheetRange.vue'
import SheetPeer from './sheets/SheetPeer.vue'
import SheetBattery from './sheets/SheetBattery.vue'
import SheetWltp from './sheets/SheetWltp.vue'
import SheetTrips from './sheets/SheetTrips.vue'
import SheetCo2 from './sheets/SheetCo2.vue'
import SheetCharging from './sheets/SheetCharging.vue'
import type { StoryTile } from '../../composables/storyModel'
import type { StatisticsData } from '../../composables/useDashboardStats'
import type { ModelPeers, TemperatureEffect } from '../../composables/storyInsights'
import type { BatterySohEntry } from '../../api/carService'
import type { MonthCosts } from '../../composables/storyEnergy'

/** Alles, was ein Kachel-Detail brauchen kann, in einem Record. */
export interface StorySheetContext {
  stats: StatisticsData | null
  monthLabels: string[]
  /** Ausgewaehlter Monat, ausgeschrieben. */
  monthName: string
  /** Ausgewaehlter Monat ist der laufende: letzter Balken heller. */
  runningMonth: boolean
  modelPeers: ModelPeers | null
  /** Modell ohne Variante, z. B. "Model 3". */
  modelName: string
  temperature: TemperatureEffect | null
  sohHistory: BatterySohEntry[]
  effectiveCapacityKwh: number | null
  /** Fixkosten oder Einnahmen in den sechs Monaten der Kosten-Balken. */
  hasFixedCosts: boolean
  /** Strom und Fixkosten je Monat der sechs Balken, erst auf Abruf. */
  loadMonthlyCosts: () => Promise<MonthCosts[] | null>
}

/** Detail zu einer Kachel: Titel, Inhalt je Kachel-Id, Schliessen. */
const props = defineProps<{
  tile: StoryTile
  context: StorySheetContext
}>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()

const title = computed(() => props.tile.id === 'peer'
  ? t('story.sheet_peer_title', { count: props.tile.peerCount })
  : t(`story.sheet_${props.tile.id}_title`))
</script>

<template>
  <BottomSheet :label="title" testid="story-sheet" panel-class="sm:max-w-lg" edge @close="emit('close')">
    <template #default="{ close }">
      <div class="px-4 pt-3 pb-4 max-h-[85vh] overflow-y-auto">
        <div class="w-10 h-1 rounded-full bg-gray-300 dark:bg-gray-600 mx-auto mb-3" />
        <h3 class="text-xl font-extrabold text-gray-900 dark:text-gray-50" :style="{ viewTransitionName: `story-tile-${tile.id}` }">{{ title }}</h3>

        <SheetTrips v-if="tile.id === 'trips' && tile.tripMonth" :trips="tile.tripMonth" :charged-kwh-per100km="context.stats?.avgConsumptionKwhPer100km ?? null" />
        <SheetFlow v-else-if="tile.id === 'flow' && (tile.flow || tile.use)" :flow="tile.flow" :use="tile.use" :standby-gaps="tile.tripMonth?.standbyGaps ?? []" />
        <SheetCo2 v-else-if="tile.id === 'co2' && tile.co2" :tile="tile" />
        <SheetCharging v-else-if="tile.id === 'charging' && tile.charging" :split="tile.charging" />
        <SheetRange v-else-if="tile.id === 'range' && tile.range" :range="tile.range" :capacity-kwh="context.effectiveCapacityKwh" />
        <SheetPeer v-else-if="tile.id === 'peer'" :tile="tile" :stats="context.stats" :model-peers="context.modelPeers" :model-name="context.modelName" />
        <SheetBattery v-else-if="tile.id === 'battery'" :tile="tile" :history="context.sohHistory" />
        <SheetWltp v-else-if="tile.id === 'wltp'" :tile="tile" :month-labels="context.monthLabels" :month-name="context.monthName" :running-month="context.runningMonth" :temperature="context.temperature" />
        <SheetCost
          v-else-if="tile.id === 'cost'" :tile="tile" :stats="context.stats" :month-labels="context.monthLabels" :month-name="context.monthName"
          :running-month="context.runningMonth" :has-fixed-costs="context.hasFixedCosts" :load-monthly-costs="context.loadMonthlyCosts" />
        <SheetGeneric v-else :tile="tile" :month-labels="context.monthLabels" :running-month="context.runningMonth" />
        <SheetCloseButton class="mt-5" @click="close" />
      </div>
    </template>
  </BottomSheet>
</template>
