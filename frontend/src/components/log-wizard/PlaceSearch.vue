<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useLocationSearch, type PickedPlace } from '../../composables/useLocationSearch'

defineProps<{ label: string; placeholder: string }>()
const emit = defineEmits<{ picked: [place: PickedPlace] }>()
const { t } = useI18n()
const search = useLocationSearch()
</script>

<template>
  <div class="space-y-1">
    <label for="wizard-place-search" class="block text-xs text-gray-500 dark:text-gray-400">{{ label }}</label>
    <div class="relative">
      <input id="wizard-place-search" v-model="search.query.value" type="text" :placeholder="placeholder" autocomplete="off"
        class="w-full rounded-sm border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 px-3 py-2 text-sm" />
      <ul v-if="search.suggestions.value.length" role="listbox"
        class="absolute z-10 mt-1 w-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-sm shadow-[4px_4px_0_rgba(0,0,0,0.30)] max-h-48 overflow-y-auto">
        <li v-for="s in search.suggestions.value" :key="s.place_id" role="option"
          class="px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
          @mousedown.prevent="emit('picked', search.select(s))">
          {{ s.display_name }}
        </li>
      </ul>
    </div>
    <p v-if="search.selectedName.value" class="text-xs text-green-600">{{ t('logfields.new_location') }} {{ search.selectedName.value }}</p>
  </div>
</template>
