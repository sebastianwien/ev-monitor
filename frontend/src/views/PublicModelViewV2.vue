<template>
  <div class="model-page min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />
    <main class="max-w-4xl mx-auto md:px-4 py-2 md:py-8">

      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <div v-else-if="notFound" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('model.not_found_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.not_found_desc') }}</p>
        <a href="/" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">{{ t('model.goto_home') }}</a>
      </div>

      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('model.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.error_desc') }}</p>
        <button @click="reload" class="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700">{{ t('model.reload') }}</button>
      </div>

      <div v-else-if="stats">

        <!-- Breadcrumb -->
        <nav class="px-4 md:px-0 text-sm text-gray-500 dark:text-gray-400 mb-2">
          <a href="/" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('model.breadcrumb_home') }}</a>
          <span class="mx-2">›</span>
          <a :href="modelsBaseUrl" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('model.breadcrumb_models') }}</a>
          <span class="mx-2">›</span>
          <a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="hover:text-gray-700 dark:hover:text-gray-200">{{ stats.brandDisplayName }}</a>
          <span class="mx-2">›</span>
          <span class="text-gray-900 dark:text-gray-100">{{ stats.modelDisplayName.replace(stats.brandDisplayName + ' ', '') }}</span>
        </nav>

        <!-- Hero Card -->
        <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 md:shadow-sm px-4 pt-3 pb-6 md:p-6 md:mb-6">
          <a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="inline-flex items-center gap-1 text-sm text-green-600 hover:underline mb-2">
            {{ t('model.back_link', { brand: stats.brandDisplayName }) }}
          </a>
          <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-3 text-center md:text-left">
            <span class="md:hidden">{{ stats.modelDisplayName }}</span>
            <span class="hidden md:inline">{{ t('model.hero_title_v2', { model: stats.modelDisplayName }) }}</span>
          </h1>
          <div class="h-1 w-16 bg-green-500 rounded-full mb-5 mx-auto md:mx-0"></div>

          <!-- Primary metric: Realer Verbrauch -->
          <div v-if="displayConsumption" class="flex flex-col items-center py-6 rounded-xl bg-gradient-to-b from-green-50 to-white dark:from-green-900/20 dark:to-gray-800/0 border border-green-100 dark:border-green-900/30 mb-3">
            <div class="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-2">
              {{ t('model.hero_consumption_label') }}
            </div>
            <div class="flex items-baseline gap-2">
              <span class="text-5xl font-bold tabular-nums text-gray-900 dark:text-gray-100">
                {{ displayConsumption.toFixed(1) }}
              </span>
              <span class="text-xl text-gray-400 dark:text-gray-500">{{ t('model.unit_kwh_per_100km') }}</span>
            </div>
            <div v-if="worstWltpConsumption" class="mt-3 flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
              <span>{{ t('model.wltp_badge', { consumption: worstWltpConsumption.toFixed(1) }) }}</span>
              <span :class="deltaLabelClass(displayConsumption, worstWltpConsumption)"
                    class="px-2 py-0.5 rounded-full text-xs font-semibold">
                {{ deltaLabel(displayConsumption, worstWltpConsumption) }}
              </span>
            </div>
          </div>

          <!-- No data notice -->
          <div v-if="stats.logCount === 0" class="mt-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-xl border border-gray-200 dark:border-gray-600">
            <p class="text-gray-600 dark:text-gray-300 text-sm">
              {{ t('common.be_first') }}
              <a :href="registerPath" class="text-green-600 font-medium hover:underline">{{ t('common.register') }}</a>
            </p>
          </div>

          <!-- Kosten-Schnitt als Textzeile -->
          <div v-if="stats.avgCostPerKwh && displayConsumption"
               class="text-center text-sm text-gray-500 dark:text-gray-400 pt-3 pb-1">
            {{ t('model.avg_cost_prefix') }}
            <span class="text-base font-bold text-gray-900 dark:text-gray-100">
              {{ (stats.avgCostPerKwh * displayConsumption).toFixed(2) }} €
            </span>
            {{ t('model.avg_cost_suffix') }}
          </div>

          <!-- Secondary stats row: 2-col on mobile, 3-col on desktop -->
          <div class="mt-3 bg-gradient-to-br from-indigo-50 via-gray-50 to-white dark:from-indigo-900/20 dark:via-gray-700/30 dark:to-gray-800/0 border border-indigo-100 dark:border-indigo-900/30 rounded-xl py-4">
            <div class="grid grid-cols-2 md:grid-cols-3">
            <!-- Links: AC/DC auf Mobile, Ladevorgänge auf Desktop -->
            <div class="px-4 text-center border-r border-gray-200 dark:border-gray-700">
              <!-- Mobile: AC/DC -->
              <template v-if="true" class="md:hidden">
                <div class="md:hidden flex flex-col gap-1.5 items-center">
                  <template v-if="stats.acAvgCostPerKwh || stats.dcAvgCostPerKwh">
                    <div v-if="stats.acAvgCostPerKwh" class="flex items-center gap-1.5 whitespace-nowrap">
                      <span class="text-sm font-semibold text-green-600 dark:text-green-400 flex items-center gap-0.5"><BoltIcon class="h-4 w-4" />AC</span>
                      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ (stats.acAvgCostPerKwh * 100).toFixed(1) }}<sup class="text-xs text-gray-400">*</sup><span class="text-xs font-normal text-gray-400"> {{ t('model.unit_ct_per_kwh') }}</span></span>
                    </div>
                    <div v-if="stats.dcAvgCostPerKwh" class="flex items-center gap-1.5 whitespace-nowrap">
                      <span class="text-sm font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-0.5"><BoltIcon class="h-4 w-4" />DC</span>
                      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ (stats.dcAvgCostPerKwh * 100).toFixed(1) }}<sup class="text-xs text-gray-400">*</sup><span class="text-xs font-normal text-gray-400"> {{ t('model.unit_ct_per_kwh') }}</span></span>
                    </div>
                  </template>
                  <template v-else-if="stats.avgCostPerKwh">
                    <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ (stats.avgCostPerKwh * 100).toFixed(1) }}</div>
                    <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.unit_ct_per_kwh') }}</div>
                  </template>
                  <template v-else>
                    <div class="text-xl font-bold text-gray-400">-</div>
                    <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_costs') }}</div>
                  </template>
                </div>
              </template>
              <!-- Desktop: Ladevorgänge -->
              <div class="hidden md:block">
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
                  {{ stats.logCount > 0 ? stats.logCount.toLocaleString() : '-' }}
                </div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_sessions') }}</div>
                <div v-if="stats.estimatedConsumptionCount > 0" class="text-xs text-gray-400 dark:text-gray-500 mt-1 italic">
                  {{ stats.estimatedConsumptionCount }} {{ t('model.metrics_estimated') }}
                </div>
              </div>
            </div>
            <!-- Reichweite -->
            <div class="px-4 text-center">
              <template v-if="displayRange">
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ displayRange }} km</div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_range') }}</div>
                <div class="flex items-center justify-center gap-1 mt-1">
                  <Battery0Icon class="h-3 w-3 text-gray-400" />
                  <span class="text-xs text-gray-400">90%→10%</span>
                </div>
              </template>
              <template v-else>
                <div class="text-xl font-bold text-gray-400">-</div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_range_label') }}</div>
              </template>
            </div>
            <!-- Kosten: nur auf Desktop als 3. Spalte -->
            <div class="hidden md:block px-4 text-center border-l border-gray-200 dark:border-gray-700">
              <template v-if="stats.acAvgCostPerKwh || stats.dcAvgCostPerKwh">
                <div class="flex flex-col gap-2 items-start w-fit mx-auto">
                  <div v-if="stats.acAvgCostPerKwh" class="flex items-baseline gap-2">
                    <span class="text-sm font-semibold text-green-600 dark:text-green-400 flex items-center gap-0.5 w-10">
                      <BoltIcon class="h-4 w-4" />AC
                    </span>
                    <span class="text-xl font-bold text-gray-900 dark:text-gray-100">
                      {{ (stats.acAvgCostPerKwh * 100).toFixed(1) }}<sup class="text-xs text-gray-400">*</sup>
                      <span class="text-sm font-normal text-gray-400">{{ t('model.unit_ct_per_kwh') }}</span>
                    </span>
                  </div>
                  <div v-if="stats.dcAvgCostPerKwh" class="flex items-baseline gap-2">
                    <span class="text-sm font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-0.5 w-10">
                      <BoltIcon class="h-4 w-4" />DC
                    </span>
                    <span class="text-xl font-bold text-gray-900 dark:text-gray-100">
                      {{ (stats.dcAvgCostPerKwh * 100).toFixed(1) }}<sup class="text-xs text-gray-400">*</sup>
                      <span class="text-sm font-normal text-gray-400">{{ t('model.unit_ct_per_kwh') }}</span>
                    </span>
                  </div>
                </div>
              </template>
              <template v-else-if="stats.avgCostPerKwh">
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
                  {{ (stats.avgCostPerKwh * 100).toFixed(1) }} ct
                </div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.unit_ct_per_kwh') }}</div>
              </template>
              <template v-else>
                <div class="text-xl font-bold text-gray-400">-</div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_costs') }}</div>
              </template>
            </div>
            </div><!-- end grid -->

            <!-- Ladevorgänge: Mobile als eigene Zeile -->
            <div class="md:hidden mt-3 pt-3 border-t border-gray-200 dark:border-gray-700 text-center">
              <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ stats.logCount > 0 ? stats.logCount.toLocaleString() : '-' }}
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_sessions') }}</div>
              <div v-if="stats.estimatedConsumptionCount > 0" class="text-xs text-gray-400 dark:text-gray-500 mt-1 italic">
                {{ stats.estimatedConsumptionCount }} {{ t('model.metrics_estimated') }}
              </div>
            </div>
          </div><!-- end stats wrapper -->

          <!-- Kostenrechner -->
          <div v-if="displayConsumption" class="mt-6 pt-5 border-t border-gray-100 dark:border-gray-700">
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
            <div class="flex items-center gap-3">
              <span class="text-xs text-gray-400 shrink-0">0,10 €</span>
              <input type="range" min="0.10" max="1.00" step="0.01" v-model.number="pricePerKwh"
                     class="flex-1 h-2 bg-gray-200 dark:bg-gray-700 rounded-full appearance-none cursor-pointer accent-yellow-500" />
              <span class="text-xs text-gray-400 shrink-0">1,00 €</span>
            </div>
          </div>
        </div><!-- end Hero -->

        <!-- AC Fußnote -->
        <div v-if="stats.acAvgCostPerKwh" class="px-4 md:px-0 mt-1 mb-3 text-center">
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('model.ac_footnote') }}</p>
        </div>

        <!-- Affiliate Banner -->
        <AffiliateBanner v-if="!authStore.isAuthenticated()" />

        <!-- Variant Switcher + Seasonal + WLTP -->
        <div v-if="stats.wltpVariants.length > 0 || showSeasonalBreakdown"
             class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 md:shadow-sm md:mb-6 overflow-hidden">

          <!-- Variant Switcher -->
          <div v-if="stats.wltpVariants.length > 1" class="px-6 py-4 border-b border-gray-100 dark:border-gray-700">
            <div class="flex items-center gap-3 flex-wrap">
              <span class="text-sm font-medium text-gray-500 dark:text-gray-400 whitespace-nowrap">{{ t('model.variant_title') }}</span>
              <div class="relative flex bg-gray-100 dark:bg-gray-700 rounded-full p-1 w-fit">
                <div class="absolute top-1 bottom-1 left-1 rounded-full bg-blue-600 shadow-sm transition-transform duration-300 ease-in-out"
                     :style="{ width: `calc((100% - 8px) / ${stats.wltpVariants.length})`, transform: `translateX(calc(${selectedVariantIndex * 100}%))` }" />
                <button v-for="(v, i) in stats.wltpVariants" :key="v.batteryCapacityKwh"
                        @click="selectedVariantIndex = i"
                        class="relative flex-1 text-center px-4 text-sm font-medium py-1.5 rounded-full transition-colors duration-300 z-10 whitespace-nowrap"
                        :class="i === selectedVariantIndex ? 'text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'">
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

          <!-- Seasonal Breakdown -->
          <div v-if="showSeasonalBreakdown" class="px-6 py-5 border-b border-gray-100 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-700/20">
            <p class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-4 flex items-center gap-1.5">
              <ChartBarIcon class="h-4 w-4 text-gray-400" />
              {{ t('model.seasonal_title') }}
            </p>

            <!-- Summer -->
            <div class="mb-3">
              <div class="flex items-center justify-between text-sm mb-1.5">
                <div class="flex items-center gap-1.5 text-amber-600 dark:text-amber-400 font-medium">
                  <SunIcon class="h-4 w-4" />
                  <span>{{ t('model.seasonal_summer') }} <span class="hidden md:inline text-gray-400 dark:text-gray-500 font-normal">{{ t('model.seasonal_summer_months') }}</span></span>
                </div>
                <div class="flex items-center gap-3">
                  <span v-if="selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km"
                        class="font-bold text-amber-600 dark:text-amber-400">
                    {{ selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                  </span>
                  <span v-if="selectedVariant && selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km"
                        class="text-sm font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
                    ~ {{ Math.round(selectedVariant.batteryCapacityKwh * 0.8 / selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km * 10) * 10 }} km
                  </span>
                  <span class="hidden md:inline text-xs"
                        :class="selectedVariant!.seasonalDistribution!.summerLogCount < 30 ? 'text-yellow-600 font-medium' : 'text-gray-400'">
                    ({{ selectedVariant!.seasonalDistribution!.summerLogCount }} {{ t('model.seasonal_trips') }})
                  </span>
                </div>
              </div>
              <!-- Bar -->
              <div class="h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                <div v-if="selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km && selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km"
                     class="h-full bg-amber-400 rounded-full"
                     :style="{ width: `${Math.min(100, (selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km / selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km) * 100).toFixed(0)}%` }">
                </div>
              </div>
            </div>

            <!-- Winter -->
            <div class="mb-4">
              <div class="flex items-center justify-between text-sm mb-1.5">
                <div class="flex items-center gap-1.5 text-blue-500 dark:text-blue-400 font-medium">
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <line x1="12" y1="2" x2="12" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/>
                    <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/><line x1="19.07" y1="4.93" x2="4.93" y2="19.07"/>
                    <circle cx="12" cy="12" r="2" fill="currentColor"/>
                  </svg>
                  <span>{{ t('model.seasonal_winter') }} <span class="hidden md:inline text-gray-400 dark:text-gray-500 font-normal">{{ t('model.seasonal_winter_months') }}</span></span>
                </div>
                <div class="flex items-center gap-3">
                  <span v-if="selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km"
                        class="font-bold text-blue-500 dark:text-blue-400">
                    {{ selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                  </span>
                  <span v-if="selectedVariant && selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km"
                        class="text-sm font-semibold text-gray-700 dark:text-gray-300 whitespace-nowrap">
                    ~ {{ Math.round(selectedVariant.batteryCapacityKwh * 0.8 / selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km * 10) * 10 }} km
                  </span>
                  <span class="hidden md:inline text-xs"
                        :class="selectedVariant!.seasonalDistribution!.winterLogCount < 30 ? 'text-yellow-600 font-medium' : 'text-gray-400'">
                    ({{ selectedVariant!.seasonalDistribution!.winterLogCount }} {{ t('model.seasonal_trips') }})
                  </span>
                </div>
              </div>
              <div class="h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                <div class="h-full bg-blue-400 rounded-full w-full"></div>
              </div>
            </div>

            <!-- Weighted Average -->
            <div class="pt-3 border-t border-gray-100 dark:border-gray-700 flex items-center justify-between text-sm">
              <div>
                <span class="font-medium text-gray-700 dark:text-gray-300">{{ t('model.seasonal_weighted_avg') }}</span>
                <p class="text-xs text-gray-400 mt-0.5">
                  {{ t('model.seasonal_split', { summer: selectedVariant!.seasonalDistribution!.summerPercentage, winter: selectedVariant!.seasonalDistribution!.winterPercentage }) }}
                </p>
              </div>
              <span class="font-bold text-gray-900 dark:text-gray-100">
                {{ selectedVariant!.seasonalDistribution!.totalConsumptionKwhPer100km != null
                  ? selectedVariant!.seasonalDistribution!.totalConsumptionKwhPer100km.toFixed(1) + ' kWh/100km'
                  : '-' }}
              </span>
            </div>
          </div><!-- end seasonal -->

          <!-- WLTP Section -->
          <div v-if="stats.wltpVariants.length > 0">
            <h2 class="text-base font-semibold text-gray-700 dark:text-gray-300 px-6 pt-5 pb-3 flex items-center gap-2">
              <ClipboardDocumentListIcon class="h-5 w-5 text-gray-400" />
              {{ t('model.wltp_section_title') }}
            </h2>

            <!-- Mobile Card -->
            <div class="md:hidden">
              <div v-if="selectedVariant" class="px-6 pb-4">
                <div class="relative flex items-center justify-center mb-3">
                  <div class="font-semibold text-gray-900 dark:text-gray-100">{{ selectedVariant.batteryCapacityKwh }} kWh</div>
                  <span v-if="!selectedVariant.realConsumptionKwhPer100km"
                        class="absolute right-0 text-xs px-1.5 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 text-gray-500">
                    {{ t('model.wltp_no_trips') }}
                  </span>
                  <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 10"
                        class="absolute right-0 text-xs px-1.5 py-0.5 rounded-full bg-red-50 border border-red-200 text-red-600 dark:bg-red-900/40 dark:border-red-700 dark:text-red-400">
                    ⚠ {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                  </span>
                  <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 50"
                        class="absolute right-0 text-xs px-1.5 py-0.5 rounded-full bg-yellow-50 border border-yellow-200 text-yellow-700 dark:bg-yellow-900/40 dark:border-yellow-700 dark:text-yellow-400">
                    {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                  </span>
                  <span v-else-if="selectedVariant.realConsumptionTripCount != null"
                        class="absolute right-0 text-xs px-1.5 py-0.5 rounded-full bg-green-50 border border-green-200 text-green-700 dark:bg-green-900/40 dark:border-green-700 dark:text-green-400">
                    {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                  </span>
                </div>
                <div class="grid grid-cols-2 gap-2 text-sm">
                  <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                    <div class="text-xs text-gray-500 mb-0.5">{{ t('model.wltp_wltp_range') }}</div>
                    <div class="font-medium text-gray-800 dark:text-gray-200">{{ selectedVariant.wltpRangeKm }} km</div>
                  </div>
                  <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                    <div class="text-xs text-gray-500 mb-0.5">{{ t('model.wltp_wltp_consumption') }}</div>
                    <div class="font-medium text-gray-800 dark:text-gray-200">{{ selectedVariant.wltpConsumptionKwhPer100km }} kWh/100km</div>
                  </div>
                  <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                    <div class="text-xs text-gray-500 mb-0.5">{{ t('model.wltp_real_range') }}</div>
                    <div class="font-medium text-gray-800 dark:text-gray-200">
                      {{ selectedVariant.realConsumptionKwhPer100km
                        ? Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.realConsumptionKwhPer100km * 10) * 10 + ' km'
                        : '-' }}
                    </div>
                    <div class="text-xs text-gray-400">{{ t('model.wltp_full_range') }}</div>
                  </div>
                  <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-2">
                    <div class="text-xs text-gray-500 mb-0.5">{{ t('model.wltp_real_consumption') }}</div>
                    <template v-if="selectedVariant.realConsumptionKwhPer100km">
                      <div :class="consumptionDeltaClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km)" class="font-medium">
                        {{ selectedVariant.realConsumptionKwhPer100km.toFixed(1) }} kWh/100km
                      </div>
                      <span :class="deltaLabelClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km)"
                            class="text-xs px-1.5 py-0.5 rounded-full mt-1 inline-block">
                        {{ deltaLabel(selectedVariant.realConsumptionKwhPer100km, selectedVariant.wltpConsumptionKwhPer100km) }}
                      </span>
                    </template>
                    <div v-else class="text-gray-400 text-sm">-</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Desktop Table -->
            <div class="hidden md:block overflow-x-auto px-6 pb-2">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-left text-gray-500 dark:text-gray-400 border-b border-gray-100 dark:border-gray-700">
                    <th class="pb-3 pr-4 font-medium whitespace-nowrap">{{ t('model.wltp_table_battery') }}</th>
                    <th class="pb-3 pr-4 font-medium whitespace-nowrap">{{ t('model.wltp_table_range') }}</th>
                    <th class="pb-3 pr-4 font-medium whitespace-nowrap">
                      {{ t('model.wltp_table_real_range') }} <span class="text-xs">{{ t('model.wltp_table_full_to_empty') }}</span>
                    </th>
                    <th class="pb-3 pr-4 font-medium whitespace-nowrap">{{ t('model.wltp_table_consumption') }}</th>
                    <th class="pb-3 font-medium whitespace-nowrap">{{ t('model.wltp_table_real_consumption') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="selectedVariant" class="border-b border-gray-50 dark:border-gray-700">
                    <td class="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">{{ selectedVariant.batteryCapacityKwh }} kWh</td>
                    <td class="py-3 pr-4 text-gray-700 dark:text-gray-300">{{ selectedVariant.wltpRangeKm }} km</td>
                    <td class="py-3 pr-4 whitespace-nowrap">
                      <div v-if="selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km || selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km"
                           class="flex items-center gap-1.5">
                        <span class="flex items-center gap-1 text-amber-600">
                          <SunIcon class="h-3.5 w-3.5" />
                          <span>{{ selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km ? Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km * 10) * 10 + ' km' : '-' }}</span>
                        </span>
                        <span class="text-gray-300">/</span>
                        <span class="flex items-center gap-1 text-blue-500">
                          <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                            <line x1="12" y1="2" x2="12" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/>
                            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/><line x1="19.07" y1="4.93" x2="4.93" y2="19.07"/>
                            <circle cx="12" cy="12" r="2" fill="currentColor"/>
                          </svg>
                          <span>{{ selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km ? Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km * 10) * 10 + ' km' : '-' }}</span>
                        </span>
                      </div>
                      <span v-else class="text-gray-400">-</span>
                    </td>
                    <td class="py-3 pr-4 text-gray-700 dark:text-gray-300">{{ selectedVariant.wltpConsumptionKwhPer100km }} kWh/100km</td>
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
                                class="text-xs px-1.5 py-0.5 rounded-full bg-red-50 border border-red-200 text-red-600 dark:bg-red-900/40 dark:border-red-700 dark:text-red-400">
                            ⚠ {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}
                          </span>
                          <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 50"
                                class="text-xs px-1.5 py-0.5 rounded-full bg-yellow-50 border border-yellow-200 text-yellow-700 dark:bg-yellow-900/40 dark:border-yellow-700 dark:text-yellow-400">
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

            <!-- WLTP Notes -->
            <div class="px-6 py-4 space-y-2">
              <span class="inline-block text-xs bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 px-2.5 py-1 rounded-full">
                {{ t('model.wltp_note') }}
              </span>
              <p class="text-xs text-gray-400 dark:text-gray-500">{{ t('model.wltp_measurement_note') }}</p>
            </div>
          </div><!-- end wltp -->

        </div><!-- end combined card -->

        <!-- CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 md:rounded-2xl p-6 text-white">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="text-xl font-bold">{{ t('model.cta_title') }}</h2>
          </div>
          <p class="text-green-100 mb-4">{{ t('model.cta_desc') }}</p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath" class="bg-white text-green-700 font-semibold px-4 py-2 rounded-lg hover:bg-green-50 transition-colors">
              {{ t('model.cta_free_start') }}
            </a>
            <a :href="loginPath" class="border border-white text-white px-4 py-2 rounded-lg hover:bg-green-600 transition-colors">
              {{ t('model.cta_login') }}
            </a>
          </div>
        </div>

        <!-- SEO Text -->
        <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-y border-gray-200 dark:border-gray-700 md:shadow-sm p-6 md:mt-6">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('model.seo_section_title', { model: stats.modelDisplayName }) }}
          </h2>
          <div class="space-y-4 text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
            <p>
              {{ t('model.seo_intro', { model: stats.modelDisplayName }) }}
              <template v-if="bestWltpRange"> {{ t('model.seo_wltp_range', { range: bestWltpRange }) }}</template>.
              <template v-if="stats.avgConsumptionKwhPer100km">
                <template v-if="consumptionDataQuality === 'good'">
                  {{ t('model.seo_consumption_good', { consumption: stats.avgConsumptionKwhPer100km.toFixed(1), sessions: stats.logCount, count: Math.min(consumptionDataCount, stats.logCount) }) }}
                </template>
                <template v-else-if="consumptionDataQuality === 'low'">
                  {{ t('model.seo_consumption_low', { consumption: stats.avgConsumptionKwhPer100km.toFixed(1), count: Math.min(consumptionDataCount, stats.logCount) }) }}
                </template>
                <template v-else>
                  {{ t('model.seo_consumption_sparse', { consumption: stats.avgConsumptionKwhPer100km.toFixed(1), count: Math.min(consumptionDataCount, stats.logCount) }) }}
                </template>
              </template>
              <template v-else>
                {{ t('model.seo_no_data_cta', { model: stats.modelDisplayName }) }}
              </template>
            </p>
            <div v-if="stats.avgCostPerKwh">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_costs_title') }}</h3>
              <p>
                {{ t('model.seo_costs_intro', { model: stats.modelDisplayName, price: (stats.avgCostPerKwh * 100).toFixed(1) }) }}
                <template v-if="stats.avgKwhPerSession">
                  {{ t('model.seo_costs_session', { kwh: stats.avgKwhPerSession.toFixed(1), cost: (stats.avgCostPerKwh * stats.avgKwhPerSession).toFixed(2) }) }}
                </template>
              </p>
            </div>
            <div v-if="stats.wltpVariants.length > 0 && consumptionDataCount >= 25">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_wltp_title') }}</h3>
              <p>
                {{ t('model.seo_wltp_intro') }}
                <template v-if="stats.avgConsumptionKwhPer100km && worstWltpConsumption">
                  {{ t('model.seo_wltp_delta', { delta: wltpDeltaPercent }) }}
                </template>
              </p>
            </div>
            <div>
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_seasonal_title') }}</h3>
              <p v-if="showSeasonalBreakdown && selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km && selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km">
                {{ t('model.seo_seasonal_data', {
                  model: stats.modelDisplayName,
                  summer: selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km.toFixed(1),
                  winter: selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km.toFixed(1),
                  pct: Math.round((selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km / selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km - 1) * 100)
                }) }}
              </p>
              <p v-else>
                {{ t('model.seo_seasonal_no_data', { model: stats.modelDisplayName }) }}
              </p>
            </div>
          </div>
        </div>

        <!-- FAQ -->
        <div v-if="faqItems.length > 0" class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-y border-gray-200 dark:border-gray-700 md:shadow-sm p-6 mt-6">
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

      </div><!-- end stats -->

    </main>

    <!-- Related models -->
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

    <!-- Floating back pill: only shown when navigating from LP v2 -->
    <Teleport to="body">
      <div v-if="showBackPill" class="fixed bottom-6 left-4 z-50">
        <button
          @click="goBackToLpV2"
          class="back-pill btn-3d-delay inline-flex items-center gap-1.5 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 rounded-full px-4 py-2 shadow-lg"
        >
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="h-4 w-4">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
          </svg>
          Zurück
        </button>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { getModelStats, type PublicModelStats } from '../api/publicModelService'
import {
  ArrowTrendingUpIcon, ClipboardDocumentListIcon, Battery0Icon,
  SunIcon, ChartBarIcon, ExclamationTriangleIcon, BoltIcon
} from '@heroicons/vue/24/outline'
import PublicNav from '../components/PublicNav.vue'
import AffiliateBanner from '../components/AffiliateBanner.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const isEn = computed(() => route.path.startsWith('/en/'))
const modelsBaseUrl = computed(() => isEn.value ? '/en/models' : '/modelle')
const loginPath = computed(() => isEn.value ? '/en/login' : '/login')
const registerPath = computed(() => isEn.value ? '/en/register' : '/register')
const authStore = useAuthStore()
const loading = ref(true)
const notFound = ref(false)
const apiError = ref(false)
const showBackPill = ref(false)
const stats = ref<PublicModelStats | null>(null)
const selectedVariantIndex = ref(0)
const pricePerKwh = ref(0.35)

const isAuthenticated = computed(() => authStore.isAuthenticated())

const brand = route.params.brand as string
const model = route.params.model as string

const canonicalBrand = computed(() => stats.value?.brandDisplayName ?? brand)
// canonicalModelSlug unused in V2 (no redirect logic)


const selectedVariant = computed(() => stats.value?.wltpVariants[selectedVariantIndex.value] ?? null)

const displayConsumption = computed(() =>
  selectedVariant.value?.realConsumptionKwhPer100km ?? stats.value?.avgConsumptionKwhPer100km ?? null
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

const consumptionDataCount = computed(() => {
  if (!stats.value) return 0
  const socCount = stats.value.wltpVariants.reduce((sum, v) => sum + (v.realConsumptionTripCount ?? 0), 0)
  return socCount + (stats.value.estimatedConsumptionCount ?? 0)
})

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

  if (stats.value.avgConsumptionKwhPer100km) {
    const n = consumptionDataCount.value
    const quality = consumptionDataQuality.value
    const dataNote = quality === 'good'
      ? t('model.faq_consumption_data_good', { n })
      : quality === 'low'
        ? t('model.faq_consumption_data_low', { n })
        : t('model.faq_consumption_data_sparse', { n })
    items.push({
      question: t('model.faq_q_consumption', { model: name }),
      answer: t('model.faq_a_consumption', {
        model: name,
        consumption: stats.value.avgConsumptionKwhPer100km.toFixed(1),
        dataNote,
        wltp: worstWltpConsumption.value?.toFixed(1) ?? '-'
      })
    })
  }

  if (bestWltpRange.value && stats.value.avgConsumptionKwhPer100km && stats.value.wltpVariants.length > 0) {
    const largestBattery = Math.max(...stats.value.wltpVariants.map(v => v.batteryCapacityKwh))
    const realRange = Math.round(largestBattery / stats.value.avgConsumptionKwhPer100km * 100)
    items.push({
      question: t('model.faq_q_range', { model: name }),
      answer: t('model.faq_a_range', {
        model: name,
        wltpRange: bestWltpRange.value,
        battery: largestBattery,
        consumption: stats.value.avgConsumptionKwhPer100km.toFixed(1),
        realRange
      })
    })
  }

  if (stats.value.avgCostPerKwh && stats.value.avgKwhPerSession) {
    items.push({
      question: t('model.faq_q_cost', { model: name }),
      answer: t('model.faq_a_cost', {
        price: (stats.value.avgCostPerKwh * 100).toFixed(1),
        kwh: stats.value.avgKwhPerSession.toFixed(1),
        cost: (stats.value.avgCostPerKwh * stats.value.avgKwhPerSession).toFixed(2)
      })
    })
  }

  if (worstWltpConsumption.value && stats.value.avgConsumptionKwhPer100km && consumptionDataCount.value >= 25) {
    const diff = (stats.value.avgConsumptionKwhPer100km - worstWltpConsumption.value).toFixed(1)
    const pct = Math.round((stats.value.avgConsumptionKwhPer100km / worstWltpConsumption.value - 1) * 100)
    items.push({
      question: t('model.faq_q_wltp_delta', { model: name }),
      answer: t('model.faq_a_wltp_delta', {
        model: name,
        wltp: worstWltpConsumption.value.toFixed(1),
        real: stats.value.avgConsumptionKwhPer100km.toFixed(1),
        diff,
        pct
      })
    })
  }

  const seasonal = selectedVariant.value?.seasonalDistribution
  const hasSeasonalData = seasonal && seasonal.winterLogCount >= 10 && seasonal.summerLogCount >= 10
  items.push({
    question: t('model.faq_q_winter', { model: name }),
    answer: hasSeasonalData
      ? t('model.faq_a_winter_data', {
          model: name,
          winter: seasonal!.winterConsumptionKwhPer100km?.toFixed(1) ?? '-',
          summer: seasonal!.summerConsumptionKwhPer100km?.toFixed(1) ?? '-'
        })
      : t('model.faq_a_winter_no_data', { model: name })
  })

  return items
})

const currentYear = new Date().getFullYear()

useHead(computed(() => {
  if (notFound.value) return { title: 'Modell nicht gefunden - EV Monitor', meta: [{ name: 'robots', content: 'noindex, nofollow' }] }
  if (!stats.value) return { title: 'EV Monitor', meta: [{ name: 'robots', content: 'index, follow' }] }

  const name = stats.value.modelDisplayName
  const consumption = displayConsumption.value
  const wltp = worstWltpConsumption.value

  const canonicalUrl = isEn.value
    ? `https://ev-monitor.net/en/models/${canonicalBrand.value}/${model}`
    : `https://ev-monitor.net/modelle/${canonicalBrand.value}/${model}`
  const deUrl = `https://ev-monitor.net/modelle/${canonicalBrand.value}/${model}`
  const enUrl = `https://ev-monitor.net/en/models/${canonicalBrand.value}/${model}`

  const description = consumption && wltp
    ? t('model.meta_description_with_data', { model: name, consumption: consumption.toFixed(1), wltp: wltp.toFixed(1) })
    : t('model.meta_description_no_data', { model: name })

  const title = t('model.meta_title', { model: name, year: currentYear })

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
      { '@type': 'ListItem', position: 2, name: isEn.value ? 'Electric Cars' : 'Elektroautos', item: isEn.value ? 'https://ev-monitor.net/en/models' : 'https://ev-monitor.net/modelle' },
      { '@type': 'ListItem', position: 3, name: stats.value.brandDisplayName, item: isEn.value ? `https://ev-monitor.net/en/models/${canonicalBrand.value}` : `https://ev-monitor.net/modelle/${canonicalBrand.value}` },
      { '@type': 'ListItem', position: 4, name, item: canonicalUrl },
    ]
  }

  const productJsonLd: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name,
    description,
    brand: { '@type': 'Brand', name: stats.value.brandDisplayName },
    url: canonicalUrl,
  }
  if (consumption) {
    productJsonLd['additionalProperty'] = [
      { '@type': 'PropertyValue', name: isEn.value ? 'Real Consumption' : 'Realverbrauch', value: `${consumption.toFixed(1)} kWh/100km` },
      ...(wltp ? [{ '@type': 'PropertyValue', name: 'WLTP', value: `${wltp.toFixed(1)} kWh/100km` }] : []),
    ]
  }

  return {
    title,
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: t('model.meta_keywords', { model: name }) },
      { name: 'robots', content: 'index, follow' },
      { property: 'og:title', content: title },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'website' },
      { property: 'og:url', content: canonicalUrl },
      { property: 'og:locale', content: isEn.value ? 'en_GB' : 'de_DE' },
    ],
    link: [
      { rel: 'canonical', href: canonicalUrl },
      { rel: 'alternate', hreflang: 'de', href: deUrl },
      { rel: 'alternate', hreflang: 'en', href: enUrl },
      { rel: 'alternate', hreflang: 'x-default', href: deUrl },
    ],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(productJsonLd) },
    ]
  }
}))

