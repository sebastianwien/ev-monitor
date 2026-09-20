<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChartBarIcon, UserGroupIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n()

const features = [
  { key: 'logs', icon: BoltIcon, tone: 'text-green-600 dark:text-green-400' },
  { key: 'stats', icon: ChartBarIcon, tone: 'text-blue-600 dark:text-blue-400' },
  { key: 'community', icon: UserGroupIcon, tone: 'text-purple-600 dark:text-purple-400' },
]
</script>

<template>
  <div class="px-4 pb-4 md:px-8">
    <h2 id="onboarding-title"
      class="text-2xl md:text-3xl font-bold text-gray-800 dark:text-gray-100 text-balance">
      {{ t('onboarding.welcome_title') }}
    </h2>
    <p class="mt-2 text-gray-600 dark:text-gray-400 text-balance">{{ t('onboarding.welcome_desc') }}</p>

    <!-- Nebeneinander erst ab sm: auf schmalen Geraeten bleibt der Text lesbar statt zu brechen. -->
    <ul class="mt-5 grid gap-3 sm:grid-cols-3">
      <li v-for="feature in features" :key="feature.key"
        class="flex items-center gap-3 rounded-sm border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700/40 p-3 sm:flex-col sm:items-start">
        <component :is="feature.icon" :class="['h-7 w-7 flex-shrink-0', feature.tone]" aria-hidden="true" />
        <div>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ t('onboarding.welcome_feat_' + feature.key) }}
          </p>
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ t('onboarding.welcome_feat_' + feature.key + '_desc') }}
          </p>
        </div>
      </li>
    </ul>
  </div>
</template>
