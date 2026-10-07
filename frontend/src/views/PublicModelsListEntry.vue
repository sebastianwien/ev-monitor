<template>
  <PublicModelsListViewV2 v-if="isPreview" preview />
  <PublicModelsListView v-else />
</template>

<script setup lang="ts">
// Preview switch for the new ranking: /modelle?ansicht=neu shows V2 (noindex), everything
// else keeps the classic list. V2 loads as its own chunk, so V1 visitors don't download it.
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import PublicModelsListView from './PublicModelsListView.vue'

const PublicModelsListViewV2 = defineAsyncComponent(() => import('./PublicModelsListViewV2.vue'))

const route = useRoute()
const isPreview = computed(() => route.query.ansicht === 'neu')
</script>
