<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  ArrowUpOnSquareIcon, CheckCircleIcon, DevicePhoneMobileIcon,
  EllipsisVerticalIcon, PlusCircleIcon,
} from '@heroicons/vue/24/outline'
import type { InstallPlatform } from '../../../composables/usePwaInstall'

defineProps<{
  platform: InstallPlatform
  isStandalone: boolean
  outcome: 'accepted' | 'dismissed' | null
}>()

const { t } = useI18n()

const IOS_STEPS = [
  { key: 'install_ios_step1', icon: ArrowUpOnSquareIcon },
  { key: 'install_ios_step2', icon: PlusCircleIcon },
  { key: 'install_ios_step3', icon: CheckCircleIcon },
]
</script>

<template>
  <div class="px-4 pb-4 md:px-8">
    <div class="flex items-start gap-3">
      <DevicePhoneMobileIcon class="h-8 w-8 flex-shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
      <div>
        <h2 id="onboarding-title" class="text-xl md:text-2xl font-bold text-gray-800 dark:text-gray-100 text-balance">
          {{ t('onboarding.install_title') }}
        </h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400 text-balance">{{ t('onboarding.install_desc') }}</p>
      </div>
    </div>

    <!-- Schon installiert: das gilt auch direkt nach dem Zusagen im Dialog. -->
    <div v-if="isStandalone" data-testid="install-done"
      class="mt-5 flex items-center gap-3 rounded-sm border border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/30 p-3">
      <CheckCircleIcon class="h-6 w-6 flex-shrink-0 text-green-600 dark:text-green-400" aria-hidden="true" />
      <p class="text-sm font-medium text-green-800 dark:text-green-200">{{ t('onboarding.install_done') }}</p>
    </div>

    <p v-else-if="outcome === 'dismissed'" class="mt-5 rounded-sm bg-gray-50 dark:bg-gray-700/50 p-3 text-sm text-gray-600 dark:text-gray-300">
      {{ t('onboarding.install_dismissed') }}
    </p>

    <!-- Mit Prompt: der Knopf steht im Fuss, hier braucht es keine Anleitung. -->
    <template v-else-if="platform === 'ios'">
      <p class="mt-5 text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('onboarding.install_ios_title') }}</p>
      <ol class="mt-2 flex flex-col gap-2">
        <li v-for="(step, i) in IOS_STEPS" :key="step.key"
          class="flex items-center gap-3 rounded-sm border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700/40 p-3">
          <span class="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-indigo-600 text-xs font-bold text-white">
            {{ i + 1 }}
          </span>
          <component :is="step.icon" class="h-5 w-5 flex-shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          <span class="text-sm text-gray-700 dark:text-gray-200">{{ t('onboarding.' + step.key) }}</span>
        </li>
      </ol>
    </template>

    <div v-else-if="platform === 'other'"
      class="mt-5 flex items-start gap-3 rounded-sm border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700/40 p-3">
      <EllipsisVerticalIcon class="h-5 w-5 flex-shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
      <div>
        <p class="text-sm font-semibold text-gray-700 dark:text-gray-200">{{ t('onboarding.install_other_title') }}</p>
        <p class="text-sm text-gray-600 dark:text-gray-300">{{ t('onboarding.install_other_desc') }}</p>
      </div>
    </div>
  </div>
</template>
