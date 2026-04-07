<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-6xl mx-auto px-4 py-8">
      <!-- Breadcrumb -->
      <nav class="text-sm text-gray-500 dark:text-gray-400 mb-6">
        <a href="/modelle" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('compare.breadcrumb_models') }}</a>
        <span class="mx-2">›</span>
        <span class="text-gray-900 dark:text-gray-100">{{ t('compare.breadcrumb_current') }}</span>
      </nav>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- Error / invalid params -->
      <div v-else-if="error || validModels.length < 2" class="text-center py-20">
        <TruckIcon class="h-16 w-16 text-gray-300 dark:text-gray-600 mx-auto mb-4" />
        <h1 class="text-xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('compare.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ error || t('compare.error_min_models') }}</p>
        <a href="/modelle" class="bg-green-600 text-white px-6 py-3 rounded-lg hover:bg-green-700">
          {{ t('compare.breadcrumb_models') }}
        </a>
      </div>

      <template v-else>
        <!-- Header -->
        <div class="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 p-6 mb-6">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">
            {{ validModels.map(m => m.modelDisplayName).join(' vs. ') }}
          </h1>
          <p class="text-gray-500 dark:text-gray-400 text-sm">
            {{ t('compare.subtitle') }}
          </p>
          <a href="/modelle" class="inline-flex items-center gap-1.5 text-sm text-green-600 hover:underline mt-3">
            <ArrowLeftIcon class="h-4 w-4" />
            {{ t('compare.change_models') }}
          </a>
        </div>

        <!-- Sentinel for IntersectionObserver (mobile sticky header trigger) -->
        <div ref="stickysentinel" class="block md:hidden h-0"></div>

        <!-- Fixed floating header (shown when sentinel scrolls out of view) -->
        <Transition name="slide-down">
          <div v-if="floatingHeaderVisible"
               class="block md:hidden fixed left-0 right-0 z-40 bg-white dark:bg-gray-900 border-b-2 border-gray-100 dark:border-gray-800 px-4 py-2 shadow-md"
               :style="{ top: floatingHeaderTop }">
            <div :class="validModels.length === 3 ? 'grid grid-cols-3 gap-3' : 'grid grid-cols-2 gap-3'">
              <div v-for="(m, mi) in validModels" :key="m.brand + m.model" class="text-center">
                <div class="text-xs font-bold text-gray-900 dark:text-gray-100 leading-tight truncate">{{ m.modelDisplayName }}</div>
                <select
                  v-if="m.wltpVariants.length > 1"
                  :value="selectedVariantIdx[mi]"
                  @change="selectedVariantIdx[mi] = +($event.target as HTMLSelectElement).value"
                  class="mt-1 w-full text-[11px] rounded border px-1 py-0.5 bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 focus:outline-none focus:border-green-500">
                  <option v-for="(v, vi) in m.wltpVariants" :key="vi" :value="vi">{{ v.variantName ? `${v.variantName} (${v.batteryCapacityKwh} kWh)` : `${v.batteryCapacityKwh} kWh` }}{{ v.realConsumptionTripCount ? ` · ${v.realConsumptionTripCount} Fahrten` : '' }}</option>
                </select>
              </div>
            </div>
          </div>
        </Transition>

        <!-- Comparison: Mobile Card Layout -->
        <div class="block md:hidden bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 mb-6">
          <!-- Static model header (inside card, always present) -->
          <div class="bg-white dark:bg-gray-900 border-b-2 border-gray-100 dark:border-gray-800 px-4 py-3 rounded-t-2xl">
            <div :class="validModels.length === 3 ? 'grid grid-cols-3 gap-2' : 'grid grid-cols-2 gap-2'">
              <div v-for="(m, mi) in validModels" :key="m.brand + m.model" class="text-center">
                <a :href="`/modelle/${m.brand}/${m.model}`"
                   class="text-xs font-bold text-gray-900 dark:text-gray-100 hover:text-green-600 dark:hover:text-green-400 leading-tight block">
                  {{ m.modelDisplayName }}
                </a>
                <span v-if="m.logCount > 0" class="text-[10px] text-green-600 dark:text-green-400 flex items-center justify-center gap-0.5 mt-0.5">
                  <ChartBarIcon class="h-2.5 w-2.5" />Community
                </span>
                <span v-else class="text-[10px] text-gray-400 dark:text-gray-500 block mt-0.5">WLTP</span>
                <!-- Variant picker -->
                <div v-if="m.wltpVariants.length > 1" class="mt-1.5">
                  <select
                    :value="selectedVariantIdx[mi]"
                    @change="selectedVariantIdx[mi] = +($event.target as HTMLSelectElement).value"
                    class="w-full text-[11px] rounded border px-1.5 py-0.5 bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 focus:outline-none focus:border-green-500">
                    <option v-for="(v, vi) in m.wltpVariants" :key="vi" :value="vi">{{ v.variantName ? `${v.variantName} (${v.batteryCapacityKwh} kWh)` : `${v.batteryCapacityKwh} kWh` }}{{ v.realConsumptionTripCount ? ` · ${v.realConsumptionTripCount} Fahrten` : '' }}</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          <!-- Section helper -->
          <template v-for="section in mobileSections" :key="section.label">
            <div class="bg-gray-50 dark:bg-gray-800 px-4 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide text-center">
              {{ section.label }}
            </div>
            <div v-for="row in section.rows" :key="row.label" class="border-t border-gray-100 dark:border-gray-800 px-4 py-3">
              <div class="text-xs mb-2 text-center" :class="row.labelClass ?? 'text-gray-500 dark:text-gray-400'">
                {{ row.label }}
                <span v-if="row.sublabel" class="block text-gray-400 dark:text-gray-500 font-normal">{{ row.sublabel }}</span>
              </div>
              <div :class="validModels.length === 3 ? 'grid grid-cols-3 gap-2' : 'grid grid-cols-2 gap-2'">
                <div v-for="(cell, i) in row.cells" :key="i"
                     class="text-center text-sm font-medium py-1 rounded-lg"
                     :class="[cell.highlight, cell.isBest ? 'bg-green-50 dark:bg-green-900/20' : cell.isWorst ? 'bg-red-50 dark:bg-red-900/20' : '']">
                  <template v-if="cell.chip">
                    <span :class="cell.chipClass" class="text-xs px-2 py-0.5 rounded-full font-medium">
                      {{ cell.value }}
                    </span>
                  </template>
                  <template v-else>{{ cell.value }}</template>
                </div>
              </div>
            </div>
          </template>

          <!-- Legend -->
          <div class="px-4 py-3 border-t border-gray-100 dark:border-gray-800 flex flex-wrap gap-4 text-xs text-gray-400 dark:text-gray-500 rounded-b-2xl">
            <span class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-full bg-green-500 inline-block"></span>
              {{ t('compare.legend_best') }}
            </span>
            <span class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-full bg-red-400 inline-block"></span>
              {{ t('compare.legend_worst') }}
            </span>
            <span>{{ t('compare.legend_no_data') }}</span>
          </div>
        </div>

        <!-- Comparison: Desktop Table -->
        <div class="hidden md:block bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 overflow-hidden mb-6">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[480px] text-sm">
              <!-- Model header row -->
              <thead>
                <tr class="border-b-2 border-gray-100 dark:border-gray-800">
                  <th class="sticky left-0 bg-white dark:bg-gray-900 z-10 w-36 py-4 px-4 text-left font-normal text-gray-400"></th>
                  <th v-for="(m, mi) in validModels" :key="m.brand + m.model"
                      class="py-4 px-3 text-center"
                      :class="m.logCount === 0 ? 'opacity-60' : ''">
                    <a :href="`/modelle/${m.brand}/${m.model}`"
                       class="font-bold text-gray-900 dark:text-gray-100 hover:text-green-600 dark:hover:text-green-400 transition-colors block">
                      {{ m.modelDisplayName }}
                    </a>
                    <!-- Real data badge -->
                    <span v-if="m.logCount > 0"
                          class="inline-flex items-center gap-1 mt-1 text-xs px-2 py-0.5 rounded-full bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-800 text-green-700 dark:text-green-400 font-normal">
                      <ChartBarIcon class="h-3 w-3" />
                      {{ t('compare.badge_community') }}
                    </span>
                    <!-- WLTP-only badge -->
                    <span v-else
                          class="inline-flex items-center gap-1 mt-1 text-xs px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-gray-400 dark:text-gray-500 font-normal">
                      {{ t('compare.badge_wltp_only') }}
                    </span>
                    <!-- Variant picker -->
                    <div v-if="m.wltpVariants.length > 1" class="mt-2">
                      <select
                        :value="selectedVariantIdx[mi]"
                        @change="selectedVariantIdx[mi] = +($event.target as HTMLSelectElement).value"
                        class="text-xs rounded border px-2 py-0.5 bg-white dark:bg-gray-900 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 focus:outline-none focus:border-green-500">
                        <option v-for="(v, vi) in m.wltpVariants" :key="vi" :value="vi">
                          {{ v.variantName ? `${v.variantName} (${v.batteryCapacityKwh} kWh)` : `${v.batteryCapacityKwh} kWh` }}{{ v.realConsumptionTripCount ? ` · ${v.realConsumptionTripCount} Fahrten` : '' }}
                        </option>
                      </select>
                    </div>
                  </th>
                </tr>
              </thead>

              <tbody>
                <!-- ── Block 1: Verbrauch & Effizienz ───────────────────── -->
                <tr class="bg-gray-50 dark:bg-gray-800">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                    {{ t('compare.section_consumption') }}
                  </td>
                </tr>
                <!-- Real consumption -->
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_real_consumption') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, realConsumptions)">
                    {{ validModels[i].avgConsumptionKwhPer100km ? formatConsumption(validModels[i].avgConsumptionKwhPer100km) : '–' }}
                  </td>
                </tr>
                <!-- WLTP consumption -->
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_wltp_consumption') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center text-gray-700 dark:text-gray-300">
                    {{ wltpConsumptions[i] ? formatConsumption(wltpConsumptions[i]!) : '–' }}
                  </td>
                </tr>
                <!-- Delta WLTP -->
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_wltp_delta') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, wltpDeltas)">
                    {{ wltpDeltas[i] != null ? (wltpDeltas[i]! > 0 ? '+' : '') + wltpDeltas[i]!.toFixed(1) + '%' : '–' }}
                  </td>
                </tr>

                <!-- ── Block 2: Reichweite ──────────────────────────────── -->
                <tr class="bg-gray-50 dark:bg-gray-800">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                    {{ t('compare.section_range') }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    <div>{{ t('compare.row_real_range') }}</div>
                    <div class="text-xs text-gray-400 dark:text-gray-500 font-normal">{{ t('compare.row_real_range_sub') }}</div>
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, realRanges, false)">
                    {{ realRanges[i] ? formatDistance(realRanges[i]!) : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_wltp_range') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center text-gray-700 dark:text-gray-300">
                    {{ wltpRanges[i] ? formatDistance(wltpRanges[i]!) : '–' }}
                  </td>
                </tr>

                <!-- ── Block 3: Kosten ──────────────────────────────────── -->
                <tr class="bg-gray-50 dark:bg-gray-800">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                    {{ t('compare.section_costs') }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_costs_per_100') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, costsPerHundred)">
                    {{ costsPerHundred[i] ? formatCostPerDistance(costsPerHundred[i]!) : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_costs_per_kwh') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, costsPerKwh)">
                    {{ costsPerKwh[i] != null ? formatCostPerKwh(costsPerKwh[i]!) : '–' }}
                  </td>
                </tr>

                <!-- ── Block 4: Saisonal ────────────────────────────────── -->
                <tr class="bg-gray-50 dark:bg-gray-800">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                    {{ t('compare.section_seasonal') }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    🌞 {{ t('compare.row_summer') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, summerConsumptions)">
                    {{ summerConsumptions[i] ? formatConsumption(summerConsumptions[i]!) : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    ❄️ {{ t('compare.row_winter') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, winterConsumptions)">
                    {{ winterConsumptions[i] ? formatConsumption(winterConsumptions[i]!) : '–' }}
                  </td>
                </tr>

                <!-- ── Block 5: Datenbasis ──────────────────────────────── -->
                <tr class="bg-gray-50 dark:bg-gray-800">
                  <td :colspan="validModels.length + 1" class="sticky left-0 px-4 py-2 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                    {{ t('compare.section_data') }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_log_count') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center" :class="cellClass(i, logCounts, false)">
                    {{ validModels[i].logCount > 0 ? validModels[i].logCount.toLocaleString() : '–' }}
                  </td>
                </tr>
                <tr class="border-t border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                  <td class="sticky left-0 bg-white dark:bg-gray-900 z-10 px-4 py-3 text-gray-600 dark:text-gray-400 font-medium whitespace-nowrap">
                    {{ t('compare.row_contributors') }}
                  </td>
                  <td v-for="(_, i) in validModels" :key="i"
                      class="px-3 py-3 text-center text-gray-700 dark:text-gray-300">
                    {{ validModels[i].uniqueContributors > 0 ? validModels[i].uniqueContributors : '–' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Legend -->
          <div class="px-4 py-3 border-t border-gray-100 dark:border-gray-800 flex flex-wrap gap-4 text-xs text-gray-400 dark:text-gray-500">
            <span class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-full bg-green-500 inline-block"></span>
              {{ t('compare.legend_best') }}
            </span>
            <span class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-full bg-red-400 inline-block"></span>
              {{ t('compare.legend_worst') }}
            </span>
            <span>{{ t('compare.legend_no_data') }}</span>
          </div>
        </div>

        <!-- Links to individual pages -->
        <div class="bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 p-5 mb-6">
          <p class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('compare.detail_pages') }}</p>
          <div class="flex flex-wrap gap-3">
            <a v-for="m in validModels" :key="m.brand + m.model"
               :href="`/modelle/${m.brand}/${m.model}`"
               class="flex items-center gap-1.5 text-sm text-green-600 dark:text-green-400 hover:underline">
              <TruckIcon class="h-4 w-4" />
              {{ m.modelDisplayName }} →
            </a>
          </div>
        </div>

        <!-- CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 rounded-2xl p-6 text-white">
          <h2 class="text-xl font-bold mb-2">{{ t('compare.cta_title') }}</h2>
          <p class="text-green-100 text-sm mb-4">
            {{ t('compare.cta_desc') }}
          </p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath" class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors text-sm">
              {{ t('compare.cta_register') }}
            </a>
            <a :href="loginPath" class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors text-sm">
              {{ t('compare.cta_login') }}
            </a>
          </div>
        </div>
      </template>
    </main>

    <footer class="max-w-6xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 dark:border-gray-800 text-sm text-gray-500 dark:text-gray-400 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/modelle" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('compare.all_models') }}</a>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { getModelStats, type PublicModelStats } from '../api/publicModelService'
import { TruckIcon, ArrowLeftIcon, ChartBarIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'
import { useAuthStore } from '../stores/auth'
import { useTickerState } from '../composables/useTickerState'
import { useLocaleFormat } from '../composables/useLocaleFormat'

const { t, locale } = useI18n()
const { formatConsumption, formatDistance, formatCostPerKwh, formatCostPerDistance } = useLocaleFormat()
const authStore = useAuthStore()
const { tickerHasItems, tickerCollapsed } = useTickerState()
const floatingHeaderTop = computed(() => {
  if (!authStore.isAuthenticated()) return '0px'
  if (tickerHasItems.value && !tickerCollapsed.value) return '90px'
  return '58px'
})
const registerPath = computed(() => locale.value === 'en' ? '/en/register' : '/register')
const loginPath = computed(() => locale.value === 'en' ? '/en/login' : '/login')

const route = useRoute()
const loading = ref(true)

// ── Floating sticky header (IntersectionObserver) ─────────────────────────────
const stickysentinel = ref<HTMLElement | null>(null)
const floatingHeaderVisible = ref(false)
let observer: IntersectionObserver | null = null

watch(stickysentinel, (el) => {
  observer?.disconnect()
  if (!el) return
  observer = new IntersectionObserver(
    ([entry]) => { floatingHeaderVisible.value = !entry.isIntersecting },
    { threshold: 0, rootMargin: '0px' }
  )
  observer.observe(el)
})

onUnmounted(() => observer?.disconnect())
const error = ref<string | null>(null)
const models = ref<(PublicModelStats | null)[]>([])
const currentYear = new Date().getFullYear()

const modelKeys = computed((): string[] => {
  const raw = route.query.models as string | undefined
  if (!raw) return []
  return raw.split(',').slice(0, 3).map(s => s.trim()).filter(Boolean)
})

onMounted(async () => {
  if (modelKeys.value.length < 2) {
    error.value = t('compare.error_need_two')
    loading.value = false
    return
  }
  try {
    models.value = await Promise.all(
      modelKeys.value.map(key => {
        const [brand, model] = key.split('/')
        return getModelStats(brand, model)
      })
    )
  } catch {
    error.value = t('compare.error_loading')
  } finally {
    loading.value = false
  }
})

const validModels = computed(() => models.value.filter((m): m is PublicModelStats => m !== null))

// ── Variant selection (one index per model) ────────────────────────────────────
const selectedVariantIdx = ref<number[]>([])

// Reset variant selection when models load
watch(validModels, (newModels) => {
  selectedVariantIdx.value = newModels.map(() => 0)
}, { immediate: true })

function selectedVariant(modelIdx: number) {
  const m = validModels.value[modelIdx]
  if (!m || !m.wltpVariants.length) return null
  const idx = selectedVariantIdx.value[modelIdx] ?? 0
  return m.wltpVariants[idx] ?? m.wltpVariants[0]
}

// ── Metric arrays (index = model index) ───────────────────────────────────────
const realConsumptions = computed(() =>
  validModels.value.map((m, i) => {
    const variant = selectedVariant(i)
    // prefer variant-specific real consumption if available and has enough data
    if (variant?.realConsumptionKwhPer100km != null && (variant.realConsumptionTripCount ?? 0) >= 5) {
      return variant.realConsumptionKwhPer100km
    }
    return m.avgConsumptionKwhPer100km
  })
)

const wltpConsumptions = computed(() =>
  validModels.value.map((_, i) => selectedVariant(i)?.wltpConsumptionKwhPer100km ?? null)
)

const wltpDeltas = computed(() =>
  validModels.value.map((_, i) => {
    const real = realConsumptions.value[i]
    const wltp = wltpConsumptions.value[i]
    if (!real || !wltp) return null
    return ((real - wltp) / wltp) * 100
  })
)

const realRanges = computed(() =>
  validModels.value.map((_, i) => {
    const real = realConsumptions.value[i]
    const variant = selectedVariant(i)
    if (!real || !variant) return null
    return Math.round(variant.batteryCapacityKwh * 0.8 / real * 100)
  })
)

const wltpRanges = computed(() =>
  validModels.value.map((_, i) => selectedVariant(i)?.wltpRangeKm ?? null)
)

const costsPerHundred = computed(() =>
  validModels.value.map((m, i) => {
    const real = realConsumptions.value[i]
    if (!m.avgCostPerKwh || !real) return null
    return m.avgCostPerKwh * real
  })
)

const costsPerKwh = computed(() =>
  validModels.value.map(m => m.avgCostPerKwh ?? null)
)

const summerConsumptions = computed(() =>
  validModels.value.map((m, i) => {
    const variant = selectedVariant(i)
    return variant?.seasonalDistribution?.summerConsumptionKwhPer100km
      ?? m.seasonalDistribution?.summerConsumptionKwhPer100km
      ?? null
  })
)

const winterConsumptions = computed(() =>
  validModels.value.map((m, i) => {
    const variant = selectedVariant(i)
    return variant?.seasonalDistribution?.winterConsumptionKwhPer100km
      ?? m.seasonalDistribution?.winterConsumptionKwhPer100km
      ?? null
  })
)

const logCounts = computed(() => validModels.value.map(m => m.logCount))

// ── Mobile sections data ───────────────────────────────────────────────────────
interface MobileCell { value: string; highlight: string; isBest: boolean; isWorst: boolean; chip: boolean; chipClass: string }
interface MobileRow { label: string; sublabel?: string; labelClass?: string; cells: MobileCell[] }
interface MobileSection { label: string; rows: MobileRow[] }

function buildCells(values: (number | null)[], format: (v: number) => string, lowerIsBetter = true, chip = false): MobileCell[] {
  const best = bestIdx(values, lowerIsBetter)
  const worst = worstIdx(values, lowerIsBetter)
  return values.map((v, i) => {
    const isBest = i === best
    const isWorst = i === worst
    const highlight = isBest ? 'text-green-600 dark:text-green-400 font-bold' : isWorst ? 'text-red-500 dark:text-red-400' : 'text-gray-800 dark:text-gray-200'
    const rawValue = v != null ? format(v) : '–'
    let chipClass = ''
    if (chip && v != null) {
      chipClass = v <= 5 ? 'chip-green' : v <= 15 ? 'chip-yellow' : 'chip-red'
    }
    return { value: rawValue, highlight, isBest, isWorst, chip, chipClass }
  })
}

const mobileSections = computed((): MobileSection[] => {
  const t2 = t
  return [
    {
      label: t2('compare.section_consumption'),
      rows: [
        {
          label: t2('compare.row_real_consumption'),
          cells: buildCells(realConsumptions.value, v => formatConsumption(v))
        },
        {
          label: t2('compare.row_wltp_consumption'),
          cells: wltpConsumptions.value.map(v => ({ value: v != null ? formatConsumption(v) : '–', highlight: 'text-gray-700 dark:text-gray-300', isBest: false, isWorst: false, chip: false, chipClass: '' }))
        },
        {
          label: t2('compare.row_wltp_delta'),
          cells: buildCells(wltpDeltas.value, v => (v > 0 ? '+' : '') + v.toFixed(1) + '%')
        }
      ]
    },
    {
      label: t2('compare.section_range'),
      rows: [
        {
          label: t2('compare.row_real_range'),
          sublabel: t2('compare.row_real_range_sub'),
          cells: buildCells(realRanges.value, v => formatDistance(v), false)
        },
        {
          label: t2('compare.row_wltp_range'),
          cells: wltpRanges.value.map(v => ({ value: v != null ? formatDistance(v) : '–', highlight: 'text-gray-700 dark:text-gray-300', isBest: false, isWorst: false, chip: false, chipClass: '' }))
        }
      ]
    },
    {
      label: t2('compare.section_costs'),
      rows: [
        {
          label: t2('compare.row_costs_per_100'),
          cells: buildCells(costsPerHundred.value, v => formatCostPerDistance(v))
        },
        {
          label: t2('compare.row_costs_per_kwh'),
          cells: buildCells(costsPerKwh.value, v => formatCostPerKwh(v))
        }
      ]
    },
    {
      label: t2('compare.section_seasonal'),
      rows: [
        {
          label: '🌞 ' + t2('compare.row_summer'),
          labelClass: 'text-orange-600 dark:text-orange-400 font-medium',
          cells: buildCells(summerConsumptions.value, v => formatConsumption(v))
        },
        {
          label: '❄️ ' + t2('compare.row_winter'),
          labelClass: 'text-blue-700 dark:text-blue-400 font-medium',
          cells: buildCells(winterConsumptions.value, v => formatConsumption(v))
        }
      ]
    },
    {
      label: t2('compare.section_data'),
      rows: [
        {
          label: t2('compare.row_log_count'),
          cells: buildCells(logCounts.value, v => v.toLocaleString(), false)
        },
        {
          label: t2('compare.row_contributors'),
          cells: validModels.value.map(m => ({ value: m.uniqueContributors > 0 ? String(m.uniqueContributors) : '–', highlight: 'text-gray-700 dark:text-gray-300', isBest: false, isWorst: false, chip: false, chipClass: '' }))
        }
      ]
    }
  ]
})

// ── Highlighting helpers ───────────────────────────────────────────────────────
function bestIdx(values: (number | null)[], lowerIsBetter: boolean): number {
  const valid = values.map((v, i) => ({ v, i })).filter(x => x.v !== null)
  if (valid.length < 2) return -1
  return lowerIsBetter
    ? valid.reduce((a, b) => a.v! < b.v! ? a : b).i
    : valid.reduce((a, b) => a.v! > b.v! ? a : b).i
}

function worstIdx(values: (number | null)[], lowerIsBetter: boolean): number {
  const valid = values.map((v, i) => ({ v, i })).filter(x => x.v !== null)
  if (valid.length < 2) return -1
  return lowerIsBetter
    ? valid.reduce((a, b) => a.v! > b.v! ? a : b).i
    : valid.reduce((a, b) => a.v! < b.v! ? a : b).i
}

function cellClass(i: number, vals: (number | null)[], lowerIsBetter = true): string {
  if (bestIdx(vals, lowerIsBetter) === i) return 'text-green-600 dark:text-green-400 font-bold'
  if (worstIdx(vals, lowerIsBetter) === i) return 'text-red-500 dark:text-red-400'
  return 'text-gray-800 dark:text-gray-200'
}


useHead(computed(() => {
  const names = validModels.value.map(m => m.modelDisplayName).join(' vs. ')
  return {
    title: names ? `${names} – ${t('compare.breadcrumb_current')} | EV Monitor` : t('compare.head_title_fallback'),
    meta: [
      { name: 'description', content: t('compare.head_desc', { names }) },
      { name: 'robots', content: 'index, follow' }
    ]
  }
}))
</script>

<style scoped>
.slide-down-enter-active, .slide-down-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-down-enter-from, .slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
