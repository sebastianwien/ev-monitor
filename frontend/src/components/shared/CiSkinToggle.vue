<script setup lang="ts">
import { useRoute } from 'vue-router'
import { ciSkin, isSkinRoute, setCiSkin, type CiSkin } from '../../composables/useCiSkin'

/** Nur für Admins auf der Vorschau: zwischen App-CI und Probe-CI v1 umschalten, auf den Routen des Skins. Deutsch ohne i18n wie der Admin-Bereich. */
const route = useRoute()
const OPTIONS: { skin: CiSkin | null; label: string }[] = [{ skin: null, label: 'Standard' }, { skin: 'v1', label: 'Mint' }]
</script>

<template>
  <div v-if="isSkinRoute(route.name)" role="group" aria-label="CI umschalten" data-testid="ci-skin-toggle"
    class="fixed left-4 z-40 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] md:bottom-6 inline-flex h-8 overflow-hidden rounded-lg border-2 border-edge bg-white text-xs font-semibold dark:bg-gray-800">
    <button v-for="o in OPTIONS" :key="o.label" type="button" :aria-pressed="ciSkin === o.skin"
      class="px-2.5 transition-colors"
      :class="ciSkin === o.skin ? 'ci-selected bg-indigo-600 text-white dark:bg-indigo-400 dark:text-gray-900' : 'text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-white/10'"
      @click="setCiSkin(o.skin, route)">{{ o.label }}</button>
  </div>
</template>
