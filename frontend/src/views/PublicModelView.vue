<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-4xl mx-auto md:px-4 py-6 md:py-8">
      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <!-- 404 state -->
      <div v-else-if="notFound" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('model.not_found_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.not_found_desc') }}</p>
        <a href="/" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          {{ t('model.goto_home') }}
        </a>
      </div>

      <!-- API error state (transient) — no noindex -->
      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('model.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.error_desc') }}</p>
        <button @click="reload" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">
          {{ t('model.reload') }}
        </button>
      </div>

      <!-- Model stats page -->
      <div v-else-if="stats">
        <!-- Breadcrumb -->
        <nav class="px-4 md:px-0 text-sm text-gray-500 dark:text-gray-400 mb-4">
          <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('model.breadcrumb_home') }}</a>
          <span class="mx-2">›</span>
          <a :href="modelsBaseUrl" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('model.breadcrumb_models') }}</a>
          <span class="mx-2">›</span>
          <a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="hover:text-gray-700 dark:hover:text-gray-200">{{ stats.brandDisplayName }}</a>
          <span class="mx-2">›</span>
          <span class="text-gray-900 dark:text-gray-100">{{ stats.modelDisplayName.replace(stats.brandDisplayName + ' ', '') }}</span>
        </nav>

        <!-- Hero -->
        <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 p-6 md:mb-6">
          <a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="inline-flex items-center gap-1 text-sm text-green-600 hover:underline mb-3">
            {{ t('model.back_link', { brand: stats.brandDisplayName }) }}
          </a>
          <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">
            {{ t('model.hero_title', { model: stats.modelDisplayName }) }}
          </h1>
          <p class="text-gray-600 dark:text-gray-400 text-lg">
            {{ t('model.hero_desc') }}
          </p>

          <!-- Key metrics -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
            <!-- Mobile: Verbrauch first, Kosten second, then Ladevorgänge, Reichweite -->
            <!-- Desktop: Ladevorgänge, Reichweite, Verbrauch, Kosten -->
            <div class="bg-purple-50 dark:bg-purple-900/30 rounded-xl p-4 text-center order-1 md:order-3">
              <div class="text-2xl font-bold text-purple-700 dark:text-purple-300">
                {{ displayConsumption ? displayConsumption.toFixed(1) + ' kWh' : '–' }}
              </div>
              <div class="text-sm text-purple-600 dark:text-purple-400 mt-1">{{ t('model.metrics_consumption') }}</div>
            </div>
            <div class="bg-yellow-50 dark:bg-yellow-900/30 rounded-xl p-4 text-center order-2 md:order-4">
              <div class="text-2xl font-bold text-yellow-700 dark:text-yellow-300">
                {{ stats.avgCostPerKwh && stats.avgConsumptionKwhPer100km
                  ? (stats.avgCostPerKwh * stats.avgConsumptionKwhPer100km).toFixed(2) + ' €'
                  : stats.avgCostPerKwh ? (stats.avgCostPerKwh * 100).toFixed(1) + ' ct' : '–' }}
              </div>
              <div class="text-sm text-yellow-600 dark:text-yellow-400 mt-1">{{ t('model.metrics_costs') }}</div>
              <div v-if="stats.avgCostPerKwh" class="text-xs text-yellow-500 dark:text-yellow-500 mt-1">
                {{ (stats.avgCostPerKwh * 100).toFixed(1) }} ct/kWh
              </div>
            </div>
            <div class="bg-green-50 dark:bg-green-900/30 rounded-xl p-4 text-center order-3 md:order-1">
              <div class="text-2xl font-bold text-green-700 dark:text-green-300">
                {{ stats.logCount > 0 ? stats.logCount.toLocaleString() : '–' }}
              </div>
              <div class="text-sm text-green-600 dark:text-green-400 mt-1">{{ t('model.metrics_sessions') }}</div>
              <div v-if="stats.estimatedConsumptionCount > 0" class="text-xs text-green-500 dark:text-green-500 mt-2 italic">
                {{ stats.estimatedConsumptionCount }} {{ t('model.metrics_estimated') }}
              </div>
            </div>
            <div class="bg-blue-50 dark:bg-blue-900/30 rounded-xl p-4 text-center order-4 md:order-2 flex flex-col justify-center">
              <template v-if="displayRange">
                <div class="text-2xl font-bold text-blue-700 dark:text-blue-300">{{ displayRange }} km</div>
                <div class="mt-1">
                  <div class="text-sm font-bold text-blue-600 dark:text-blue-400">{{ t('model.metrics_range') }}</div>
                  <div class="flex items-center justify-center gap-1 mt-2">
                    <Battery0Icon class="h-3.5 w-3.5 text-blue-500 dark:text-blue-400" />
                    <span class="text-xs text-blue-500 dark:text-blue-400">90%→10%</span>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="text-2xl font-bold text-blue-700 dark:text-blue-300">–</div>
                <div class="text-sm text-blue-600 dark:text-blue-400 mt-1">{{ t('model.metrics_range_label') }}</div>
              </template>
            </div>
          </div>

          <!-- No data yet notice -->
          <div v-if="stats.logCount === 0" class="mt-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-xl border border-gray-200 dark:border-gray-600">
            <p class="text-gray-600 dark:text-gray-300 text-sm">
              {{ t('common.be_first') }}
              <a :href="registerPath" class="text-green-600 font-medium hover:underline">{{ t('common.register') }}</a>
            </p>
          </div>

          <!-- Kostenrechner -->
          <div v-if="displayConsumption" class="mt-6 pt-5 border-t border-gray-100 dark:border-gray-700">
            <!-- Label + Ergebnis -->
            <div class="flex items-center justify-center gap-2 mb-3 flex-wrap">
              <BoltIcon class="h-4 w-4 text-yellow-500 shrink-0" />
              <span class="text-sm text-gray-500 dark:text-gray-400">
                {{ t('model.calculator_label') }} {{ Math.round(pricePerKwh * 100) }} {{ t('model.calculator_unit') }}
              </span>
              <span class="text-sm text-gray-300 dark:text-gray-600">~</span>
              <span class="text-lg font-bold text-yellow-600 dark:text-yellow-400">
                {{ (pricePerKwh * displayConsumption).toFixed(2) }} {{ t('model.calculator_result') }}
              </span>
            </div>
            <!-- Slider -->
            <div class="flex items-center gap-3">
              <span class="text-xs text-gray-400 shrink-0">0,10 €</span>
              <input
                type="range"
                min="0.10"
                max="1.00"
                step="0.01"
                v-model.number="pricePerKwh"
                class="flex-1 h-2 bg-gray-200 dark:bg-gray-700 rounded-full appearance-none cursor-pointer accent-yellow-500"
              />
              <span class="text-xs text-gray-400 shrink-0">1,00 €</span>
            </div>
          </div>

        </div>

        <!-- Affiliate Banner: nur für nicht eingeloggte User -->
        <AffiliateBanner v-if="!authStore.isAuthenticated()" />

        <!-- Combined: Variant Switcher + Seasonal + WLTP -->
        <div v-if="stats.wltpVariants.length > 0 || showSeasonalBreakdown"
             class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 md:mb-6 overflow-hidden">

          <!-- Variant Switcher Header -->
          <div v-if="stats.wltpVariants.length > 1" class="px-6 py-4 border-b border-gray-100 dark:border-gray-700">
            <div class="flex items-center gap-3 flex-wrap">
              <span class="text-sm font-medium text-gray-500 dark:text-gray-400 whitespace-nowrap">{{ t('model.variant_title') }}</span>
              <div class="relative flex bg-gray-100 dark:bg-gray-700 rounded-full p-1 w-fit">
                <div
                  class="absolute top-1 bottom-1 left-1 rounded-full bg-blue-600 shadow-sm transition-transform duration-300 ease-in-out"
                  :style="{
                    width: `calc((100% - 8px) / ${stats.wltpVariants.length})`,
                    transform: `translateX(calc(${selectedVariantIndex * 100}%))`
                  }"
                />
                <button
                  v-for="(v, i) in stats.wltpVariants"
                  :key="v.batteryCapacityKwh"
                  @click="selectedVariantIndex = i"
                  class="relative flex-1 text-center px-4 text-sm font-medium py-1.5 rounded-full transition-colors duration-300 z-10 whitespace-nowrap"
                  :class="i === selectedVariantIndex ? 'text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                >
                  {{ v.batteryCapacityKwh }} kWh
                </button>
              </div>
              <span v-if="selectedVariant?.seasonalDistribution && (selectedVariant.seasonalDistribution.summerLogCount < 30 || selectedVariant.seasonalDistribution.winterLogCount < 30)"
                    class="flex items-center gap-1 text-xs text-yellow-600 font-medium whitespace-nowrap">
                <ExclamationTriangleIcon class="h-3.5 w-3.5" />
                {{ t('model.variant_low_trips') }}
              </span>
            </div>
          </div>

          <!-- Seasonal Consumption Breakdown -->
          <div v-if="showSeasonalBreakdown" class="px-6 py-5 bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 border-b border-blue-100 dark:border-blue-900/50">
          <div class="flex-1">
            <p class="text-sm font-bold text-blue-900 dark:text-blue-300 mb-3 flex items-center gap-1.5">
              <ChartBarIcon class="h-4 w-4" />
              {{ t('model.seasonal_title') }}
            </p>

            <!-- Summer Stats -->
            <div class="flex items-center justify-between mb-2 text-sm">
              <div class="flex items-center gap-2">
                <span class="text-orange-600 font-medium">🌞 {{ t('model.seasonal_summer') }} <span class="block md:inline"> {{ t('model.seasonal_summer_months') }}</span></span>
              </div>
              <div class="flex items-center gap-3">
                <template v-if="selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km">
                  <span class="font-bold text-orange-600">
                    {{ selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                  </span>
                  <span v-if="selectedVariant" class="text-base font-bold text-orange-600 whitespace-nowrap">
                    ~ {{ Math.round(selectedVariant.batteryCapacityKwh * 0.8 / selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km * 10) * 10 }} km
                  </span>
                </template>
                <span v-else class="text-gray-400 text-xs">—</span>
                <span class="hidden md:inline text-xs"
                      :class="selectedVariant!.seasonalDistribution!.summerLogCount < 30 ? 'text-yellow-600 font-medium' : 'text-gray-500'">
                  <ExclamationTriangleIcon v-if="selectedVariant!.seasonalDistribution!.summerLogCount < 30" class="h-3 w-3 inline -mt-0.5 mr-0.5" />
                  ({{ selectedVariant!.seasonalDistribution!.summerLogCount }} {{ t('model.seasonal_trips') }})
                </span>
              </div>
            </div>

            <!-- Winter Stats -->
            <div class="flex items-center justify-between mb-3 text-sm">
              <div class="flex items-center gap-2">
                <span class="text-blue-700 font-medium">❄️ {{ t('model.seasonal_winter') }} <span class="block md:inline"> {{ t('model.seasonal_winter_months') }}</span></span>
              </div>
              <div class="flex items-center gap-3">
                <template v-if="selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km">
                  <span class="font-bold text-blue-700">
                    {{ selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                  </span>
                  <span v-if="selectedVariant" class="text-base font-bold text-blue-700 whitespace-nowrap">
                    ~ {{ Math.round(selectedVariant.batteryCapacityKwh * 0.8 / selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km * 10) * 10 }} km
                  </span>
                </template>
                <span v-else class="text-gray-400 text-xs">—</span>
                <span class="hidden md:inline text-xs"
                      :class="selectedVariant!.seasonalDistribution!.winterLogCount < 30 ? 'text-yellow-600 font-medium' : 'text-gray-500'">
                  <ExclamationTriangleIcon v-if="selectedVariant!.seasonalDistribution!.winterLogCount < 30" class="h-3 w-3 inline -mt-0.5 mr-0.5" />
                  ({{ selectedVariant!.seasonalDistribution!.winterLogCount }} {{ t('model.seasonal_trips') }})
                </span>
              </div>
            </div>

            <!-- Weighted Average (Overall) -->
            <div class="pt-2 border-t border-blue-200 dark:border-blue-900/50">
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-700 dark:text-gray-300 font-medium">{{ t('model.seasonal_weighted_avg') }}</span>
                <span class="font-bold text-gray-900 dark:text-gray-100">
                  {{ selectedVariant!.seasonalDistribution!.totalConsumptionKwhPer100km != null ? selectedVariant!.seasonalDistribution!.totalConsumptionKwhPer100km.toFixed(1) + ' kWh/100km' : '—' }}
                </span>
              </div>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {{ t('model.seasonal_split', { summer: selectedVariant!.seasonalDistribution!.summerPercentage, winter: selectedVariant!.seasonalDistribution!.winterPercentage }) }}
              </p>
            </div>
          </div>
          </div><!-- end seasonal section -->

          <!-- WLTP vs Real Comparison (inside combined card) -->
          <div v-if="stats.wltpVariants.length > 0">
          <h2 class="text-base font-semibold text-gray-700 dark:text-gray-300 px-6 pt-5 pb-3 flex items-center gap-2">
            <ClipboardDocumentListIcon class="h-5 w-5 text-gray-500 dark:text-gray-400" />
            {{ t('model.wltp_section_title') }}
          </h2>
          <!-- Mobile: Card (selected variant only) -->
          <div class="md:hidden">
            <div v-if="selectedVariant" class="px-6 pb-4">
              <div class="relative flex items-center justify-center mb-3">
                <div class="font-semibold text-gray-900 dark:text-gray-100">{{ selectedVariant.batteryCapacityKwh }} kWh</div>
                <span v-if="!selectedVariant.realConsumptionKwhPer100km"
                      class="absolute right-0 inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-500 dark:text-gray-400">
                  {{ t('model.wltp_no_trips') }}
                </span>
                <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 10"
                      class="absolute right-0 inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-red-50 border border-red-200 text-red-600 dark:bg-red-900/40 dark:border-red-700 dark:text-red-400">
                  ⚠ {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                </span>
                <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 50"
                      class="absolute right-0 inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-yellow-50 border border-yellow-200 text-yellow-700 dark:bg-yellow-900/40 dark:border-yellow-700 dark:text-yellow-400">
                  {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                </span>
                <span v-else-if="selectedVariant.realConsumptionTripCount != null"
                      class="absolute right-0 inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-green-50 border border-green-200 text-green-700 dark:bg-green-900/40 dark:border-green-700 dark:text-green-400">
                  {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                </span>
              </div>
              <div class="grid grid-cols-2 gap-2 text-sm">
                <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                  <div class="text-xs text-gray-500 dark:text-gray-400 mb-0.5">{{ t('model.wltp_wltp_range') }}</div>
                  <div class="font-medium text-gray-800 dark:text-gray-200">{{ selectedVariant.wltpRangeKm }} km</div>
                </div>
                <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                  <div class="text-xs text-gray-500 dark:text-gray-400 mb-0.5">{{ t('model.wltp_wltp_consumption') }}</div>
                  <div class="font-medium text-gray-800 dark:text-gray-200">{{ selectedVariant.wltpConsumptionKwhPer100km }} kWh/100km</div>
                </div>
                <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                  <div class="text-xs text-gray-500 dark:text-gray-400 mb-0.5">{{ t('model.wltp_real_range') }}</div>
                  <div class="font-medium text-gray-800 dark:text-gray-200">
                    {{ selectedVariant.realConsumptionKwhPer100km ? Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.realConsumptionKwhPer100km * 10) * 10 + ' km' : '–' }}
                  </div>
                  <div class="text-xs text-gray-400 dark:text-gray-500">{{ t('model.wltp_full_range') }}</div>
                </div>
                <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                  <div class="text-xs text-gray-500 dark:text-gray-400 mb-0.5">{{ t('model.wltp_real_consumption') }}</div>
                  <template v-if="selectedVariant.realConsumptionKwhPer100km">
                    <div :class="consumptionDeltaClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km)" class="font-medium">
                      {{ selectedVariant.realConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                    </div>
                    <span :class="deltaLabelClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km)"
                          class="text-xs px-1.5 py-0.5 rounded-full mt-1 inline-block">
                      {{ deltaLabel(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km) }}
                    </span>
                  </template>
                  <div v-else class="text-gray-400 text-sm">–</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Desktop: Table (selected variant only) -->
          <div class="hidden md:block overflow-x-auto px-6 pb-2">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-gray-500 dark:text-gray-400 border-b border-gray-100 dark:border-gray-700">
                  <th class="pb-3 pr-4 font-medium whitespace-nowrap">{{ t('model.wltp_table_battery') }}</th>
                  <th class="pb-3 pr-4 font-medium whitespace-nowrap">{{ t('model.wltp_table_range') }}</th>
                  <th class="pb-3 pr-4 font-medium whitespace-nowrap">
                    <div>{{ t('model.wltp_table_real_range') }} <span class="text-xs">{{ t('model.wltp_table_full_to_empty') }}</span></div>
                  </th>
                  <th class="pb-3 pr-4 font-medium whitespace-nowrap">{{ t('model.wltp_table_consumption') }}</th>
                  <th class="pb-3 font-medium whitespace-nowrap">{{ t('model.wltp_table_real_consumption') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="selectedVariant" class="border-b border-gray-50 dark:border-gray-700">
                  <td class="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100 whitespace-nowrap">{{ selectedVariant.batteryCapacityKwh }} kWh</td>
                  <td class="py-3 pr-4 text-gray-700 dark:text-gray-300 whitespace-nowrap">{{ selectedVariant.wltpRangeKm }} km</td>
                  <td class="py-3 pr-4 whitespace-nowrap">
                    <div v-if="selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km || selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km"
                         class="flex items-center gap-1.5">
                      <span class="flex items-center gap-1 text-amber-600">
                        <SunIcon class="h-3.5 w-3.5" />
                        <span>{{ selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km ? Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km * 10) * 10 + ' km' : '–' }}</span>
                      </span>
                      <span class="text-gray-300">/</span>
                      <span class="flex items-center gap-1 text-blue-500">
                        <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="12" y1="2" x2="12" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/><line x1="19.07" y1="4.93" x2="4.93" y2="19.07"/><circle cx="12" cy="12" r="2" fill="currentColor"/></svg>
                        <span>{{ selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km ? Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km * 10) * 10 + ' km' : '–' }}</span>
                      </span>
                    </div>
                    <span v-else class="text-gray-400">–</span>
                  </td>
                  <td class="py-3 pr-4 text-gray-700 dark:text-gray-300 whitespace-nowrap">{{ selectedVariant.wltpConsumptionKwhPer100km }} kWh/100km</td>
                  <td class="py-3 align-top">
                    <div v-if="selectedVariant.realConsumptionKwhPer100km" class="flex flex-col items-start gap-1">
                      <span :class="consumptionDeltaClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km)"
                            class="font-medium whitespace-nowrap">
                        {{ selectedVariant.realConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                      </span>
                      <div class="flex items-center gap-1.5">
                        <span :class="deltaLabelClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km)"
                              class="text-xs px-1.5 py-0.5 rounded-full">
                          {{ deltaLabel(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km) }}
                        </span>
                        <span v-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 10"
                              class="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-red-50 border border-red-200 text-red-600 dark:bg-red-900/40 dark:border-red-700 dark:text-red-400">
                          ⚠ {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                        </span>
                        <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 50"
                              class="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full bg-yellow-50 border border-yellow-200 text-yellow-700 dark:bg-yellow-900/40 dark:border-yellow-700 dark:text-yellow-400">
                          {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                        </span>
                      </div>
                    </div>
                    <span v-else class="text-gray-400">{{ t('model.wltp_no_data') }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="px-6 py-4 space-y-2">
            <span class="inline-block text-xs bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 px-2.5 py-1 rounded-full">
              {{ t('model.wltp_note') }}
            </span>
            <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('model.wltp_measurement_note') }}</p>
          </div>
          </div><!-- end WLTP section -->
        </div><!-- end combined card -->

        <!-- What is EV Monitor / CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 md:rounded-2xl p-6 text-white">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="text-xl font-bold">{{ t('model.cta_title') }}</h2>
          </div>
          <p class="text-green-100 mb-4">
            {{ t('model.cta_desc') }}
          </p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath"
               class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors">
              {{ t('model.cta_free_start') }}
            </a>
            <a :href="loginPath"
               class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors">
              {{ t('model.cta_login') }}
            </a>
          </div>
        </div>

        <!-- SEO rich text section -->
        <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-y border-gray-200 dark:border-gray-700 p-6 md:mt-6">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('model.seo_section_title', { model: stats.modelDisplayName }) }}
          </h2>

          <div class="space-y-4 text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
            <p>
              Der <strong>{{ stats.modelDisplayName }}</strong> ist ein Elektrofahrzeug
              <template v-if="bestWltpRange">mit einer offiziellen WLTP-Reichweite von bis zu <strong>{{ bestWltpRange }} km</strong></template>.
              <template v-if="stats.avgConsumptionKwhPer100km">
                <template v-if="consumptionDataQuality === 'good'">
                  Laut realen Messdaten von EV Monitor Nutzern liegt der Durchschnittsverbrauch
                  bei <strong>{{ stats.avgConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong> –
                  ermittelt aus <strong>{{ stats.logCount }} Ladevorgängen</strong>
                  ({{ Math.min(consumptionDataCount, stats.logCount) }} davon mit Verbrauchsmessung).
                </template>
                <template v-else-if="consumptionDataQuality === 'low'">
                  Laut bisherigen Nutzerdaten liegt der Verbrauch bei
                  <strong>{{ stats.avgConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong> –
                  basierend auf <strong>{{ Math.min(consumptionDataCount, stats.logCount) }} Fahrten mit Verbrauchsmessung</strong>.
                  Der Wert wird repräsentativer, je mehr Fahrer ihre Daten beitragen.
                </template>
                <template v-else>
                  Erste Messwerte liegen vor: <strong>{{ stats.avgConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong>
                  aus <strong>{{ Math.min(consumptionDataCount, stats.logCount) }} Fahrten mit Verbrauchsdaten</strong> –
                  noch zu wenig für eine belastbare Aussage. Je mehr Fahrer beitragen, desto genauer wird der Wert.
                </template>
              </template>
              <template v-else>
                Sei der Erste, der Ladevorgänge für diesen {{ stats.modelDisplayName }} einträgt und der Community hilft!
              </template>
            </p>

            <div v-if="stats.avgCostPerKwh">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">Ladekosten im Alltag</h3>
              <p>
                Der durchschnittliche Ladepreis für den {{ stats.modelDisplayName }} beträgt laut Nutzerdaten
                <strong>{{ (stats.avgCostPerKwh * 100).toFixed(1) }} Cent pro kWh</strong>.
                <template v-if="stats.avgKwhPerSession">
                  Eine typische Ladeeinheit von {{ stats.avgKwhPerSession.toFixed(1) }} kWh kostet damit
                  rund <strong>{{ (stats.avgCostPerKwh * stats.avgKwhPerSession).toFixed(2) }} €</strong>.
                </template>
                Zu Hause an der Wallbox fallen geringere Kosten an (ca. 25–35 ct/kWh) als an öffentlichen
                Schnellladesäulen (35–60 ct/kWh).
              </p>
            </div>

            <div v-if="stats.wltpVariants.length > 0 && consumptionDataCount >= 25">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">WLTP vs. realer Verbrauch</h3>
              <p>
                Der WLTP-Zyklus wird unter standardisierten Laborbedingungen gemessen und weicht im Alltag
                häufig ab. Faktoren wie Autobahnfahrten, Heizung, Klimaanlage und Fahrstil erhöhen den
                tatsächlichen Energieverbrauch des {{ stats.modelDisplayName }}.
                <template v-if="stats.avgConsumptionKwhPer100km && worstWltpConsumption">
                  Im Community-Durchschnitt liegt der Realverbrauch
                  <strong>{{ wltpDeltaPercent }}</strong>
                  gegenüber dem WLTP-Wert.
                </template>
              </p>
            </div>

            <div>
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">Verbrauch im Winter und Sommer</h3>
              <p v-if="showSeasonalBreakdown">
                <template v-if="selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km && selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km">
                  Laut Community-Daten ({{ selectedVariant.seasonalDistribution.summerLogCount + selectedVariant.seasonalDistribution.winterLogCount }} Fahrten)
                  verbraucht der {{ stats.modelDisplayName }} im Sommer (Mai–August)
                  <strong>{{ selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong>
                  und im Winter (November–Februar)
                  <strong>{{ selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km.toFixed(1) }} kWh/100km</strong> –
                  das entspricht einem Mehrverbrauch von
                  <strong>{{ Math.round((selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km / selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km - 1) * 100) }}%</strong>
                  im Winter durch Kabinenheizung und reduzierte Batterieeffizienz bei Kälte.
                  Vorheizen des Fahrzeugs beim Laden schont die Reichweite erheblich.
                </template>
                <template v-else>
                  Wie alle Elektroautos zeigt der {{ stats.modelDisplayName }} saisonale Verbrauchsschwankungen.
                  Im Winter (November bis Februar) steigt der Verbrauch durch Kabinenheizung und reduzierte Batterieeffizienz,
                  im Sommer (Mai bis August) wird die beste Effizienz erreicht.
                  Vorheizen des Fahrzeugs beim Laden schont die Reichweite erheblich.
                </template>
              </p>
              <p v-else>
                Für den {{ stats.modelDisplayName }} liegen noch nicht genug saisonale Daten vor, um Winter- und
                Sommerverbrauch zuverlässig zu trennen. Allgemein gilt bei Elektroautos: Im Winter (Frost, Heizung)
                steigt der Verbrauch typischerweise um <strong>15–30%</strong>, im Sommer wird die beste Effizienz
                erreicht. Vorheizen beim Laden spart Reichweite.
              </p>
            </div>
          </div>
        </div>

        <!-- FAQ Section -->
        <div v-if="faqItems.length > 0" class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-y border-gray-200 dark:border-gray-700 p-6 mt-6">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('model.faq_title', { model: stats.modelDisplayName }) }}
          </h2>
          <div class="space-y-3">
            <details v-for="(faq, i) in faqItems" :key="i"
                     class="border border-gray-100 dark:border-gray-700 rounded-xl overflow-hidden">
              <summary class="flex items-center justify-between p-4 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700
                              font-medium text-gray-900 dark:text-gray-100 text-sm list-none">
                {{ faq.question }}
                <span class="text-gray-400 ml-2 flex-shrink-0">﹀</span>
              </summary>
              <div class="px-4 pb-4 pt-1 text-sm text-gray-600 dark:text-gray-400 leading-relaxed border-t border-gray-100 dark:border-gray-700">
                {{ faq.answer }}
              </div>
            </details>
          </div>
        </div>
      </div>
    </main>

    <!-- Internal linking: popular models -->
    <div class="max-w-4xl mx-auto md:px-4 mt-8">
      <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-y border-gray-200 dark:border-gray-700 p-6">
        <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 mb-3">{{ t('model.related_title') }}</h2>
        <div class="flex flex-wrap gap-2 text-sm">
          <a :href="`${modelsBaseUrl}/Tesla/Model_3`" class="text-green-600 hover:underline">Tesla Model 3</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Tesla/Model_Y`" class="text-green-600 hover:underline">Tesla Model Y</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Volkswagen/ID.3`" class="text-green-600 hover:underline">VW ID.3</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Volkswagen/ID.4`" class="text-green-600 hover:underline">VW ID.4</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Hyundai/Ioniq_5`" class="text-green-600 hover:underline">Hyundai Ioniq 5</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Hyundai/Ioniq_6`" class="text-green-600 hover:underline">Hyundai Ioniq 6</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Kia/EV6`" class="text-green-600 hover:underline">Kia EV6</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/BMW/i4`" class="text-green-600 hover:underline">BMW i4</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Audi/Q4_e-tron`" class="text-green-600 hover:underline">Audi Q4 e-tron</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Polestar/Polestar_2`" class="text-green-600 hover:underline">Polestar 2</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Renault/Zoe`" class="text-green-600 hover:underline">Renault Zoe</a>
          <span class="text-gray-300">·</span>
          <a :href="`${modelsBaseUrl}/Nissan/Leaf`" class="text-green-600 hover:underline">Nissan Leaf</a>
        </div>
      </div>
    </div>

    <footer class="max-w-4xl mx-auto px-4 py-8 mt-6 border-t border-gray-200 dark:border-gray-700 text-sm text-gray-500 dark:text-gray-400 text-center">
      © {{ currentYear }} EV Monitor ·
      <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">{{ isAuthenticated ? t('nav.dashboard') : t('common.home') }}</a>
      <template v-if="!isAuthenticated">
        ·
        <a :href="registerPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.free_start') }}</a> ·
        <a :href="loginPath" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('common.login') }}</a>
      </template>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { getModelStats, type PublicModelStats } from '../api/publicModelService'
import { ArrowTrendingUpIcon, ClipboardDocumentListIcon, Battery0Icon, SunIcon, ChartBarIcon, ExclamationTriangleIcon, BoltIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'
import AffiliateBanner from '../components/AffiliateBanner.vue'

const route = useRoute()
const { t } = useI18n()
const isEn = computed(() => route.path.startsWith('/en/'))
const modelsBaseUrl = computed(() => isEn.value ? '/en/models' : '/modelle')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const notFound = ref(false)   // true only on genuine 404 — triggers noindex
const apiError = ref(false)   // true on transient errors — keeps robots: index, follow
const stats = ref<PublicModelStats | null>(null)
const selectedVariantIndex = ref(0)
const pricePerKwh = ref(0.35)

const isAuthenticated = computed(() => authStore.isAuthenticated())

const brand = route.params.brand as string
const model = route.params.model as string

// Canonical URL computed from response data (not raw params — avoids TESLA/Tesla dupe issues)
const canonicalBrand = computed(() => stats.value?.brandDisplayName ?? brand)
const canonicalModelSlug = computed(() => {
  if (!stats.value) return model
  return stats.value.modelDisplayName.replace(stats.value.brandDisplayName + ' ', '').replace(/ /g, '_')
})

const selectedVariant = computed(() =>
  stats.value?.wltpVariants[selectedVariantIndex.value] ?? null
)

const displayConsumption = computed(() =>
  selectedVariant.value?.realConsumptionKwhPer100km
    ?? stats.value?.avgConsumptionKwhPer100km
    ?? null
)

const displayRange = computed(() => {
  if (!selectedVariant.value || !displayConsumption.value) return null
  return Math.round(selectedVariant.value.batteryCapacityKwh * 0.8 / displayConsumption.value * 10) * 10
})

const bestWltpRange = computed(() => {
  if (!stats.value?.wltpVariants.length) return null
  return Math.max(...stats.value.wltpVariants.map(v => v.wltpRangeKm))
})

const wltpDeltaPercent = computed(() => {
  if (!stats.value?.avgConsumptionKwhPer100km || !worstWltpConsumption.value) return null
  const pct = ((stats.value.avgConsumptionKwhPer100km - worstWltpConsumption.value) / worstWltpConsumption.value) * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(0)}%`
})

const worstWltpConsumption = computed(() => {
  if (!stats.value?.wltpVariants.length) return null
  return Math.max(...stats.value.wltpVariants.map(v => v.wltpConsumptionKwhPer100km))
})

// Number of logs that actually contributed to consumption calculation
const consumptionDataCount = computed(() => {
  if (!stats.value) return 0
  const socCount = stats.value.wltpVariants.reduce((sum, v) => sum + (v.realConsumptionTripCount ?? 0), 0)
  return socCount + (stats.value.estimatedConsumptionCount ?? 0)
})

// 'good' >= 100, 'low' 50-99, 'scarce' < 50
const consumptionDataQuality = computed((): 'good' | 'low' | 'scarce' => {
  const n = consumptionDataCount.value
  if (n >= 100) return 'good'
  if (n >= 50) return 'low'
  return 'scarce'
})


const showSeasonalBreakdown = computed(() => {
  const s = selectedVariant.value?.seasonalDistribution
  if (!s) return false
  return s.winterLogCount >= 10 && s.summerLogCount >= 10 && s.winterLogCount + s.summerLogCount > 10
})


const faqItems = computed(() => {
  if (!stats.value) return []
  const name = stats.value.modelDisplayName
  const items: { question: string; answer: string }[] = []

  // Q1: realer Verbrauch
  if (stats.value.avgConsumptionKwhPer100km) {
    const n = consumptionDataCount.value
    const quality = consumptionDataQuality.value
    const dataNote = quality === 'good'
      ? `basierend auf ${n} Fahrten mit Verbrauchsmessung`
      : quality === 'low'
        ? `basierend auf ${n} Fahrten mit Verbrauchsmessung – Wert wird mit mehr Daten genauer`
        : `erst ${n} Fahrten mit Verbrauchsdaten – noch nicht repräsentativ`
    items.push({
      question: `Wie hoch ist der reale Verbrauch des ${name}?`,
      answer: `Laut EV Monitor Nutzerdaten liegt der Verbrauch des ${name} bei ${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km (${dataNote}). Der offizielle WLTP-Wert beträgt ${worstWltpConsumption.value?.toFixed(1) ?? '–'} kWh/100km. Im Winter kann der Verbrauch durch Heizung und kältere Batterien 15–30% höher ausfallen.`
    })
  }

  // Q2: echte Reichweite
  if (bestWltpRange.value && stats.value.avgConsumptionKwhPer100km && stats.value.wltpVariants.length > 0) {
    const largestBattery = Math.max(...stats.value.wltpVariants.map(v => v.batteryCapacityKwh))
    const realRange = Math.round(largestBattery / stats.value.avgConsumptionKwhPer100km * 100)
    items.push({
      question: `Wie weit kommt der ${name} wirklich?`,
      answer: `Der WLTP-Wert des ${name} beträgt bis zu ${bestWltpRange.value} km. In der Praxis erreichen Fahrer mit der ${largestBattery} kWh Batterie bei einem realen Verbrauch von ${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km etwa ${realRange} km. Im Winter (Frost, Heizung) kann die Reichweite auf 60–70% des WLTP-Wertes sinken.`
    })
  }

  // Q3: Ladekosten
  if (stats.value.avgCostPerKwh && stats.value.avgKwhPerSession) {
    const costPerSession = (stats.value.avgCostPerKwh * stats.value.avgKwhPerSession).toFixed(2)
    items.push({
      question: `Was kostet eine Ladung des ${name}?`,
      answer: `Im Durchschnitt kostet eine Ladeeinheit ${(stats.value.avgCostPerKwh * 100).toFixed(1)} Cent pro kWh. Bei einer typischen Ladung von ${stats.value.avgKwhPerSession.toFixed(1)} kWh entstehen Kosten von etwa ${costPerSession} €. Zu Hause an der Wallbox ist Laden deutlich günstiger als an öffentlichen Schnellladern - die Preise variieren je nach Region, Anbieter und Vertrag erheblich.`
    })
  }

  // Q4: WLTP vs Real — nur bei ausreichender Datenbasis
  if (worstWltpConsumption.value && stats.value.avgConsumptionKwhPer100km && consumptionDataCount.value >= 25) {
    const diff = (stats.value.avgConsumptionKwhPer100km - worstWltpConsumption.value).toFixed(1)
    const pct = Math.round((stats.value.avgConsumptionKwhPer100km / worstWltpConsumption.value - 1) * 100)
    items.push({
      question: `Wie groß ist der Unterschied zwischen WLTP und realem Verbrauch beim ${name}?`,
      answer: `Der offizielle WLTP-Verbrauch des ${name} liegt bei ${worstWltpConsumption.value.toFixed(1)} kWh/100km. Laut Community-Daten verbrauchen Fahrer im Alltag ${stats.value.avgConsumptionKwhPer100km.toFixed(1)} kWh/100km – das sind ${diff} kWh mehr (+${pct}%). WLTP-Werte werden unter idealisierten Testbedingungen ermittelt und weichen im Alltag durch Autobahnfahrten, Heizung und Klimaanlage ab.`
    })
  }

  // Q5: Winter
  const seasonal = selectedVariant.value?.seasonalDistribution
  const hasSeasonalData = seasonal && seasonal.winterLogCount >= 10 && seasonal.summerLogCount >= 10
  items.push({
    question: `Wie verändert sich der Verbrauch des ${name} im Winter?`,
    answer: hasSeasonalData
      ? `Laut Community-Daten verbraucht der ${name} im Winter (Nov–Feb) ${seasonal!.winterConsumptionKwhPer100km?.toFixed(1) ?? '–'} kWh/100km und im Sommer (Mai–Aug) ${seasonal!.summerConsumptionKwhPer100km?.toFixed(1) ?? '–'} kWh/100km. Ursachen für den Winteranstieg sind Kabinenheizung, reduzierte Zelleffizienz bei Kälte und Batterie-Vorwärmung. Vorheizen beim Laden (per App) minimiert die Reichweitenverluste.`
      : `Für den ${name} liegen noch nicht genug saisonale Messdaten vor. Allgemein steigt der Verbrauch von Elektroautos im Winter typischerweise um 15–30% – durch Heizung, kältere Batterien und schlechtere Rekuperation. Vorheizen beim Laden spart Reichweite.`
  })

  return items
})

const brandDisplay = computed(() => toTitleCase(brand))
const currentYear = new Date().getFullYear()

// Dynamic SEO meta tags
useHead(computed(() => {
  const base = 'https://ev-monitor.net'
  const enBase = `${base}/en/models`
  const deBase = `${base}/modelle`
  if (notFound.value) {
    return {
      title: 'Modell nicht gefunden – EV Monitor',
      meta: [{ name: 'robots', content: 'noindex, nofollow' }]
    }
  }
  if (!stats.value) {
    const path = isEn.value ? `${enBase}/${brand}/${model}` : `${deBase}/${brand}/${model}`
    return {
      title: 'EV Monitor',
      meta: [{ name: 'robots', content: 'index, follow' }],
      link: [{ rel: 'canonical', href: path }]
    }
  }
  const name = stats.value.modelDisplayName
  const realConsumption = stats.value.avgConsumptionKwhPer100km
  const wltpConsumption = worstWltpConsumption.value
  const range = bestWltpRange.value ? `${bestWltpRange.value} km` : null
  const logCount = stats.value.logCount

  // Title: Zahlen direkt rein wenn vorhanden
  const titleConsumption = realConsumption && wltpConsumption
    ? `${realConsumption.toFixed(1)} kWh/100km real vs. ${wltpConsumption.toFixed(1)} kWh WLTP`
    : realConsumption
      ? `${realConsumption.toFixed(1)} kWh/100km Realverbrauch`
      : null

  // Description: Frage-Format + Delta als Hook
  const descParts = [`Wie viel verbraucht der ${name} wirklich? `]
  if (realConsumption && wltpConsumption) {
    const delta = wltpDeltaPercent.value
    descParts.push(`Community-Durchschnitt aus ${logCount} Ladevorgängen: ${realConsumption.toFixed(1)} kWh/100km`)
    if (delta) descParts.push(` (${delta} gegenüber WLTP)`)
    descParts.push(`. `)
  } else if (realConsumption) {
    descParts.push(`Community-Durchschnitt aus ${logCount} Ladevorgängen: ${realConsumption.toFixed(1)} kWh/100km. `)
  }
  if (range) descParts.push(`WLTP-Reichweite bis zu ${range}. `)
  if (!realConsumption) descParts.push(`Echte Fahrerdaten statt Marketing-Versprechen. `)
  descParts.push(`Kostenlos vergleichen & eigene Daten eintragen.`)
  const description = descParts.join('').trim()

  const keywords = [
    name,
    `${name} Verbrauch`,
    `${name} kWh pro 100km`,
    `${name} Reichweite`,
    `${name} WLTP`,
    `${name} realer Verbrauch`,
    `${name} Ladekosten`,
    `${name} laden`,
    `${name} Praxisverbrauch`,
    `${name} Winterverbrauch`,
    `${name} Sommerverbrauch`,
    `${brandDisplay.value} Elektroauto`,
    'Elektroauto Verbrauch',
    'EV kWh 100km',
    'WLTP Unterschied real',
    'Elektroauto Ladekosten',
    'EV Reichweite Winter',
    'Ladetagebuch Elektroauto',
  ].join(', ')

  const faqJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: faqItems.value.map(f => ({
      '@type': 'Question',
      name: f.question,
      acceptedAnswer: { '@type': 'Answer', text: f.answer }
    }))
  }

  const articleJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'WebPage',
    name: `${name} Verbrauch & Reichweite – Realer WLTP Vergleich`,
    description,
    author: { '@type': 'Organization', name: 'EV Monitor' },
    publisher: { '@type': 'Organization', name: 'EV Monitor', url: 'https://ev-monitor.net' },
    url: `https://ev-monitor.net/modelle/${canonicalBrand.value}/${canonicalModelSlug.value}`,
    ...(stats.value.wltpVariants.length > 0 && {
      about: stats.value.wltpVariants.map(v => ({
        '@type': 'PropertyValue',
        name: `WLTP Reichweite (${v.batteryCapacityKwh} kWh)`,
        value: `${v.wltpRangeKm} km`
      }))
    })
  }

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      {
        '@type': 'ListItem',
        position: 1,
        name: 'EV Monitor',
        item: 'https://ev-monitor.net'
      },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Modelle',
        item: 'https://ev-monitor.net/modelle'
      },
      {
        '@type': 'ListItem',
        position: 3,
        name: `${stats.value.brandDisplayName} Elektroautos`,
        item: `https://ev-monitor.net/modelle/${canonicalBrand.value}`
      },
      {
        '@type': 'ListItem',
        position: 4,
        name: name
      }
    ]
  }

  const dePath = `${deBase}/${canonicalBrand.value}/${canonicalModelSlug.value}`
  const enPath = `${enBase}/${canonicalBrand.value}/${canonicalModelSlug.value}`
  const canonicalHref = isEn.value ? enPath : dePath
  const ogLocale = isEn.value ? 'en_GB' : 'de_DE'

  return {
    title: titleConsumption
      ? `${name}: ${titleConsumption} | EV Monitor`
      : `${name} Verbrauch & Reichweite (${currentYear}) – EV Monitor`,
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: keywords },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: titleConsumption ? `${name}: ${titleConsumption}` : `${name} – Realer Verbrauch & WLTP Vergleich ${currentYear}` },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: canonicalHref },
      { property: 'og:locale', content: ogLocale },
    ],
    link: [
      { rel: 'canonical', href: canonicalHref },
      { rel: 'alternate', hreflang: 'de', href: dePath },
      { rel: 'alternate', hreflang: 'en', href: enPath },
      { rel: 'alternate', hreflang: 'x-default', href: dePath },
    ],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(articleJsonLd) },
      ...(faqItems.value.length > 0
        ? [{ type: 'application/ld+json', innerHTML: JSON.stringify(faqJsonLd) }]
        : [])
    ]
  }
}))

onMounted(async () => {
  try {
    const data = await getModelStats(brand, model)
    if (!data) {
      notFound.value = true
    } else {
      stats.value = data
      // Default to variant with most real consumption data
      if (data.wltpVariants.length > 1) {
        let maxTrips = -1
        data.wltpVariants.forEach((v, i) => {
          if ((v.realConsumptionTripCount ?? 0) > maxTrips) {
            maxTrips = v.realConsumptionTripCount ?? 0
            selectedVariantIndex.value = i
          }
        })
      }
      const modelSlug = data.modelDisplayName.replace(data.brandDisplayName + ' ', '').replace(/ /g, '_')
      const base = isEn.value ? '/en/models' : '/modelle'
      const canonicalPath = `${base}/${data.brandDisplayName}/${modelSlug}`
      if (route.path !== canonicalPath) {
        router.replace(canonicalPath)
      }
    }
  } catch {
    // Transient error (network, 500, timeout) — do NOT set noindex
    apiError.value = true
  } finally {
    loading.value = false
  }

})

function consumptionDeltaClass(real: number, wltp: number): string {
  const percentDelta = ((real - wltp) / wltp) * 100
  if (percentDelta <= 0) return 'text-green-600'
  if (percentDelta <= 15) return 'text-yellow-600'
  return 'text-red-600'
}

function deltaLabelClass(real: number, wltp: number): string {
  const percentDelta = ((real - wltp) / wltp) * 100
  if (percentDelta <= 0) return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400'
  if (percentDelta <= 15) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
}

function deltaLabel(real: number, wltp: number): string {
  const percentDelta = ((real - wltp) / wltp) * 100
  const sign = percentDelta > 0 ? '+' : ''
  return `${sign}${percentDelta.toFixed(1)}%`
}

function reload() { window.location.reload() }

function toTitleCase(s: string): string {
  // Preserve all-caps brands (BMW, MG) — only capitalize first letter if fully lowercase
  if (s === s.toUpperCase()) return s
  return s.charAt(0).toUpperCase() + s.slice(1)
}
</script>
