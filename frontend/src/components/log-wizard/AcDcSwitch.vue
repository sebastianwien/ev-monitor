<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PillSwitch from './PillSwitch.vue'
/** Ladeart als Kippschalter */
const model = defineModel<'AC' | 'DC'>({ required: true })
const { t } = useI18n()
</script>

<template>
  <PillSwitch v-model="model" :label="t('logwizard.charging_type')" class="w-[5.5rem]"
    :options="[{ value: 'AC', label: 'AC', testid: 'charging-type-ac' }, { value: 'DC', label: 'DC', testid: 'charging-type-dc' }]" />
</template>
