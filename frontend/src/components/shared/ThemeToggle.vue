<template>
  <button
    @click="themeStore.toggle()"
    class="grid h-10 w-10 place-items-center rounded-full transition-colors hover:bg-black/10 dark:hover:bg-white/10"
    :title="themeStore.isDark ? 'Hell-Modus' : 'Dunkel-Modus'"
    aria-label="Theme wechseln">
    <SunIcon v-if="themeStore.isDark" class="h-5 w-5" />
    <MoonIcon v-else class="h-5 w-5" />
  </button>
</template>

<script setup lang="ts">
import { SunIcon, MoonIcon } from '@heroicons/vue/24/outline'
import { useThemeStore } from '../../stores/theme'

const themeStore = useThemeStore()
</script>
