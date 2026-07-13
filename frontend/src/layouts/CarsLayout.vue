<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ViewSegmentedControl from '../components/shared/ViewSegmentedControl.vue'
import SwipeTabPager from '../components/shared/SwipeTabPager.vue'
import { CAR_TABS } from '../config/carTabs'
import CarManagementView from '../views/CarManagementView.vue'

const ChargingProvidersView = defineAsyncComponent(() => import('../views/ChargingProvidersView.vue'))

/**
 * Geteiltes Layout fuer /cars und /charging-providers - der Fuhrpark aus zwei
 * Blickwinkeln: die Autos und die Karten, mit denen sie geladen werden. Beide Routen
 * zeigen auf diese Komponente, der Tab-Switch bleibt dabei stehen und der Wechsel
 * laeuft als Swipe (SwipeTabPager), genau wie Dashboard <-> Log-Feed.
 */
const route = useRoute()
const { t } = useI18n()

const TAB_PATHS: readonly string[] = CAR_TABS.map(tab => tab.to)
const tabs = computed(() => CAR_TABS.map(tab => ({ to: tab.to, label: t(tab.labelKey) })))
const activeIndex = computed(() => Math.max(0, TAB_PATHS.indexOf(route.path)))
</script>

<template>
  <div>
    <!-- Persistenter Tab-Switch ueber beiden Bodies: er bleibt stehen, waehrend der
         Inhalt darunter durchwischt. -->
    <div class="md:max-w-4xl md:mx-auto px-2 pt-2 md:px-6 md:pt-6">
      <ViewSegmentedControl class="mb-2 md:mb-4" :tabs="tabs" />
    </div>

    <SwipeTabPager :tabs="TAB_PATHS" :active-index="activeIndex">
      <template #left><CarManagementView /></template>
      <template #right><ChargingProvidersView /></template>
    </SwipeTabPager>
  </div>
</template>