onMounted(async () => {
  if (sessionStorage.getItem('ev_from') === 'lp_v2') {
    showBackPill.value = true
    sessionStorage.removeItem('ev_from')
  }

  try {
    const data = await getModelStats(brand, model)
    if (!data) {
      notFound.value = true
    } else {
      stats.value = data
      if (data.wltpVariants.length > 1) {
        let maxTrips = -1
        data.wltpVariants.forEach((v, i) => {
          if ((v.realConsumptionTripCount ?? 0) > maxTrips) {
            maxTrips = v.realConsumptionTripCount ?? 0
            selectedVariantIndex.value = i
          }
        })
      }
    }
  } catch {
    apiError.value = true
  } finally {
    loading.value = false
  }
})

function consumptionDeltaClass(real: number, wltp: number): string {
  const pct = ((real - wltp) / wltp) * 100
  if (pct <= 0) return 'text-green-600'
  if (pct <= 15) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-500'
}

function deltaLabelClass(real: number, wltp: number): string {
  const pct = ((real - wltp) / wltp) * 100
  if (pct <= 0) return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400'
  if (pct <= 15) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
}

function deltaLabel(real: number, wltp: number): string {
  const pct = ((real - wltp) / wltp) * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
}

function reload() { window.location.reload() }

function goBackToLpV2() {
  sessionStorage.setItem('ev_back_slide', '1')
  router.back()
}

</script>

<style scoped>
.model-page {
  animation: page-slide-in 0.28s cubic-bezier(0.25, 0.46, 0.45, 0.94) both;
}

@keyframes page-slide-in {
  from {
    opacity: 0;
    transform: translateX(28px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.back-pill {
  transition: transform 0.1s, box-shadow 0.1s;
}
.back-pill:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}
</style>
