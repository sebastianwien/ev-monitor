<template>
  <!-- The page's chips look like its buttons: dark outline, a small hard shadow, pressed in when chosen.
       Dark mode drops outline and shadow: translucent surfaces (they lift off box and card alike), the chosen one light.
       A toggle reports aria-pressed; as a link (href, an in-app path) it keeps the look without a state. -->
  <component
    :is="href ? RouterLink : 'button'"
    :to="href"
    :type="href ? undefined : 'button'"
    :aria-pressed="href ? undefined : active"
    class="btn-3d cta-shadow-sm inline-flex flex-none items-center gap-1.5 whitespace-nowrap rounded-full border-2 font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
    :class="[
      SIZES[size],
      active
        ? 'active border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900'
        : solid
          ? 'border-edge bg-white text-gray-800 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'
          : 'border-edge bg-white text-gray-800 hover:bg-gray-50 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15',
    ]"
  ><slot /></component>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(defineProps<{
  /** chosen; ignored on a link */
  active?: boolean
  /** renders a link with the chip's look */
  href?: string
  /** sm: dense rows of chips; lg: a question's answers (44 px touch target) */
  size?: 'sm' | 'md' | 'lg'
  /** opaque in dark mode too: chips straight on the wallpaper, not on a box */
  solid?: boolean
}>(), { active: false, href: undefined, size: 'md', solid: false })

const SIZES = { sm: 'min-h-9 px-3.5 text-[13.5px]', md: 'min-h-10 px-3.5 text-sm', lg: 'min-h-11 px-5 text-[14px]' }
</script>
