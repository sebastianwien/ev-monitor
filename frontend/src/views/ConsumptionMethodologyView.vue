<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { BoltIcon, BoltSlashIcon, ArrowDownIcon, InformationCircleIcon, SignalIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n()
</script>

<template>
  <div class="md:max-w-3xl md:mx-auto md:p-6">
    <div class="bg-white dark:bg-gray-800 md:rounded-xl md:shadow-lg p-4 md:p-8 space-y-12">

      <!-- Hero -->
      <div>
        <h1 class="text-3xl sm:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-3">
          {{ t('cm.title') }}
        </h1>
        <p class="text-lg text-gray-600 dark:text-gray-400">{{ t('cm.intro') }}</p>
      </div>

      <!-- Die drei Messpunkte -->
      <section>
        <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">{{ t('cm.s1_title') }}</h2>
        <p class="text-gray-600 dark:text-gray-400 mb-6">{{ t('cm.s1_desc') }}</p>

        <div class="space-y-1">

          <!-- Grid -->
          <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl p-4">
            <div class="flex items-start gap-3">
              <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-yellow-100 dark:bg-yellow-900 flex items-center justify-center">
                <BoltIcon class="w-4 h-4 text-yellow-600 dark:text-yellow-400" />
              </div>
              <div class="flex-1">
                <div class="flex items-baseline justify-between gap-2 flex-wrap">
                  <span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('cm.grid_label') }}</span>
                  <span class="text-sm font-mono font-bold text-yellow-700 dark:text-yellow-400">24,82 kWh</span>
                </div>
                <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ t('cm.grid_desc') }}</p>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-3 px-4 py-1">
            <ArrowDownIcon class="w-4 h-4 text-gray-400 flex-shrink-0 ml-2" />
            <span class="text-sm text-gray-400 dark:text-gray-500">{{ t('cm.loss1') }}</span>
          </div>

          <!-- AT_VEHICLE -->
          <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-xl p-4">
            <div class="flex items-start gap-3">
              <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-green-100 dark:bg-green-900 flex items-center justify-center">
                <BoltIcon class="w-4 h-4 text-green-600 dark:text-green-400" />
              </div>
              <div class="flex-1">
                <div class="flex items-baseline justify-between gap-2 flex-wrap">
                  <span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('cm.battery_label') }}</span>
                  <span class="text-sm font-mono font-bold text-green-700 dark:text-green-400">24,00 kWh</span>
                </div>
                <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{{ t('cm.battery_desc') }}</p>
                <div class="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-green-700 dark:text-green-400 bg-green-100 dark:bg-green-900/40 px-2 py-0.5 rounded-full">
                  <SignalIcon class="w-3 h-3" />
                  {{ t('cm.battery_badge') }}
                </div>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-3 px-4 py-1">
            <ArrowDownIcon class="w-4 h-4 text-gray-400 flex-shrink-0 ml-2" />
            <span class="text-sm text-gray-400 dark:text-gray-500">{{ t('cm.loss2') }}</span>
          </div>

          <!-- Bordcomputer -->
          <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4">
            <div class="flex items-start gap-3">
              <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center">
                <BoltSlashIcon class="w-4 h-4 text-blue-600 dark:text-blue-400" />
              </div>
              <div class="flex-1">
                <div class="flex items-baseline justify-between gap-2 flex-wrap">
                  <span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('cm.bc_label') }}</span>
                  <span class="text-sm font-mono font-bold text-blue-700 dark:text-blue-400">~15 kWh/100km</span>
                </div>
                <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5" v-html="t('cm.bc_desc')" />
              </div>
            </div>
          </div>

        </div>
      </section>

      <!-- SoC-Zeitlinie -->
      <section>
        <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t('cm.s2_title') }}</h2>
        <p class="text-gray-600 dark:text-gray-400 mb-6" v-html="t('cm.s2_desc')" />

        <div class="relative space-y-0">

          <div class="flex items-center gap-4">
            <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">{{ t('cm.charge_end') }}<br><span class="font-semibold text-gray-700 dark:text-gray-300">11.04.</span></div>
            <div class="flex-shrink-0 w-3 h-3 rounded-full bg-green-500 ring-2 ring-white dark:ring-gray-900 z-10"></div>
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <div class="h-5 rounded bg-green-500" style="width: 87%"></div>
                <span class="text-sm font-bold text-green-700 dark:text-green-400 whitespace-nowrap">87% SoC</span>
              </div>
              <div class="text-xs text-green-700 dark:text-green-400 mt-0.5">{{ t('cm.known') }}</div>
            </div>
          </div>

          <div class="flex items-stretch gap-4">
            <div class="w-28 flex-shrink-0"></div>
            <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-orange-300 dark:bg-orange-700"></div></div>
            <div class="flex items-center py-2"><span class="text-xs text-orange-600 dark:text-orange-400 italic">{{ t('cm.standby_loss') }}</span></div>
          </div>

          <div class="flex items-center gap-4">
            <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">{{ t('cm.trip_start') }}</div>
            <div class="flex-shrink-0 w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600 ring-2 ring-white dark:ring-gray-900 z-10"></div>
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <div class="h-5 rounded bg-gray-300 dark:bg-gray-600" style="width: 82%"></div>
                <span class="text-sm font-bold text-gray-400 dark:text-gray-500 whitespace-nowrap">~82%?</span>
              </div>
              <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5" v-html="t('cm.unknown')" />
            </div>
          </div>

          <div class="flex items-stretch gap-4">
            <div class="w-28 flex-shrink-0"></div>
            <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-blue-400 dark:bg-blue-600"></div></div>
            <div class="flex items-center py-2"><span class="text-xs text-blue-600 dark:text-blue-400 italic">{{ t('cm.trip_segment') }}</span></div>
          </div>

          <div class="flex items-center gap-4">
            <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">{{ t('cm.trip_end') }}</div>
            <div class="flex-shrink-0 w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600 ring-2 ring-white dark:ring-gray-900 z-10"></div>
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <div class="h-5 rounded bg-gray-300 dark:bg-gray-600" style="width: 29%"></div>
                <span class="text-sm font-bold text-gray-400 dark:text-gray-500 whitespace-nowrap">~29%?</span>
              </div>
              <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5" v-html="t('cm.unknown')" />
            </div>
          </div>

          <div class="flex items-stretch gap-4">
            <div class="w-28 flex-shrink-0"></div>
            <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-orange-300 dark:bg-orange-700"></div></div>
            <div class="flex items-center py-2"><span class="text-xs text-orange-600 dark:text-orange-400 italic">{{ t('cm.standby_loss') }}</span></div>
          </div>

          <div class="flex items-center gap-4">
            <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">{{ t('cm.charge_start') }}<br><span class="font-semibold text-gray-700 dark:text-gray-300">16.04.</span></div>
            <div class="flex-shrink-0 w-3 h-3 rounded-full bg-green-500 ring-2 ring-white dark:ring-gray-900 z-10"></div>
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <div class="h-5 rounded bg-green-500" style="width: 26%"></div>
                <span class="text-sm font-bold text-green-700 dark:text-green-400 whitespace-nowrap">26% SoC</span>
              </div>
              <div class="text-xs text-green-700 dark:text-green-400 mt-0.5">{{ t('cm.known') }}</div>
            </div>
          </div>

        </div>

        <div class="mt-5 bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-xl p-4">
          <div class="flex gap-2 items-start">
            <InformationCircleIcon class="w-5 h-5 text-orange-600 dark:text-orange-400 flex-shrink-0 mt-0.5" />
            <div class="text-sm text-orange-800 dark:text-orange-300" v-html="t('cm.s2_info')" />
          </div>
        </div>
      </section>

      <!-- Formel -->
      <section>
        <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">{{ t('cm.s3_title') }}</h2>
        <p class="text-gray-600 dark:text-gray-400 mb-4">{{ t('cm.s3_desc') }}</p>
        <div class="bg-gray-900 dark:bg-gray-800 rounded-xl p-4 font-mono text-sm text-green-400 mb-4 overflow-x-auto">
          <div>{{ t('cm.formula') }}</div>
        </div>
        <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4">
          <div class="flex gap-2 items-start">
            <InformationCircleIcon class="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
            <div class="text-sm text-blue-800 dark:text-blue-300" v-html="t('cm.soc_correction')" />
          </div>
        </div>
      </section>

      <!-- Standby -->
      <section>
        <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">{{ t('cm.s4_title') }}</h2>
        <p class="text-gray-600 dark:text-gray-400 mb-4">{{ t('cm.s4_desc') }}</p>
        <div class="grid sm:grid-cols-2 gap-4">
          <div class="bg-gray-50 dark:bg-gray-700 rounded-xl p-4 border border-gray-200 dark:border-gray-600">
            <div class="text-2xl font-bold text-green-600 dark:text-green-400 mb-1">+1.75-2 kWh</div>
            <div class="text-sm text-gray-600 dark:text-gray-400">{{ t('cm.stat1_desc') }}</div>
          </div>
          <div class="bg-gray-50 dark:bg-gray-700 rounded-xl p-4 border border-gray-200 dark:border-gray-600">
            <div class="text-2xl font-bold text-orange-500 dark:text-orange-400 mb-1">1-3 kWh/{{ t('cm.per_day') }}</div>
            <div class="text-sm text-gray-600 dark:text-gray-400">{{ t('cm.stat2_desc') }}</div>
          </div>
        </div>
      </section>

      <!-- Unschärfe -->
      <section>
        <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">{{ t('cm.s5_title') }}</h2>
        <p class="text-gray-600 dark:text-gray-400 mb-4">{{ t('cm.s5_intro') }}</p>
        <ul class="space-y-3 text-sm text-gray-600 dark:text-gray-400">
          <li v-for="i in [1,2,3,4]" :key="i" class="flex gap-3">
            <span class="text-gray-400 dark:text-gray-500 flex-shrink-0 font-mono">-</span>
            <span v-html="t(`cm.bullet${i}`)" />
          </li>
        </ul>
        <p class="mt-4 text-sm text-gray-500 dark:text-gray-400">{{ t('cm.s5_footer') }}</p>
      </section>

      <!-- Quellen -->
      <section class="border-t border-gray-200 dark:border-gray-700 pt-8">
        <h2 class="text-base font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('cm.sources_title') }}</h2>
        <ul class="space-y-2 text-sm">
          <li>
            <a href="https://www.researchgate.net/publication/267928696_Electric_vehicles'_energy_consumption_measurement_and_estimation"
               target="_blank" rel="noopener noreferrer"
               class="text-green-700 dark:text-green-400 hover:underline">
              Electric vehicles' energy consumption measurement and estimation - ResearchGate
            </a>
          </li>
          <li>
            <a href="https://www.sciencedirect.com/science/article/pii/S2352484719310911"
               target="_blank" rel="noopener noreferrer"
               class="text-green-700 dark:text-green-400 hover:underline">
              Real-world study for optimal charging of electric vehicles - ScienceDirect
            </a>
          </li>
          <li>
            <a href="https://evlife.world/tech/how-does-ev-power-consumption-information-work/"
               target="_blank" rel="noopener noreferrer"
               class="text-green-700 dark:text-green-400 hover:underline">
              How do you measure power consumption of an EV? - EVLife
            </a>
          </li>
        </ul>
      </section>

    </div>
  </div>
</template>
