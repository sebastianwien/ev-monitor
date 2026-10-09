<template>
  <PublicModelsListView v-if="isLegacy" />
  <PublicModelsListViewV2 v-else />
</template>

<script setup lang="ts">
// Release 2: the ranking (V2) is the default for /modelle and indexable. The classic list
// stays reachable under ?ansicht=alt until it is removed; it loads as its own chunk.
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import PublicModelsListViewV2 from './PublicModelsListViewV2.vue'

const PublicModelsListView = defineAsyncComponent(() => import('./PublicModelsListView.vue'))

const route = useRoute()
const isLegacy = computed(() => route.query.ansicht === 'alt')
</script>
