<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewSegmentedControl from '../components/shared/ViewSegmentedControl.vue'
import SwipeTabPager from '../components/shared/SwipeTabPager.vue'
import { useStickyTabIndex } from '../composables/useStickyTabIndex'
import { CAR_TABS } from '../config/tabs'
import CarManagementView from '../views/CarManagementView.vue'

const ChargingProvidersView = defineAsyncComponent(() => import('../views/ChargingProvidersView.vue'))

/**
 * Geteiltes Layout fuer /cars und /charging-providers - der Fuhrpark aus zwei
 * Blickwinkeln: die Autos und die Karten, mit denen sie geladen werden. Beide Routen
 * zeigen auf diese Komponente, der Tab-Switch bleibt dabei stehen und der Wechsel
 * laeuft als Swipe (SwipeTabPager), genau wie Dashboard <-> Log-Feed.
 */
const { t } = useI18n()

const TAB_PATHS: readonly string[] = CAR_TABS.map(tab => tab.to)
const tabs = computed(() => CAR_TABS.map(tab => ({ to: tab.to, label: t(tab.labelKey) })))
const activeIndex = useStickyTabIndex(TAB_PATHS)
</script>

<template>
  <div>
    <!-- Mobile: Zweier-Switch ueber beiden Bodies - er bleibt stehen, waehrend der
         Inhalt darunter durchwischt. -->
    <div class="md:hidden px-2 pt-2">
      <ViewSegmentedControl class="mb-2" :tabs="tabs" />
    </div>

    <!-- Die Desktop-Leiste mit allen vier Zielen liegt in App.vue. -->
    <SwipeTabPager :tabs="TAB_PATHS" :active-index="activeIndex">
      <template #left><CarManagementView /></template>
      <template #right><ChargingProvidersView /></template>
    </SwipeTabPager>
  </div>
</template>
