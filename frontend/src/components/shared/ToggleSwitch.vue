<script setup lang="ts">
/** Ein/Aus-Schalter mit 44 px Tippfläche um die kleine Schiene. An = dunkle Fläche wie ein gewählter Chip (App-CI). */
const model = defineModel<boolean>({ required: true })
defineProps<{ label: string }>()
</script>

<template>
  <button type="button" role="switch" :aria-checked="model" :aria-label="label"
    class="group -m-2 inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-full focus-visible:outline-none"
    @click="model = !model">
    <span aria-hidden="true"
      class="relative inline-flex h-5 w-9 rounded-full border-2 border-transparent transition-colors group-focus-visible:ring-2 group-focus-visible:ring-green-600 group-focus-visible:ring-offset-1 group-focus-visible:ring-offset-white dark:group-focus-visible:ring-offset-gray-900"
      :class="model ? 'bg-gray-900 dark:bg-gray-100' : 'bg-gray-300 dark:bg-gray-600'">
      <span class="pointer-events-none inline-block h-4 w-4 transform rounded-full shadow transition"
        :class="[model ? 'translate-x-4 bg-white dark:bg-gray-900' : 'translate-x-0 bg-white']" />
    </span>
  </button>
</template>
