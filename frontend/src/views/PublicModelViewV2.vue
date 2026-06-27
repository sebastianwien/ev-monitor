<template>
  <div :class="isAuthenticated ? '' : 'sl-grid isolate min-h-screen bg-gray-50 dark:bg-gray-950 overflow-x-clip'">
    <!-- Maus-reaktives Gitter wie auf der Startseite (nur für öffentliche Besucher) -->
    <GridRippleBackground v-if="!isAuthenticated" />
    <div class="model-page">
    <PublicNav />
    <main class="max-w-4xl mx-auto md:px-4 py-2 md:py-8">

      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <div v-else-if="notFound" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('model.not_found_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.not_found_desc') }}</p>
        <a href="/" class="bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">{{ t('model.goto_home') }}</a>
      </div>

      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200 mb-2">{{ t('model.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.error_desc') }}</p>
        <button @click="reload" class="bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">{{ t('model.reload') }}</button>
      </div>

      <div v-else-if="stats">

        <!-- Breadcrumb: als Chip geerdet, damit er nicht im Gitter-Hintergrund untergeht -->
        <nav aria-label="Breadcrumb" class="px-4 md:px-0 mb-3">
          <ol class="inline-flex flex-wrap items-center gap-1.5 text-sm bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm border border-gray-200 dark:border-gray-700 rounded-full px-3.5 py-1.5 shadow-sm">
            <li><a href="/" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ t('model.breadcrumb_home') }}</a></li>
            <ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600 shrink-0" aria-hidden="true" />
            <li><a :href="modelsBaseUrl" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ t('model.breadcrumb_models') }}</a></li>
            <ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600 shrink-0" aria-hidden="true" />
            <li><a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ stats.brandDisplayName }}</a></li>
            <ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600 shrink-0" aria-hidden="true" />
            <li aria-current="page" class="font-bold text-gray-900 dark:text-gray-100">{{ stats.modelDisplayName.replace(stats.brandDisplayName + ' ', '') }}</li>
          </ol>
        </nav>

        <!-- Hero Card -->
        <div class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-t-2 md:border-b-2 border-gray-800 dark:border-gray-200 px-4 pt-3 pb-6 md:p-6 md:mb-6">
          <a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="inline-flex items-center gap-1 text-sm text-green-600 hover:underline mb-2">
            {{ t('model.back_link', { brand: stats.brandDisplayName }) }}
          </a>
          <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-3 text-center">
            <span class="md:hidden">{{ stats.modelDisplayName }}</span>
            <span class="hidden md:inline">{{ t('model.hero_title_v2', { model: stats.modelDisplayName }) }}</span>
          </h1>
          <div class="h-1 w-16 bg-green-500 rounded-full mb-4 mx-auto"></div>

          <!-- Variant Switcher -->
          <div v-if="activeVariants.length > 1" :style="{ top: stickyTop }" class="sticky z-20 flex flex-col items-center gap-2 mb-5 py-3 bg-white/95 dark:bg-gray-800/95 backdrop-blur-sm border-b border-gray-200/80 dark:border-gray-700/80">
            <span class="text-xs font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wide">{{ t('model.variant_title') }}</span>
            <div class="flex gap-2 flex-wrap justify-center">
              <button v-for="(v, i) in activeVariants" :key="v.displayLabel ?? v.batteryCapacityKwh"
                      @click="selectedVariantIndex = i"
                      class="btn-3d px-3 py-1.5 rounded-sm text-sm font-semibold border-2 border-gray-800 dark:border-gray-200 transition whitespace-nowrap"
                      :class="i === selectedVariantIndex
                        ? 'bg-green-600 text-white active'
                        : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700'">
                {{ v.displayLabel || v.variantName || (v.batteryCapacityKwh + ' kWh') }}
              </button>
            </div>
            <span v-if="selectedVariant?.seasonalDistribution && (selectedVariant.seasonalDistribution.summerLogCount < 30 || selectedVariant.seasonalDistribution.winterLogCount < 30)"
                  class="flex items-center gap-1 text-xs text-yellow-600 font-medium">
              <ExclamationTriangleIcon class="h-3.5 w-3.5" />
              {{ t('model.variant_low_trips') }}
            </span>
          </div>

          <!-- Primary metric: Realer Verbrauch -->
          <div v-if="displayConsumption" class="flex flex-col items-center py-5 rounded-xl bg-green-50 dark:bg-green-900/20 border-2 border-gray-800 dark:border-gray-200 mb-3">
            <div class="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-2">
              {{ t('model.hero_consumption_label') }}
            </div>
            <!-- Range: min – max -->
            <template v-if="communityConsumptionRange">
              <div class="flex items-baseline gap-1.5">
                <span class="text-4xl sm:text-5xl font-extrabold tabular-nums text-gray-900 dark:text-gray-100">
                  {{ formatConsumption(communityConsumptionRange.min, { showUnit: false }) }}
                </span>
                <span class="text-2xl sm:text-3xl text-gray-400 dark:text-gray-500">–</span>
                <span class="text-4xl sm:text-5xl font-extrabold tabular-nums text-gray-900 dark:text-gray-100">
                  {{ formatConsumption(communityConsumptionRange.max, { showUnit: false }) }}
                </span>
                <span class="text-base sm:text-xl text-gray-400 dark:text-gray-500">{{ consumptionUnitLabel() }}</span>
              </div>
              <div v-if="heroOfficialConsumption" class="mt-3 flex items-center gap-2 text-base text-gray-600 dark:text-gray-300">
                <span>{{ ratingLabel === 'EPA'
                  ? t('model.epa_badge', { consumption: formatConsumption(heroOfficialConsumption, { showUnit: false }) })
                  : t('model.wltp_badge', { consumption: formatConsumption(heroOfficialConsumption, { showUnit: false }) }) }}</span>
                <span class="px-2 py-0.5 rounded-full text-sm font-semibold bg-gray-100 dark:bg-gray-700">
                  <span :class="simpleDeltaClass(communityConsumptionRange.min, heroOfficialConsumption)">{{ consumptionDeltaLabel(communityConsumptionRange.min, heroOfficialConsumption) }}</span>
                  <span class="text-gray-400 dark:text-gray-500"> – </span>
                  <span :class="simpleDeltaClass(communityConsumptionRange.max, heroOfficialConsumption)">{{ consumptionDeltaLabel(communityConsumptionRange.max, heroOfficialConsumption) }}</span>
                </span>
              </div>
            </template>
            <!-- Fallback: single average -->
            <template v-else>
              <div class="flex items-baseline gap-2">
                <span class="text-5xl sm:text-6xl font-extrabold tabular-nums text-gray-900 dark:text-gray-100">
                  {{ formatConsumption(displayConsumption, { showUnit: false }) }}
                </span>
                <span class="text-base sm:text-xl text-gray-400 dark:text-gray-500">{{ consumptionUnitLabel() }}</span>
              </div>
              <div v-if="heroOfficialConsumption" class="mt-3 flex items-center gap-2 text-base text-gray-600 dark:text-gray-300">
                <span>{{ ratingLabel === 'EPA'
                  ? t('model.epa_badge', { consumption: formatConsumption(heroOfficialConsumption, { showUnit: false }) })
                  : t('model.wltp_badge', { consumption: formatConsumption(heroOfficialConsumption, { showUnit: false }) }) }}</span>
                <span :class="deltaLabelClass(displayConsumption, heroOfficialConsumption)"
                      class="px-2 py-0.5 rounded-full text-sm font-semibold">
                  {{ consumptionDeltaLabel(displayConsumption, heroOfficialConsumption) }}
                </span>
              </div>
            </template>
          </div>

          <!-- Trust-Strip: Icon inline im Text, damit es auf Mobile sauber umbricht -->
          <p v-if="displayConsumption" class="text-sm text-gray-700 dark:text-gray-300 mb-4 text-center max-w-md mx-auto">
            <span class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-green-600 text-white border-2 border-gray-800 dark:border-gray-200 align-middle mr-1.5">
              <CheckBadgeIcon class="h-3 w-3" />
            </span>{{ t('model.hero_trust', { sessions: ((selectedVariant?.realConsumptionTripCount ?? stats.logCount) || 0).toLocaleString() }) }}
          </p>

          <!-- No data for selected variant -->
          <div v-if="variantHasNoData" class="mt-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-600 text-center">
            <p class="text-gray-500 dark:text-gray-400 text-sm">{{ t('model.variant_no_data') }}</p>
          </div>

          <!-- No data notice -->
          <div v-if="stats.logCount === 0" class="mt-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-sm border border-gray-200 dark:border-gray-600">
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
              {{ formatCostPerDistance(stats.avgCostPerKwh * displayConsumption) }}
            </span>
          </div>

          <!-- Secondary stats row: 2-col on mobile, 3-col on desktop -->
          <div class="mt-3 bg-gray-50 dark:bg-gray-800/40 border-2 border-gray-800 dark:border-gray-200 rounded-xl py-4">
            <div class="grid grid-cols-2 md:grid-cols-3">
            <!-- Links: AC/DC auf Mobile, Ladevorgänge auf Desktop -->
            <div class="px-4 text-center">
              <!-- Mobile: AC/DC -->
              <template v-if="true" class="md:hidden">
                <div class="md:hidden flex flex-col gap-1.5 items-center">
                  <template v-if="stats.acAvgCostPerKwh || stats.dcAvgCostPerKwh">
                    <div v-if="stats.acAvgCostPerKwh" class="flex items-center gap-2 whitespace-nowrap">
                      <span class="flex flex-col leading-tight items-start">
                        <span class="text-sm font-semibold text-green-600 dark:text-green-400 flex items-center gap-0.5"><BoltIcon class="h-4 w-4" />AC</span>
                        <span class="text-[10px] font-medium uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('model.charging_ac_label') }}</span>
                      </span>
                      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerKwh(stats.acAvgCostPerKwh) }}<sup class="text-xs text-gray-400">*</sup></span>
                    </div>
                    <div v-if="stats.dcAvgCostPerKwh" class="flex items-center gap-2 whitespace-nowrap">
                      <span class="flex flex-col leading-tight items-start">
                        <span class="text-sm font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-0.5"><BoltIcon class="h-4 w-4" />DC</span>
                        <span class="text-[10px] font-medium uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('model.charging_dc_label') }}</span>
                      </span>
                      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerKwh(stats.dcAvgCostPerKwh) }}<sup class="text-xs text-gray-400">*</sup></span>
                    </div>
                  </template>
                  <template v-else-if="stats.avgCostPerKwh">
                    <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatCostPerKwh(stats.avgCostPerKwh) }}</div>
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
                  {{ variantHasNoData ? '-' : ((selectedVariant?.realConsumptionTripCount ?? stats.logCount) > 0 ? (selectedVariant?.realConsumptionTripCount ?? stats.logCount).toLocaleString() : '-') }}
                </div>
                <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mt-0.5">{{ t('model.metrics_sessions') }}</div>
              </div>
            </div>
            <!-- Reichweite -->
            <div class="px-4 text-center">
              <template v-if="displayRange">
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ formatDistance(displayRange) }}</div>
                <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mt-0.5">{{ t('model.metrics_range') }}</div>
                <div class="mt-1.5">
                  <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 text-xs font-medium">
                    <Battery0Icon class="h-3.5 w-3.5" /> 90% → 10%
                  </span>
                </div>
              </template>
              <template v-else>
                <div class="text-xl font-bold text-gray-400">-</div>
                <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mt-0.5">{{ t('model.metrics_range_label') }}</div>
              </template>
            </div>
            <!-- Kosten: nur auf Desktop als 3. Spalte -->
            <div class="hidden md:block px-4 text-center">
              <template v-if="stats.acAvgCostPerKwh || stats.dcAvgCostPerKwh">
                <div class="flex flex-col gap-2 items-start w-fit mx-auto">
                  <div v-if="stats.acAvgCostPerKwh" class="flex items-center gap-2.5">
                    <span class="flex flex-col leading-tight w-12">
                      <span class="text-sm font-semibold text-green-600 dark:text-green-400 flex items-center gap-0.5"><BoltIcon class="h-4 w-4" />AC</span>
                      <span class="text-[10px] font-medium uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('model.charging_ac_label') }}</span>
                    </span>
                    <span class="text-xl font-bold text-gray-900 dark:text-gray-100">
                      {{ formatCostPerKwh(stats.acAvgCostPerKwh) }}<sup class="text-xs text-gray-400">*</sup>
                    </span>
                  </div>
                  <div v-if="stats.dcAvgCostPerKwh" class="flex items-center gap-2.5">
                    <span class="flex flex-col leading-tight w-12">
                      <span class="text-sm font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-0.5"><BoltIcon class="h-4 w-4" />DC</span>
                      <span class="text-[10px] font-medium uppercase tracking-wide text-gray-400 dark:text-gray-500">{{ t('model.charging_dc_label') }}</span>
                    </span>
                    <span class="text-xl font-bold text-gray-900 dark:text-gray-100">
                      {{ formatCostPerKwh(stats.dcAvgCostPerKwh) }}<sup class="text-xs text-gray-400">*</sup>
                    </span>
                  </div>
                </div>
              </template>
              <template v-else-if="stats.avgCostPerKwh">
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
                  {{ formatCostPerKwh(stats.avgCostPerKwh) }}
                </div>
              </template>
              <template v-else>
                <div class="text-xl font-bold text-gray-400">-</div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.metrics_costs') }}</div>
              </template>
            </div>
            </div><!-- end grid -->

            <!-- Ladevorgänge: Mobile als eigene Zeile -->
            <div class="md:hidden mt-3 pt-3 border-t border-gray-300 dark:border-gray-600 text-center">
              <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ variantHasNoData ? '-' : ((selectedVariant?.realConsumptionTripCount ?? stats.logCount) > 0 ? (selectedVariant?.realConsumptionTripCount ?? stats.logCount).toLocaleString() : '-') }}
              </div>
              <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mt-0.5">{{ t('model.metrics_sessions') }}</div>
            </div>
          </div><!-- end stats wrapper -->

          <!-- Kostenrechner: persönliche Ladekosten mit eigenem Stromtarif -->
          <div v-if="displayConsumption" class="mt-6 pt-5 border-t border-gray-100 dark:border-gray-700">
            <!-- Intro: macht klar, dass man hier seinen eigenen Tarif einstellt -->
            <div class="text-center mb-4">
              <p class="text-lg font-bold text-gray-800 dark:text-gray-200 flex items-center justify-center gap-1.5">
                <BoltIcon class="h-5 w-5 text-green-600 dark:text-green-400" /> {{ t('model.calculator_title') }}
              </p>
              <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ t('model.calculator_hint') }}</p>
            </div>
            <!-- Live-Ergebnis -->
            <div class="flex items-baseline justify-center gap-2 mb-2 flex-wrap">
              <span class="text-base font-semibold text-gray-700 dark:text-gray-200">{{ formatCostPerKwh(pricePerKwh) }}</span>
              <span class="text-gray-300 dark:text-gray-600">→</span>
              <span class="text-2xl font-extrabold text-green-700 dark:text-green-400">{{ formatCostPerDistance(pricePerKwh * displayConsumption) }}</span>
            </div>
            <!-- Slider -->
            <div class="flex items-center gap-3">
              <span class="hidden sm:inline text-xs font-medium text-gray-500 dark:text-gray-400 shrink-0">{{ formatCostPerKwh(0.10) }}</span>
              <input type="range" min="0.10" max="1.00" step="0.01" v-model.number="pricePerKwh"
                     :aria-label="t('model.calculator_title')"
                     :style="{ '--pct': sliderFillPct + '%' }"
                     class="tariff-slider flex-1 cursor-pointer" />
              <span class="hidden sm:inline text-xs font-medium text-gray-500 dark:text-gray-400 shrink-0">{{ formatCostPerKwh(1.00) }}</span>
            </div>
          </div>
          <!-- Inline-CTA: Registrierung nach allen Argumenten, direkt unter dem Tarif-Slider -->
          <a v-if="displayConsumption && !authStore.isAuthenticated()" :href="registerPath"
             class="block rounded-xl border-2 border-gray-800 dark:border-gray-200 bg-[#14342a] text-white px-5 py-4 mt-6 hover:bg-[#16392d] transition-colors">
            <div class="flex flex-col sm:flex-row items-center justify-between gap-3">
              <div class="text-center sm:text-left">
                <p class="font-bold">{{ t('model.hero_cta_title', { model: stats.modelDisplayName }) }}</p>
                <p class="text-sm text-green-100/80">{{ t('model.hero_cta_desc') }}</p>
              </div>
              <span class="btn-3d shrink-0 bg-green-500 text-gray-900 font-bold px-5 py-2.5 rounded-sm border-2 border-white whitespace-nowrap" style="--btn-shadow-color:#0a1f17">
                {{ t('model.cta_free_start') }} →
              </span>
            </div>
          </a>
        </div><!-- end Hero -->

        <!-- Community methodology note (Desktop; auf Mobile steckt sie im Details-Akkordeon) -->
        <div class="hidden md:block px-4 md:px-0 py-3 border-t border-gray-100 dark:border-gray-700 md:border-0 text-center">
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('model.community_methodology_note') }}</p>
        </div>
        <!-- Cost disclaimer for non-EUR countries -->
        <div v-if="!isEurZone && (stats.avgCostPerKwh || stats.acAvgCostPerKwh)" class="px-4 md:px-0 mt-1 mb-3 text-center">
          <p class="text-xs text-gray-400 dark:text-gray-500 italic">
            {{ t('model.cost_disclaimer', { rate: EUR_EXCHANGE_RATES[currency], currency: currencySymbol, date: RATES_LAST_UPDATED }) }}
          </p>
        </div>

        <!-- Affiliate Banner -->
        <div v-if="!authStore.isAuthenticated()" class="my-6 md:my-8">
          <AffiliateBanner />
        </div>

        <!-- Baujahr-Verteilung -->
        <div v-if="stats.yearDistribution && stats.yearDistribution.length > 0"
             class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-t-2 md:border-b-2 border-gray-800 dark:border-gray-200 px-6 py-5 md:mb-6">
          <p class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-4 flex items-center gap-1.5">
            <ChartBarIcon class="h-4 w-4 text-gray-400" />
            {{ t('model.year_distribution_title', { count: stats.uniqueCars }) }}
          </p>
          <!-- Säulen-Histogramm: eine Farbe, chronologisch - sofort als Verteilung lesbar -->
          <div class="flex items-end gap-1.5 sm:gap-2">
            <div v-for="entry in stats.yearDistribution" :key="entry.year" class="flex-1 flex flex-col items-center">
              <span class="text-xs font-bold text-gray-700 dark:text-gray-300 mb-1 tabular-nums">{{ entry.carCount }}</span>
              <div class="w-full bg-green-500 rounded-t border border-b-0 border-gray-800/10 dark:border-gray-200/10" :style="{ height: yearBarHeightPx(entry.carCount) }"></div>
            </div>
          </div>
          <div class="flex gap-1.5 sm:gap-2 mt-1.5">
            <div v-for="entry in stats.yearDistribution" :key="entry.year" class="flex-1 text-center text-xs text-gray-500 dark:text-gray-400 tabular-nums">{{ entry.year }}</div>
          </div>
          <p v-if="medianModelYear" class="mt-4 text-sm text-gray-600 dark:text-gray-300 text-center">
            {{ t('model.year_distribution_summary', { year: medianModelYear }) }}
          </p>
        </div>

        <!-- Streckentyp-Verteilung -->
        <div v-if="showRouteTypeBar"
             class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-t-2 md:border-b-2 border-gray-800 dark:border-gray-200 px-6 py-5 md:mb-6">
          <p class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-4 flex items-center gap-1.5">
            <ChartBarIcon class="h-4 w-4 text-gray-400" />
            {{ t('model.route_type_title') }}
          </p>
          <div class="flex flex-col gap-2.5">
            <div class="flex h-4 rounded-full overflow-hidden">
              <div
                v-for="entry in routeTypeClassified"
                :key="entry.routeType"
                :style="{
                  width: (entry.count / routeTypeClassifiedTotal * 100) + '%',
                  backgroundColor: ROUTE_TYPE_META[entry.routeType]?.color,
                }"
              />
            </div>
            <div class="flex flex-wrap gap-x-4 gap-y-1.5">
              <div v-for="entry in routeTypeClassified" :key="entry.routeType"
                   class="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-300">
                <span class="inline-block w-2.5 h-2.5 rounded-full shrink-0"
                      :style="{ backgroundColor: ROUTE_TYPE_META[entry.routeType]?.color }"></span>
                <span class="font-medium">{{ t(ROUTE_TYPE_META[entry.routeType]?.labelKey) }}</span>
                <span class="text-gray-400 dark:text-gray-500">
                  {{ Math.round(entry.count / routeTypeClassifiedTotal * 100) }}%
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Variant Switcher + Seasonal + WLTP -->
        <div v-if="activeVariants.length > 0 || showSeasonalBreakdown"
             class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-t-2 md:border-b-2 border-gray-800 dark:border-gray-200 md:mb-6 overflow-hidden">

          <!-- Saison-Karte: einziger Ort mit Sommer-vs-Winter (Hero zeigt das nicht) -->
          <div v-if="showSeasonalBreakdown" class="px-4 md:px-6 py-5">
            <div class="flex items-center justify-center gap-2 mb-4 flex-wrap">
              <ChartBarIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
              <h2 class="text-base font-bold text-gray-800 dark:text-gray-200">{{ t('model.seasonal_title_range') }}</h2>
              <span class="text-sm text-gray-500 dark:text-gray-400 font-medium">· {{ selectedVariant!.displayLabel || selectedVariant!.variantName || (selectedVariant!.batteryCapacityKwh + ' kWh') }}</span>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <!-- Sommer: Reichweite primär, Verbrauch sekundär -->
              <div class="border-2 border-gray-800 dark:border-gray-200 rounded-xl bg-amber-50 dark:bg-amber-900/10 p-4 text-center">
                <div class="flex items-center justify-center gap-1.5 text-xs font-bold uppercase tracking-wide text-amber-600 dark:text-amber-400 mb-1">
                  <SunIcon class="h-4 w-4" /> {{ t('model.seasonal_summer') }}
                </div>
                <div v-if="seasonalSummerRangeKm" class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 tabular-nums leading-none">~ {{ formatDistance(seasonalSummerRangeKm) }}</div>
                <div v-else class="text-2xl font-extrabold text-gray-400 leading-none">-</div>
                <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mt-2">{{ formatConsumption(selectedVariant!.seasonalDistribution!.summerConsumptionKwhPer100km) }}</div>
                <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ selectedVariant!.seasonalDistribution!.summerLogCount }} {{ t('model.seasonal_trips') }}</div>
              </div>

              <!-- Winter: Reichweite primär, Verbrauch sekundär -->
              <div class="border-2 border-gray-800 dark:border-gray-200 rounded-xl bg-blue-50 dark:bg-blue-900/10 p-4 text-center">
                <div class="flex items-center justify-center gap-1.5 text-xs font-bold uppercase tracking-wide text-blue-600 dark:text-blue-400 mb-1">
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <line x1="12" y1="2" x2="12" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/>
                    <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/><line x1="19.07" y1="4.93" x2="4.93" y2="19.07"/>
                    <circle cx="12" cy="12" r="2" fill="currentColor"/>
                  </svg> {{ t('model.seasonal_winter') }}
                </div>
                <div v-if="seasonalWinterRangeKm" class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 tabular-nums leading-none">~ {{ formatDistance(seasonalWinterRangeKm) }}</div>
                <div v-else class="text-2xl font-extrabold text-gray-400 leading-none">-</div>
                <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mt-2">{{ formatConsumption(selectedVariant!.seasonalDistribution!.winterConsumptionKwhPer100km) }}</div>
                <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{{ selectedVariant!.seasonalDistribution!.winterLogCount }} {{ t('model.seasonal_trips') }}</div>
              </div>

              <!-- Winter-Aufschlag: Reichweitenverlust primär (die Pointe) -->
              <div class="border-2 border-gray-800 dark:border-gray-200 rounded-xl bg-gray-50 dark:bg-gray-800/40 p-4 text-center flex flex-col justify-center">
                <div class="text-xs font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400 mb-1">{{ t('model.seasonal_winter_penalty') }}</div>
                <div v-if="seasonalRangeLossKm" class="text-3xl font-extrabold text-red-600 dark:text-red-400 tabular-nums leading-none">- {{ formatDistance(seasonalRangeLossKm) }}</div>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.seasonal_less_range') }}</div>
                <div v-if="winterExtraPercent != null" class="text-base font-bold text-gray-700 dark:text-gray-300 mt-2">+{{ winterExtraPercent }} % {{ t('model.seasonal_more_consumption') }}</div>
              </div>
            </div>
          </div>

          <!-- Details & Methodik (eingeklappt) -->
          <details v-if="activeVariants.length > 0" :open="!showSeasonalBreakdown" class="group" :class="showSeasonalBreakdown ? 'border-t-2 border-gray-800/15 dark:border-gray-200/15' : ''">
            <summary class="flex items-center justify-center gap-2 px-6 py-4 text-sm font-semibold text-green-700 dark:text-green-400 cursor-pointer select-none list-none [&::-webkit-details-marker]:hidden">
              <ClipboardDocumentListIcon class="h-5 w-5" />
              {{ showSeasonalBreakdown ? t('model.details_methodology') : (ratingLabel === 'EPA' ? t('model.epa_section_title') : t('model.wltp_section_title')) }}
              <ChevronRightIcon class="h-4 w-4 transition-transform group-open:rotate-90" />
            </summary>

            <!-- US fallback: no EPA data yet for this model -->
            <p v-if="isUS && ratingLabel !== 'EPA'"
               class="mx-6 mb-3 text-xs text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-sm px-3 py-2">
              {{ t('model.epa_not_available') }}
            </p>

            <!-- WLTP vs. Community als 2 Profil-Cards, je Reichweite zuerst -->
            <div v-if="selectedVariant" class="px-4 md:px-6 pb-2">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <!-- WLTP-Card -->
                <div class="border-2 border-gray-800 dark:border-gray-200 rounded-xl bg-gray-50 dark:bg-gray-800/40 p-4 text-center">
                  <div class="text-xs font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400 mb-3">{{ t('model.compare_manufacturer') }}</div>
                  <div class="space-y-3">
                    <div>
                      <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mb-0.5">{{ ratingLabel === 'EPA' ? t('model.epa_range_label') : t('model.wltp_table_range') }}</div>
                      <div class="text-lg font-bold text-gray-900 dark:text-gray-100 whitespace-nowrap">
                        <template v-if="selectedVariant.officialRangeMinKm">{{ formatDistance(selectedVariant.officialRangeMinKm, { showUnit: false }) }}&thinsp;-&thinsp;{{ formatDistance(selectedVariant.officialRangeKm) }}</template>
                        <template v-else>{{ formatDistance(selectedVariant.officialRangeKm) }}</template>
                      </div>
                    </div>
                    <div>
                      <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mb-0.5">{{ ratingLabel === 'EPA' ? t('model.epa_consumption_label') : t('model.wltp_table_consumption') }}</div>
                      <div class="font-semibold text-gray-800 dark:text-gray-200">
                        <template v-if="selectedVariant.officialConsumptionMinKwhPer100km && selectedVariant.officialConsumptionMaxKwhPer100km">
                          {{ formatConsumption(selectedVariant.officialConsumptionMinKwhPer100km, { showUnit: false }) }}&thinsp;-&thinsp;{{ formatConsumption(selectedVariant.officialConsumptionMaxKwhPer100km) }}
                          <span class="block text-xs font-normal text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.wltp_varies_by_year') }}</span>
                        </template>
                        <template v-else>{{ formatConsumption(selectedVariant.officialConsumptionKwhPer100km) }}</template>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Community-Card (grün, der USP) -->
                <div class="border-2 border-gray-800 dark:border-gray-200 rounded-xl bg-green-50 dark:bg-green-900/20 p-4 text-center">
                  <div class="text-xs font-bold uppercase tracking-wide text-green-700 dark:text-green-400 mb-3">Community · {{ t('model.compare_real') }}</div>
                  <div class="space-y-3">
                    <div>
                      <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mb-0.5">{{ ratingLabel === 'EPA' ? t('model.epa_range_label') : t('model.wltp_table_range') }}</div>
                      <div class="text-lg font-bold text-gray-900 dark:text-gray-100">
                        <div v-if="selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km || selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km" class="flex items-center justify-center gap-1.5 flex-wrap">
                          <span class="flex items-center gap-1 text-amber-600 dark:text-amber-400"><SunIcon class="h-4 w-4" /><span>{{ selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km ? formatDistance(Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km * 10) * 10) : '-' }}</span></span>
                          <span class="text-gray-300 dark:text-gray-600">/</span>
                          <span class="flex items-center gap-1 text-blue-600 dark:text-blue-400">
                            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                              <line x1="12" y1="2" x2="12" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/>
                              <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/><line x1="19.07" y1="4.93" x2="4.93" y2="19.07"/>
                              <circle cx="12" cy="12" r="2" fill="currentColor"/>
                            </svg><span>{{ selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km ? formatDistance(Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km * 10) * 10) : '-' }}</span>
                          </span>
                        </div>
                        <template v-else>{{ selectedVariant.realConsumptionKwhPer100km ? formatDistance(Math.round(selectedVariant.batteryCapacityKwh / selectedVariant.realConsumptionKwhPer100km * 10) * 10) : '-' }}</template>
                      </div>
                      <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('model.wltp_full_range') }}</div>
                    </div>
                    <div>
                      <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mb-0.5">{{ ratingLabel === 'EPA' ? t('model.epa_consumption_label') : t('model.wltp_table_consumption') }}</div>
                      <template v-if="selectedVariant.realConsumptionKwhPer100km">
                        <div class="flex flex-wrap items-center justify-center gap-2">
                          <span class="font-bold text-gray-900 dark:text-gray-100 whitespace-nowrap">{{ formatConsumption(selectedVariant.realConsumptionKwhPer100km) }}</span>
                          <span :class="deltaLabelClass(selectedVariant.realConsumptionKwhPer100km, selectedVariant.officialConsumptionKwhPer100km)" class="text-xs px-1.5 py-0.5 rounded-full">{{ consumptionDeltaLabel(selectedVariant.realConsumptionKwhPer100km, selectedVariant.officialConsumptionKwhPer100km) }}</span>
                          <span v-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 10" class="text-xs px-1.5 py-0.5 rounded-full bg-red-50 border border-red-200 text-red-600 dark:bg-red-900/40 dark:border-red-700 dark:text-red-400">⚠ {{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}</span>
                          <span v-else-if="selectedVariant.realConsumptionTripCount != null && selectedVariant.realConsumptionTripCount < 50" class="text-xs px-1.5 py-0.5 rounded-full bg-yellow-50 border border-yellow-200 text-yellow-700 dark:bg-yellow-900/40 dark:border-yellow-700 dark:text-yellow-400">{{ selectedVariant.realConsumptionTripCount }} {{ t('model.seasonal_trips') }}</span>
                        </div>
                      </template>
                      <span v-else class="text-gray-400">{{ t('model.wltp_no_data') }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Rating Notes -->
            <div class="px-6 py-4 space-y-2 text-center">
              <p class="text-sm text-gray-400 dark:text-gray-400">{{ ratingLabel === 'EPA' ? t('model.epa_note') : t('model.wltp_note') }}</p>
              <p class="text-sm text-gray-400 dark:text-gray-400">{{ ratingLabel === 'EPA' ? t('model.epa_measurement_note') : t('model.wltp_measurement_note') }}</p>
              <!-- Methodik-Absatz nur auf Mobile hier (Desktop steht er unter der Hero-Karte) -->
              <p class="md:hidden text-sm text-gray-400 dark:text-gray-400">{{ t('model.community_methodology_note') }}</p>
            </div>
          </details><!-- end wltp -->

        </div><!-- end combined card -->

        <!-- AC Fußnote -->
        <div v-if="stats.acAvgCostPerKwh" class="px-4 md:px-0 mt-2 mb-3 text-center">
          <p class="text-sm text-gray-400 dark:text-gray-400">{{ t('model.ac_footnote') }}</p>
        </div>

        <!-- CTA -->
        <div class="bg-gradient-to-br from-green-600 to-green-700 border-y-2 md:border-2 border-gray-800 dark:border-gray-200 md:rounded-xl md:shadow-[5px_5px_0_0_#1f2937] dark:md:shadow-[5px_5px_0_0_#1f2937] p-6 text-white">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="text-xl font-bold">{{ t('model.cta_title') }}</h2>
          </div>
          <p class="text-green-100 mb-4">{{ t('model.cta_desc') }}</p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath" class="btn-3d bg-white text-green-700 font-bold px-5 py-2.5 rounded-sm border-2 border-gray-800 hover:bg-green-50 transition-colors" style="--btn-shadow-color:#1f2937">
              {{ t('model.cta_free_start') }}
            </a>
            <a :href="loginPath" class="border-2 border-white text-white font-semibold px-5 py-2.5 rounded-sm hover:bg-green-600 transition-colors">
              {{ t('model.cta_login') }}
            </a>
          </div>
        </div>

        <!-- SEO Text -->
        <div class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-y-2 border-gray-800 dark:border-gray-200 p-6 md:mt-6">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('model.seo_section_title', { model: stats.modelDisplayName }) }}
          </h2>
          <div class="space-y-4 text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
            <p>
              {{ t('model.seo_intro', { model: stats.modelDisplayName }) }}
              <template v-if="bestOfficialRange"> {{ t('model.seo_wltp_range', { range: formatDistance(bestOfficialRange), ratingLabel }) }}</template>.
              <template v-if="stats.avgConsumptionKwhPer100km">
                <template v-if="consumptionDataQuality === 'good'">
                  {{ t('model.seo_consumption_good', { consumption: formatConsumption(stats.avgConsumptionKwhPer100km), sessions: stats.logCount, count: Math.min(consumptionDataCount, stats.logCount) }) }}
                </template>
                <template v-else-if="consumptionDataQuality === 'low'">
                  {{ t('model.seo_consumption_low', { consumption: formatConsumption(stats.avgConsumptionKwhPer100km), count: Math.min(consumptionDataCount, stats.logCount) }) }}
                </template>
                <template v-else>
                  {{ t('model.seo_consumption_sparse', { consumption: formatConsumption(stats.avgConsumptionKwhPer100km), count: Math.min(consumptionDataCount, stats.logCount) }) }}
                </template>
              </template>
              <template v-else>
                {{ t('model.seo_no_data_cta', { model: stats.modelDisplayName }) }}
              </template>
            </p>
            <div v-if="stats.avgCostPerKwh">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_costs_title') }}</h3>
              <p>
                {{ t('model.seo_costs_intro', { model: stats.modelDisplayName, price: formatCostPerKwh(stats.avgCostPerKwh) }) }}
                <template v-if="stats.avgKwhPerSession">
                  {{ t('model.seo_costs_session', { kwh: stats.avgKwhPerSession.toFixed(1), cost: formatCurrency(stats.avgCostPerKwh * stats.avgKwhPerSession) }) }}
                </template>
              </p>
            </div>
            <div v-if="activeVariants.length > 0 && consumptionDataCount >= 25">
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_wltp_title', { ratingLabel }) }}</h3>
              <p>
                {{ t('model.seo_wltp_intro', { ratingLabel }) }}
                <template v-if="stats.avgConsumptionKwhPer100km && worstOfficialConsumption">
                  {{ t('model.seo_wltp_delta', { delta: wltpDeltaPercent, ratingLabel }) }}
                </template>
              </p>
            </div>
            <div>
              <h3 class="font-semibold text-gray-800 dark:text-gray-200 mb-1">{{ t('model.seo_seasonal_title') }}</h3>
              <p v-if="showSeasonalBreakdown && selectedVariant?.seasonalDistribution?.summerConsumptionKwhPer100km && selectedVariant?.seasonalDistribution?.winterConsumptionKwhPer100km">
                {{ t('model.seo_seasonal_data', {
                  model: stats.modelDisplayName,
                  summer: formatConsumption(selectedVariant.seasonalDistribution.summerConsumptionKwhPer100km),
                  winter: formatConsumption(selectedVariant.seasonalDistribution.winterConsumptionKwhPer100km),
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
        <div v-if="faqItems.length > 0" class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-y-2 border-gray-800 dark:border-gray-200 p-6 mt-6">
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">
            {{ t('model.faq_title', { model: stats.modelDisplayName }) }}
          </h2>
          <div class="space-y-3">
            <details v-for="(faq, i) in faqItems" :key="i"
                     class="border border-gray-100 dark:border-gray-700 rounded-sm overflow-hidden">
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
      <div class="bg-white dark:bg-gray-800 md:rounded-xl md:border-x-2 md:shadow-[5px_5px_0_0_#15803d] dark:md:shadow-[5px_5px_0_0_#22c55e] border-y-2 border-gray-800 dark:border-gray-200 p-6">
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
      <div v-if="!isAuthenticated" class="flex justify-center mb-4">
        <RegionChip />
      </div>
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
          class="back-pill btn-3d-delay inline-flex items-center gap-1.5 text-sm font-semibold text-white bg-green-600 hover:bg-green-700 rounded-full px-4 py-2 shadow-[4px_4px_0_rgba(0,0,0,0.30)] dark:shadow-[4px_4px_0_rgba(255,255,255,0.30)]"
        >
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="h-4 w-4">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
          </svg>
          Zurück
        </button>
      </div>
    </Teleport>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useTickerState } from '../composables/useTickerState'
import { useCountryStore } from '../stores/country'
import { getModelStats, type PublicModelStats, type SeasonalDistribution } from '../api/publicModelService'
import {
  ArrowTrendingUpIcon, ClipboardDocumentListIcon, Battery0Icon,
  SunIcon, ChartBarIcon, ExclamationTriangleIcon, BoltIcon, CheckBadgeIcon, ChevronRightIcon
} from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import GridRippleBackground from '../components/shared/GridRippleBackground.vue'
import AffiliateBanner from '../components/shared/AffiliateBanner.vue'
import RegionChip from '../components/shared/RegionChip.vue'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useMarketRoute, getMarketBasePath, OG_LOCALE, MARKET_HTML_LANG } from '../composables/useMarketRoute'
import { EUR_EXCHANGE_RATES, RATES_LAST_UPDATED } from '../config/exchangeRates'

/** Normalized variant - same shape regardless of WLTP or EPA source */
interface ActiveVariant {
  batteryCapacityKwh: number
  variantName: string | null
  displayLabel: string | null
  officialRangeKm: number
  officialRangeMinKm: number | null
  officialConsumptionKwhPer100km: number | null
  officialConsumptionMinKwhPer100km: number | null
  officialConsumptionMaxKwhPer100km: number | null
  realConsumptionKwhPer100km: number | null
  realConsumptionMinKwhPer100km: number | null
  realConsumptionMaxKwhPer100km: number | null
  realConsumptionTripCount: number | null
  estimatedConsumptionCount: number | null
  realConsumptionRangeSource: 'PER_DRIVER' | 'PER_TRIP' | null
  seasonalDistribution: SeasonalDistribution | null
}

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { formatConsumption, formatNumber, consumptionUnitLabel, convertConsumption, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, consumptionDeltaLabel, isEurZone, currency, currencySymbol, unitSystem } = useLocaleFormat()
const { currentMarket, isDE, isEN, isGB, marketUrl, hreflangLinks } = useMarketRoute()
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))
const authStore = useAuthStore()
const countryStore = useCountryStore()
const loading = ref(true)
const notFound = ref(false)
const apiError = ref(false)
const showBackPill = ref(false)
const stats = ref<PublicModelStats | null>(null)
const selectedVariantIndex = ref(0)
const pricePerKwh = ref(countryStore.country === 'US' ? 0.13 : 0.35)
// Füllstand des Tarif-Sliders (0.10-1.00 €/kWh) für den grünen Fortschrittsbalken
const sliderFillPct = computed(() => Math.round((pricePerKwh.value - 0.10) / 0.90 * 100))

const isAuthenticated = computed(() => authStore.isAuthenticated())
const { tickerHasItems, tickerCollapsed } = useTickerState()
const stickyTop = computed(() => {
  if (!authStore.isAuthenticated()) return '0px'
  if (tickerHasItems.value && !tickerCollapsed.value) return '90px'
  return '64px'
})

// Höchste Fahrzeugzahl eines Baujahrs - Basis für die Säulenhöhe im Histogramm
const maxYearCount = computed(() =>
  Math.max(1, ...(stats.value?.yearDistribution ?? []).map(e => e.carCount))
)
const yearBarHeightPx = (count: number) =>
  `${Math.max(4, Math.round(count / maxYearCount.value * 88))}px`
// Gewichteter Median des Baujahrs - das "typische" Baujahr der Community-Flotte
const medianModelYear = computed(() => {
  const dist = stats.value?.yearDistribution
  if (!dist || dist.length === 0) return null
  const sorted = [...dist].sort((a, b) => a.year - b.year)
  const total = sorted.reduce((s, e) => s + e.carCount, 0)
  let cum = 0
  for (const e of sorted) {
    cum += e.carCount
    if (cum >= total / 2) return e.year
  }
  return sorted[sorted.length - 1].year
})

const ROUTE_TYPE_META: Record<string, { color: string; labelKey: string }> = {
  HIGHWAY:  { color: '#3b82f6', labelKey: 'model.route_type_highway' },
  COMBINED: { color: '#22c55e', labelKey: 'model.route_type_combined' },
  CITY:     { color: '#f59e0b', labelKey: 'model.route_type_city' },
}

const routeTypeClassified = computed(() =>
  ['HIGHWAY', 'COMBINED', 'CITY'].flatMap(type => {
    const entry = (stats.value?.routeTypeDistribution ?? []).find(e => e.routeType === type)
    return entry ? [entry] : []
  })
)

const showRouteTypeBar = computed(() => {
  const dist = stats.value?.routeTypeDistribution ?? []
  const total = dist.reduce((s, e) => s + e.count, 0)
  const classified = dist.filter(e => e.routeType !== 'UNKNOWN').reduce((s, e) => s + e.count, 0)
  return total > 0 && classified / total >= 0.5
})

const routeTypeClassifiedTotal = computed(() =>
  routeTypeClassified.value.reduce((s, e) => s + e.count, 0)
)

const brand = route.params.brand as string
const model = route.params.model as string

const canonicalBrand = computed(() => stats.value?.brandDisplayName ?? brand)
// canonicalModelSlug unused in V2 (no redirect logic)

// US wenn: /us/models Route (previewCountry) ODER User hat dauerhaft US gesetzt
const isUS = computed(() => currentMarket.value === 'us' || (countryStore.previewCountry ?? countryStore.country) === 'US')
const loginPath = computed(() => (isEN.value || isGB.value || isUS.value) ? '/en/login' : '/login')
const registerPath = computed(() => (isEN.value || isGB.value || isUS.value) ? '/en/register' : '/register')
const ratingLabel = computed(() =>
  isUS.value && (stats.value?.epaVariants?.length ?? 0) > 0 ? 'EPA' : 'WLTP'
)

// Reset variant selection when switching between EPA and WLTP (e.g. user changes Country)
watch(ratingLabel, () => { selectedVariantIndex.value = 0 })

const activeVariants = computed<ActiveVariant[]>(() => {
  if (!stats.value) return []
  if (isUS.value && stats.value.epaVariants?.length) {
    return stats.value.epaVariants.map(v => ({
      batteryCapacityKwh: v.batteryCapacityKwh,
      variantName: v.variantName,
      displayLabel: v.displayLabel,
      officialRangeKm: v.epaRangeKm,
      officialRangeMinKm: null,
      officialConsumptionKwhPer100km: v.epaConsumptionKwhPer100km,
      officialConsumptionMinKwhPer100km: v.epaConsumptionMinKwhPer100km,
      officialConsumptionMaxKwhPer100km: v.epaConsumptionMaxKwhPer100km,
      realConsumptionKwhPer100km: v.realConsumptionKwhPer100km,
      realConsumptionMinKwhPer100km: v.realConsumptionMinKwhPer100km,
      realConsumptionMaxKwhPer100km: v.realConsumptionMaxKwhPer100km,
      realConsumptionTripCount: v.realConsumptionTripCount,
      estimatedConsumptionCount: v.estimatedConsumptionCount,
      realConsumptionRangeSource: v.realConsumptionRangeSource,
      seasonalDistribution: v.seasonalDistribution,
    }))
  }
  return stats.value.wltpVariants.map(v => ({
    batteryCapacityKwh: v.batteryCapacityKwh,
    variantName: v.variantName,
    displayLabel: v.displayLabel,
    officialRangeKm: v.wltpRangeKm,
    officialRangeMinKm: v.wltpRangeMinKm,
    officialConsumptionKwhPer100km: v.wltpConsumptionKwhPer100km,
    officialConsumptionMinKwhPer100km: v.wltpConsumptionMinKwhPer100km,
    officialConsumptionMaxKwhPer100km: v.wltpConsumptionMaxKwhPer100km,
    realConsumptionKwhPer100km: v.realConsumptionKwhPer100km,
    realConsumptionMinKwhPer100km: v.realConsumptionMinKwhPer100km,
    realConsumptionMaxKwhPer100km: v.realConsumptionMaxKwhPer100km,
    realConsumptionTripCount: v.realConsumptionTripCount,
    estimatedConsumptionCount: v.estimatedConsumptionCount,
    realConsumptionRangeSource: v.realConsumptionRangeSource,
    seasonalDistribution: v.seasonalDistribution,
  }))
})

const selectedVariant = computed(() => activeVariants.value[selectedVariantIndex.value] ?? null)

const variantHasNoData = computed(() =>
  activeVariants.value.length > 1 && !selectedVariant.value?.realConsumptionKwhPer100km
)

const displayConsumption = computed(() => {
  const variantConsumption = selectedVariant.value?.realConsumptionKwhPer100km
  if (variantConsumption != null) return variantConsumption
  // Only fall back to model average when no variant switcher is shown (single variant)
  if (activeVariants.value.length > 1) return null
  return stats.value?.avgConsumptionKwhPer100km ?? null
})

const displayRange = computed(() => {
  if (!selectedVariant.value || !displayConsumption.value) return null
  return Math.round(selectedVariant.value.batteryCapacityKwh * 0.9 / displayConsumption.value * 10) * 10
})

const bestOfficialRange = computed(() => {
  if (!activeVariants.value.length) return null
  return Math.max(...activeVariants.value.map(v => v.officialRangeKm))
})

const wltpDeltaPercent = computed(() => {
  if (!stats.value?.avgConsumptionKwhPer100km || !worstOfficialConsumption.value) return null
  const real = stats.value.avgConsumptionKwhPer100km
  const official = worstOfficialConsumption.value
  // For mi/kWh: positive = more efficient (more miles per kWh = better)
  // For kWh/100km: negative = more efficient (less energy = better)
  const pct = unitSystem.value.consumptionInverse
    ? (official - real) / real * 100
    : (real - official) / official * 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(0)}%`
})

const worstOfficialConsumption = computed(() => {
  if (!activeVariants.value.length) return null
  const values = activeVariants.value.map(v => v.officialConsumptionKwhPer100km).filter((v): v is number => v !== null)
  return values.length ? Math.max(...values) : null
})

// Use selected variant's WLTP for the hero badge - falls back to worst-across-all when no variant selected
const heroOfficialConsumption = computed(() =>
  selectedVariant.value?.officialConsumptionKwhPer100km ?? worstOfficialConsumption.value
)

const consumptionDataCount = computed(() => {
  if (!stats.value) return 0
  const socCount = activeVariants.value.reduce((sum, v) => sum + (v.realConsumptionTripCount ?? 0), 0)
  return socCount + (stats.value.estimatedConsumptionCount ?? 0)
})

// ── SEO-Title-Werte: modell-level & deterministisch (unabhaengig vom Varianten-Switcher) ──
// Verbrauch = Modell-Schnitt (immer befuellt, sobald Community-Daten existieren). Bewusst
// NICHT displayConsumption: das ist variantenabhaengig und bei Multi-Varianten oft null.
const metaConsumption = computed(() => stats.value?.avgConsumptionKwhPer100km ?? null)

// Real-world-Reichweite je Variante (Akku x 0,9 / Real-Verbrauch, sonst Modell-Schnitt),
// als Min/Max-Span ueber alle Varianten - rundet auf 10 km wie die Hero-Anzeige.
const metaRealRangeLabel = computed(() => {
  const avg = metaConsumption.value
  if (!avg || !activeVariants.value.length) return null
  const ranges = activeVariants.value.map(v =>
    Math.round(v.batteryCapacityKwh * 0.9 / (v.realConsumptionKwhPer100km ?? avg) * 10) * 10
  )
  const unit = distanceUnitLabel()
  const minStr = formatDistance(Math.min(...ranges), { showUnit: false })
  const maxStr = formatDistance(Math.max(...ranges), { showUnit: false })
  return minStr === maxStr ? `${maxStr} ${unit}` : `${minStr}-${maxStr} ${unit}`
})

// WLTP-Range-Fallback fuer Modelle ohne Community-Verbrauch (immer aus Specs vorhanden).
const metaWltpRangeLabel = computed(() =>
  bestOfficialRange.value ? formatDistance(bestOfficialRange.value) : null
)

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

// Saison-Reichweiten (90→10%, wie im Hero) und der Winter-Aufschlag - die einzigen
// Werte, die die Hero-Karte NICHT zeigt, daher der Kern dieser Karte.
const seasonalSummerRangeKm = computed(() => {
  const s = selectedVariant.value?.seasonalDistribution
  const b = selectedVariant.value?.batteryCapacityKwh
  if (!s?.summerConsumptionKwhPer100km || !b) return null
  return Math.round(b * 0.9 / s.summerConsumptionKwhPer100km * 10) * 10
})
const seasonalWinterRangeKm = computed(() => {
  const s = selectedVariant.value?.seasonalDistribution
  const b = selectedVariant.value?.batteryCapacityKwh
  if (!s?.winterConsumptionKwhPer100km || !b) return null
  return Math.round(b * 0.9 / s.winterConsumptionKwhPer100km * 10) * 10
})
const winterExtraPercent = computed(() => {
  const s = selectedVariant.value?.seasonalDistribution
  if (!s?.summerConsumptionKwhPer100km || !s?.winterConsumptionKwhPer100km) return null
  return Math.round((s.winterConsumptionKwhPer100km / s.summerConsumptionKwhPer100km - 1) * 100)
})
const seasonalRangeLossKm = computed(() => {
  if (seasonalSummerRangeKm.value == null || seasonalWinterRangeKm.value == null) return null
  return seasonalSummerRangeKm.value - seasonalWinterRangeKm.value
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
        consumption: formatConsumption(stats.value.avgConsumptionKwhPer100km),
        dataNote,
        wltp: worstOfficialConsumption.value ? formatConsumption(worstOfficialConsumption.value) : '-',
        ratingLabel: ratingLabel.value
      })
    })
  }

  if (bestOfficialRange.value && stats.value.avgConsumptionKwhPer100km && activeVariants.value.length > 0) {
    const largestBattery = Math.max(...activeVariants.value.map(v => v.batteryCapacityKwh))
    const realRange = Math.round(largestBattery / stats.value.avgConsumptionKwhPer100km * 100)
    items.push({
      question: t('model.faq_q_range', { model: name }),
      answer: t('model.faq_a_range', {
        model: name,
        wltpRange: formatDistance(bestOfficialRange.value),
        battery: largestBattery,
        consumption: formatConsumption(stats.value.avgConsumptionKwhPer100km),
        realRange: formatDistance(realRange),
        ratingLabel: ratingLabel.value
      })
    })
  }

  if (stats.value.avgCostPerKwh && stats.value.avgKwhPerSession) {
    items.push({
      question: t('model.faq_q_cost', { model: name }),
      answer: t('model.faq_a_cost', {
        price: formatCostPerKwh(stats.value.avgCostPerKwh),
        kwh: stats.value.avgKwhPerSession.toFixed(1),
        cost: formatCurrency(stats.value.avgCostPerKwh * stats.value.avgKwhPerSession)
      })
    })
  }

  if (worstOfficialConsumption.value && stats.value.avgConsumptionKwhPer100km && consumptionDataCount.value >= 25) {
    const real = stats.value.avgConsumptionKwhPer100km
    const official = worstOfficialConsumption.value
    // Compute diff and pct in display unit space
    const realDisplay = convertConsumption(real)
    const officialDisplay = convertConsumption(official)
    const diffVal = Math.abs(realDisplay - officialDisplay)
    const diffFormatted = `${diffVal.toFixed(1)} ${consumptionUnitLabel()}`
    const displayPct = unitSystem.value.consumptionInverse
      ? Math.round((official / real - 1) * 100)
      : Math.round((real / official - 1) * 100)
    const pctLabel = (displayPct > 0 ? '+' : '') + displayPct + '%'
    items.push({
      question: t('model.faq_q_wltp_delta', { model: name, ratingLabel: ratingLabel.value }),
      answer: t('model.faq_a_wltp_delta', {
        model: name,
        wltp: formatConsumption(official),
        real: formatConsumption(real),
        diff: diffFormatted,
        pct: pctLabel,
        ratingLabel: ratingLabel.value
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
          winter: seasonal!.winterConsumptionKwhPer100km ? formatConsumption(seasonal!.winterConsumptionKwhPer100km) : '-',
          summer: seasonal!.summerConsumptionKwhPer100km ? formatConsumption(seasonal!.summerConsumptionKwhPer100km) : '-'
        })
      : t('model.faq_a_winter_no_data', { model: name })
  })

  return items
})

const currentYear = new Date().getFullYear()

useHead(computed(() => {
  if (notFound.value) return { title: 'Modell nicht gefunden - EV Monitor', meta: [{ name: 'robots', content: 'noindex, nofollow' }] }
  if (!stats.value) return { title: 'EV Monitor', meta: [{ name: 'robots', content: 'noindex, follow' }] }

  const name = stats.value.modelDisplayName
  // Meta-Tags sind modell-level (nicht variantenabhaengig): sonst widerspricht die Description
  // dem Title bei Multi-Varianten, deren erste Variante keinen Realverbrauch hat (z.B. ID.4).
  const consumption = metaConsumption.value
  const wltp = worstOfficialConsumption.value

  const suffix = `/${canonicalBrand.value}/${model}`
  const canonicalUrl = marketUrl(currentMarket.value, suffix)

  const description = consumption && wltp
    ? t('model.meta_description_with_data', { model: name, consumption: formatConsumption(consumption), wltp: formatConsumption(wltp), ratingLabel: ratingLabel.value, logCount: formatNumber(stats.value.logCount) })
    : t('model.meta_description_no_data', { model: name, ratingLabel: ratingLabel.value })

  // SEO-Title: echte Zahlen (Verbrauch + Reichweite-Span) sind der CTR-Hebel im SERP -
  // kein WLTP-Konkurrent liefert gemessene Community-Werte. Modell-level, damit der Title
  // unabhaengig vom Varianten-Switcher immer befuellt ist. Fallback: WLTP-Range aus Specs.
  const title = metaConsumption.value && metaRealRangeLabel.value
    ? t('model.meta_title_with_data', { model: name, consumption: formatConsumption(metaConsumption.value), range: metaRealRangeLabel.value, year: currentYear })
    : metaWltpRangeLabel.value
      ? t('model.meta_title_wltp', { model: name, range: metaWltpRangeLabel.value, ratingLabel: ratingLabel.value, year: currentYear })
      : t('model.meta_title', { model: name, year: currentYear })

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'EV Monitor', item: 'https://ev-monitor.net' },
      { '@type': 'ListItem', position: 2, name: isDE.value ? 'Elektroautos' : 'Electric Cars', item: marketUrl(currentMarket.value) },
      { '@type': 'ListItem', position: 3, name: stats.value.brandDisplayName, item: marketUrl(currentMarket.value, `/${canonicalBrand.value}`) },
      { '@type': 'ListItem', position: 4, name, item: canonicalUrl },
    ]
  }

  const webPageJsonLd: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'WebPage',
    name: title,
    description,
    url: canonicalUrl,
    author: { '@type': 'Organization', name: 'EV Monitor', url: 'https://ev-monitor.net' },
  }
  if (consumption) {
    webPageJsonLd['about'] = [
      { '@type': 'PropertyValue', name: isDE.value ? 'Realverbrauch' : 'Real Consumption', value: formatConsumption(consumption) },
      ...(wltp ? [{ '@type': 'PropertyValue', name: ratingLabel.value, value: formatConsumption(wltp) }] : []),
    ]
  }

  const faqJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: faqItems.value.map(f => ({
      '@type': 'Question',
      name: f.question,
      acceptedAnswer: { '@type': 'Answer', text: f.answer }
    }))
  }

  const htmlLang = MARKET_HTML_LANG[currentMarket.value]

  return {
    title,
    htmlAttrs: { lang: htmlLang },
    meta: [
      { name: 'description', content: description },
      { name: 'keywords', content: t('model.meta_keywords', { model: name }) },
      // threshold in sync with PublicModelService.SITEMAP_MIN_LOG_COUNT = 25
      { name: 'robots', content: consumptionDataCount.value >= 25 ? 'index, follow' : 'noindex, follow' },
      { property: 'og:title', content: title },
      { property: 'og:description', content: description },
      { property: 'og:type', content: 'article' },
      { property: 'og:url', content: canonicalUrl },
      { property: 'og:locale', content: OG_LOCALE[currentMarket.value] ?? 'en_GB' },
    ],
    link: [
      { rel: 'canonical', href: canonicalUrl },
      ...hreflangLinks(suffix),
    ],
    script: [
      { type: 'application/ld+json', innerHTML: JSON.stringify(breadcrumbJsonLd) },
      { type: 'application/ld+json', innerHTML: JSON.stringify(webPageJsonLd) },
      ...(faqItems.value.length > 0
        ? [{ type: 'application/ld+json', innerHTML: JSON.stringify(faqJsonLd) }]
        : [])
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
      // Select variant with most real-world trips as default
      if (activeVariants.value.length > 1) {
        let maxTrips = -1
        activeVariants.value.forEach((v, i) => {
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

function simpleDeltaClass(real: number, wltp: number): string {
  const pct = (real - wltp) / wltp * 100
  return pct <= 0
    ? 'text-green-600 dark:text-green-400'
    : 'text-red-600 dark:text-red-400'
}

function deltaLabelClass(real: number | null, wltp: number | null): string {
  if (!real || !wltp) return ''
  const pct = ((real - wltp) / wltp) * 100
  if (pct <= 0) return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400'
  if (pct <= 15) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
}

const communityConsumptionRange = computed(() => {
  const min = selectedVariant.value?.realConsumptionMinKwhPer100km
  const max = selectedVariant.value?.realConsumptionMaxKwhPer100km
  if (!min || !max) return null
  return { min, max, rangeSource: selectedVariant.value?.realConsumptionRangeSource ?? null }
})

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

/* Tarif-Slider: grüner Fortschrittsbalken + kräftiger Thumb, statt durchgehend grau */
.tariff-slider {
  -webkit-appearance: none;
  appearance: none;
  height: 10px;
  border-radius: 9999px;
  background: linear-gradient(to right, #16a34a var(--pct, 0%), #e5e7eb var(--pct, 0%));
}
.dark .tariff-slider {
  background: linear-gradient(to right, #22c55e var(--pct, 0%), #4b5563 var(--pct, 0%));
}
.tariff-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 22px;
  height: 22px;
  border-radius: 9999px;
  background: #16a34a;
  border: 3px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  cursor: pointer;
}
.dark .tariff-slider::-webkit-slider-thumb {
  background: #22c55e;
  border-color: #1f2937;
}
.tariff-slider::-moz-range-thumb {
  width: 22px;
  height: 22px;
  border-radius: 9999px;
  background: #16a34a;
  border: 3px solid #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  cursor: pointer;
}
.dark .tariff-slider::-moz-range-thumb {
  background: #22c55e;
  border-color: #1f2937;
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
