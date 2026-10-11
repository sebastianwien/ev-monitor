<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <div class="model-page">
    <PublicNav />
    <main class="max-w-4xl mx-auto md:px-4 py-2 md:py-8">

      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-green-600"></div>
      </div>

      <div v-else-if="notFound" class="text-center py-20">
        <div class="text-5xl mb-4">🔍</div>
        <h1 class="t-value-lg text-gray-800 dark:text-gray-200 mb-2">{{ t('model.not_found_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.not_found_desc') }}</p>
        <a href="/" class="bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">{{ t('model.goto_home') }}</a>
      </div>

      <div v-else-if="apiError" class="text-center py-20">
        <h1 class="t-value-lg text-gray-800 dark:text-gray-200 mb-2">{{ t('model.error_title') }}</h1>
        <p class="text-gray-500 dark:text-gray-400 mb-6">{{ t('model.error_desc') }}</p>
        <button @click="reload" class="bg-green-600 text-white px-4 py-2 rounded-sm hover:bg-green-700">{{ t('model.reload') }}</button>
      </div>

      <div v-else-if="stats">

        <StartCodeTeaser v-if="isAuthenticated" class="mx-4 md:mx-0 mb-3" />

        <!-- Breadcrumb: als Chip geerdet, damit er nicht im Gitter-Hintergrund untergeht -->
        <nav aria-label="Breadcrumb" class="px-4 md:px-0 mb-3">
          <ol class="flex flex-nowrap items-center gap-1.5 text-sm whitespace-nowrap overflow-x-auto max-w-full [scrollbar-width:none] [&::-webkit-scrollbar]:hidden bg-white/80 dark:bg-gray-800/80 backdrop-blur-sm border border-gray-200 dark:border-gray-700 rounded-full px-3.5 py-1.5 shadow-sm">
            <li class="shrink-0"><a href="/" class="flex items-center gap-1 font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors"><HomeIcon class="h-4 w-4 shrink-0" aria-hidden="true" />{{ t('model.breadcrumb_home') }}</a></li>
            <ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600 shrink-0" aria-hidden="true" />
            <li class="shrink-0"><a :href="modelsBaseUrl" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ t('model.breadcrumb_models') }}</a></li>
            <ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600 shrink-0" aria-hidden="true" />
            <li class="shrink-0"><a :href="`${modelsBaseUrl}/${canonicalBrand}`" class="font-medium text-gray-600 dark:text-gray-300 hover:text-green-700 dark:hover:text-green-400 transition-colors">{{ stats.brandDisplayName }}</a></li>
            <ChevronRightIcon class="h-3.5 w-3.5 text-gray-300 dark:text-gray-600 shrink-0" aria-hidden="true" />
            <li aria-current="page" class="shrink-0 font-bold text-gray-900 dark:text-gray-100">{{ stats.modelDisplayName.replace(stats.brandDisplayName + ' ', '') }}</li>
          </ol>
        </nav>

        <!-- Story "Prospekt gegen Realität": hero with the gauge, four chapters with one question
             each, the visitor's range band alongside, the exact result behind the gate at the end. -->
        <div class="px-4 md:px-0">
          <h1 class="max-w-[22ch] text-balance pt-1 text-[clamp(26px,6.4vw,44px)] font-bold leading-[1.06] tracking-tight text-gray-900 dark:text-gray-100">{{ t('model.hero_title_v2', { model: stats.modelDisplayName }) }}</h1>
          <header class="grid gap-5 pt-5 md:grid-cols-[1.1fr_1fr] md:items-center md:gap-8 md:pt-6">
            <div class="grid gap-3">
              <!-- Variant chips as the eyebrow: the story speaks about one battery -->
              <div v-if="activeVariants.length > 1" class="[scrollbar-width:none] [&::-webkit-scrollbar]:hidden -mx-4 flex gap-2 overflow-x-auto px-4 py-0.5 md:mx-0 md:flex-wrap md:overflow-visible md:px-0" role="group" :aria-label="t('model.variant_title')">
                <button v-for="(v, i) in activeVariants" :key="v.displayLabel ?? v.batteryCapacityKwh" type="button"
                        class="inline-flex min-h-9 flex-none items-center whitespace-nowrap rounded-full px-3.5 text-[13.5px] font-medium transition-colors"
                        :class="i === selectedVariantIndex
                          ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900'
                          : 'bg-gray-200/70 text-gray-800 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700'"
                        :aria-pressed="i === selectedVariantIndex"
                        @click="selectedVariantIndex = i">
                  {{ v.displayLabel || v.variantName || (v.batteryCapacityKwh + ' kWh') }}
                </button>
              </div>
              <p v-else class="text-[13px] text-gray-500 dark:text-gray-400">{{ variantLabel }}</p>
              <!-- One sentence per line, never wrapping: the size follows the viewport on the phone
                   and the half-width column from md up (about 13 em per line) -->
              <h2 class="text-[clamp(19px,6vw,40px)] font-bold leading-[1.1] tracking-tight text-gray-900 md:text-[clamp(24px,3.3vw,32px)] dark:text-gray-100" data-testid="story-hero">
                <span v-for="(line, i) in heroLines" :key="i" class="block whitespace-nowrap">{{ line }}{{ ' ' }}</span>
              </h2>
              <p class="max-w-[46ch] text-gray-600 dark:text-gray-400">{{ story.lede }}</p>
            </div>
            <RangeGauge v-if="storyInput.wltpRangeKm" :wltp-km="storyInput.wltpRangeKm" :real-km="story.realRangeKm" :winter-km="story.winterRangeKm" :rating-label="ratingLabel" />
          </header>

          <!-- Data basis -->
          <p class="mt-5 flex flex-wrap items-center gap-x-3 gap-y-1 border-y border-gray-200 py-3 text-[13px] text-gray-500 dark:border-gray-800 dark:text-gray-400">
            <template v-if="stats.logCount > 0">
              <span class="inline-flex items-center gap-1.5"><i class="h-2 w-2 rounded-full bg-green-600 dark:bg-green-500" aria-hidden="true"></i><b class="font-semibold text-gray-900 dark:text-gray-100">{{ t('model.story.basis_logs', { logs: formatNumber(stats.logCount) }) }}</b></span>
              <span>{{ t('model.story.basis_cars', { cars: formatNumber(stats.uniqueCars) }) }}</span>
              <span>{{ t('model.story.basis_drivers', { drivers: formatNumber(stats.uniqueContributors) }) }}</span>
            </template>
            <span v-else>{{ t('model.story.basis_none') }}</span>
            <span v-if="!isEurZone && stats.avgCostPerKwh" class="basis-full italic text-gray-400 dark:text-gray-500">{{ t('model.cost_disclaimer', { rate: EUR_EXCHANGE_RATES[currency], currency: currencySymbol, date: RATES_LAST_UPDATED }) }}</span>
          </p>

          <!-- One band instance: after the chapters on the phone (sticky bottom), the side column from md up (sticky top) -->
          <div class="grid md:grid-cols-[minmax(0,1fr)_280px] md:items-start md:gap-x-8">
            <div class="grid md:col-start-1 md:row-start-1">
              <StoryChapter v-for="(chapter, i) in story.chapters" :key="chapter.key"
                            :chapter-key="chapter.key" :level="chapter.level" :title="t(`model.story.c${i + 1}_title`)"
                            :headline="chapter.headline" :say="chapter.say" :tiles="chapter.tiles" :effect="chapter.effect"
                            :questions="questionsFor(chapter.key)"
                            @answer="answer">
                <template v-if="chapter.key === 'cost'" #extra>
                  <label class="mt-1 grid max-w-xs gap-1 text-[13px] text-gray-600 dark:text-gray-400">
                    <span>{{ t('model.story.own_price_label') }}</span>
                    <span class="flex items-center gap-2">
                      <input v-model="ownPriceText" type="text" inputmode="decimal" :placeholder="communityPricePlaceholder" data-testid="own-price"
                             class="h-11 w-32 rounded-xl border border-gray-200 bg-white px-3 text-base tabular-nums text-gray-900 placeholder:text-gray-400 focus:border-green-600 focus:outline-none focus:ring-2 focus:ring-green-600/30 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100"
                             @change="onOwnPrice" />
                      <span class="font-medium text-gray-700 dark:text-gray-300">{{ t('model.story.own_price_unit') }}</span>
                    </span>
                  </label>
                </template>
              </StoryChapter>

              <!-- For owners: a real call to action, visually second to the green "Meine Zahlen" -->
              <aside v-if="!isAuthenticated" class="mt-9 grid gap-3 rounded-2xl border border-gray-200 bg-white p-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-6 dark:border-gray-800 dark:bg-gray-900" data-testid="owner-cta">
                <div class="grid gap-1">
                  <p class="text-balance text-[17px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.story.owner_title', { model: stats.modelDisplayName }) }}</p>
                  <p class="text-[15px] text-gray-600 dark:text-gray-400">{{ t('model.story.owner_text', { n: formatNumber(stats.uniqueCars + 1) }) }}</p>
                </div>
                <a :href="registerPath" class="inline-flex min-h-11 items-center justify-center whitespace-nowrap rounded-xl bg-gray-900 px-5 font-semibold text-white hover:bg-gray-800 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white">{{ t('model.story.owner_cta') }}</a>
              </aside>
            </div>

            <div class="sticky z-30 mt-6 md:col-start-2 md:row-start-1 md:mt-0 md:pt-9 md:[position:sticky] md:top-24 md:bottom-auto"
                 :class="isAuthenticated ? 'bottom-[calc(env(safe-area-inset-bottom)+68px)]' : 'bottom-[calc(env(safe-area-inset-bottom)+12px)]'">
              <RangeBandBar :brand="brand" :model="model" :model-name="stats.modelDisplayName" :inputs="storyInputs" :answered="answeredCount" :total="QUESTION_COUNT" :fallback-band="story.fallbackBand" :profile="profileRows" @open="openResult" />
            </div>

            <PersonalRangeCalculator
              ref="calculatorRef"
              class="md:col-start-1 md:row-start-2"
              :brand="brand"
              :model="model"
              :model-name="stats.modelDisplayName"
              :log-count="stats.logCount"
              :car-count="stats.uniqueCars"
              :inputs="storyInputs"
              @profile="applyProfile" />
          </div>
        </div>

        <!-- Community methodology note -->
        <p class="px-4 py-4 text-center text-[13px] text-gray-500 md:px-0 dark:text-gray-400">{{ t('model.community_methodology_note') }}</p>

        <!-- Baujahr-Verteilung -->
        <div v-if="stats.yearDistribution && stats.yearDistribution.length > 0"
             class="bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 md:border md:rounded-2xl px-6 py-5 md:mb-6">
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
             class="bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 md:border md:rounded-2xl px-6 py-5 md:mb-6">
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

        <!-- WLTP and methodology, collapsed: the story above carries the figures -->
        <div v-if="activeVariants.length > 0" class="bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 md:border md:rounded-2xl overflow-hidden md:mb-6">
          <!-- Details & Methodik (eingeklappt) -->
          <details :open="story.level !== 'FULL'" class="group">
            <summary class="flex items-center justify-center gap-2 px-6 py-4 text-sm font-semibold text-green-700 dark:text-green-400 cursor-pointer select-none list-none [&::-webkit-details-marker]:hidden">
              <ClipboardDocumentListIcon class="h-5 w-5" />
              {{ story.level === 'FULL' ? t('model.details_methodology') : (ratingLabel === 'EPA' ? t('model.epa_section_title') : t('model.wltp_section_title')) }}
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
                  <div class="t-eyebrow text-gray-500 dark:text-gray-400 mb-3">{{ t('model.compare_manufacturer') }}</div>
                  <div class="space-y-3">
                    <div>
                      <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mb-0.5">{{ ratingLabel === 'EPA' ? t('model.epa_range_label') : t('model.wltp_table_range') }}</div>
                      <div class="t-value text-gray-900 dark:text-gray-100 whitespace-nowrap">
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
                  <div class="t-eyebrow text-green-700 dark:text-green-400 mb-3">Community · {{ t('model.compare_real') }}</div>
                  <div class="space-y-3">
                    <div>
                      <div class="text-sm font-semibold text-gray-600 dark:text-gray-300 mb-0.5">{{ ratingLabel === 'EPA' ? t('model.epa_range_label') : t('model.wltp_table_range') }}</div>
                      <div class="t-value text-gray-900 dark:text-gray-100">
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

        <!-- Fahrer sagen: Bewertungen unter den Messdaten -->
        <ModelRatingsBlock
          v-if="ratings"
          :ratings="ratings"
          :model="shortModelName"
          :cta-car-id="ratingCtaCarId"
          :register-path="isAuthenticated ? null : registerPath"
          class="mb-6"
        />

        <!-- CTA -->
        <div class="bg-green-700 p-6 text-white md:rounded-2xl dark:bg-green-800">
          <div class="flex items-center gap-2 mb-2">
            <ArrowTrendingUpIcon class="h-6 w-6" />
            <h2 class="t-title">{{ t('model.story.end_title', { model: stats.modelDisplayName }) }}</h2>
          </div>
          <p class="text-green-100 mb-4">{{ t('model.story.end_desc') }}</p>
          <div class="flex flex-wrap gap-3">
            <a :href="registerPath" class="inline-flex min-h-11 items-center rounded-xl bg-white px-5 font-semibold text-green-800 transition-colors hover:bg-green-50">
              {{ t('model.cta_free_start') }}
            </a>
            <a :href="loginPath" class="inline-flex min-h-11 items-center rounded-xl border border-white/60 px-5 font-semibold text-white transition-colors hover:bg-white/10">
              {{ t('model.cta_login') }}
            </a>
          </div>
        </div>

        <!-- SEO Text -->
        <div class="bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 md:border md:rounded-2xl p-6 md:mt-6">
          <h2 class="t-title text-gray-900 dark:text-gray-100 mb-4">
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
                <template v-if="stopCost">
                  {{ t('model.seo_costs_session', { kwh: formatNumber(Math.round(stopCost.kwh)), cost: formatCurrency(stopCost.eur, { decimals: 0 }) }) }}
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
              <p v-if="storyInput.seasonal">
                {{ t('model.seo_seasonal_data', {
                  model: stats.modelDisplayName,
                  summer: formatConsumption(storyInput.seasonal.summer),
                  winter: formatConsumption(storyInput.seasonal.winter),
                  pct: Math.round((storyInput.seasonal.winter / storyInput.seasonal.summer - 1) * 100)
                }) }}
              </p>
              <p v-else>
                {{ t('model.seo_seasonal_no_data', { model: stats.modelDisplayName }) }}
              </p>
            </div>
          </div>
        </div>

        <!-- FAQ -->
        <div v-if="faqItems.length > 0" class="bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 md:border md:rounded-2xl p-6 mt-6">
          <h2 class="t-title text-gray-900 dark:text-gray-100 mb-4">
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
      <div class="bg-white dark:bg-gray-900 border-y border-gray-200 dark:border-gray-800 md:border md:rounded-2xl p-6">
        <h2 class="t-title text-gray-900 dark:text-gray-100 mb-3">{{ t('model.related_title') }}</h2>
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

    <ModelCooperationNote v-for="partner in PARTNER_BANNERS" :key="partner.id" :partner="partner" />

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
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useCountryStore } from '../stores/country'
import { getModelStats, getTopModels, type PublicModelStats, type SeasonalDistribution, type TopModelPreview } from '../api/publicModelService'
import {
  ArrowTrendingUpIcon, ClipboardDocumentListIcon, SunIcon, ChartBarIcon, ChevronRightIcon, HomeIcon
} from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import StartCodeTeaser from '../components/codes/StartCodeTeaser.vue'
import ModelCooperationNote from '../components/models/ModelCooperationNote.vue'
import { PARTNER_BANNERS } from '../config/cooperations'
import PersonalRangeCalculator from '../components/models/PersonalRangeCalculator.vue'
import RangeGauge from '../components/models/RangeGauge.vue'
import StoryChapter, { type StoryQuestion } from '../components/models/StoryChapter.vue'
import RangeBandBar from '../components/models/RangeBandBar.vue'
import { buildModelStory, fastStop, keepUnits, type ChapterKey, type StoryInput } from '../utils/modelStory'
import type { PersonalRangeInputs } from '../api/personalRangeService'
import { analytics } from '../services/analytics'
import RegionChip from '../components/shared/RegionChip.vue'
import ModelRatingsBlock from '../components/rating/ModelRatingsBlock.vue'
import { ratingService, type PublicModelRatings } from '../api/ratingService'
import { useRatingStore } from '../stores/rating'
import { buildProductJsonLd } from '../utils/ratingJsonLd'
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
const { formatConsumption, formatNumber, consumptionUnitLabel, convertConsumption, formatDistance, distanceUnitLabel, formatCurrency, formatCostPerKwh, formatCostPerDistance, consumptionDeltaLabel, formatDecimal, isEurZone, currency, currencySymbol, unitSystem } = useLocaleFormat()
const { currentMarket, isDE, isEN, isGB, marketUrl, hreflangLinks } = useMarketRoute()
const modelsBaseUrl = computed(() => getMarketBasePath(currentMarket.value))
const authStore = useAuthStore()
const countryStore = useCountryStore()
const loading = ref(true)
const notFound = ref(false)
const apiError = ref(false)
const showBackPill = ref(false)
const stats = ref<PublicModelStats | null>(null)
/** Fahrerbewertungen, null bis geladen oder bei Fehler - die Seite funktioniert auch ohne. */
const ratings = ref<PublicModelRatings | null>(null)
/** Eigenes, bewertbares und unbewertetes Auto dieses Modells (nur eingeloggt). */
const ratingCtaCarId = ref<string | null>(null)
const selectedVariantIndex = ref(0)
const isAuthenticated = computed(() => authStore.isAuthenticated())

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

/** "ID.3" statt "Volkswagen ID.3" - für "ID.3-Fahrer". */
const shortModelName = computed(() => {
  if (!stats.value) return ''
  const full = stats.value.modelDisplayName
  const brandPrefix = `${stats.value.brandDisplayName} `
  return full.startsWith(brandPrefix) ? full.slice(brandPrefix.length) : full
})

async function loadRatingCta() {
  if (!authStore.isAuthenticated() || !stats.value) return
  try {
    const eligibility = await useRatingStore().getEligibility()
    ratingCtaCarId.value = eligibility.cars.find(c => c.eligible && !c.rated
      && c.brandName === stats.value!.brandDisplayName && c.modelName === shortModelName.value)?.carId ?? null
  } catch {
    // ohne Hinweis weiter
  }
}
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

// ── Story "Prospekt gegen Realität" ─────────────────────────────────────────────────────
// Model-level figures (the same the ranking and the backend calculator use) on the battery of
// the variant the reader picked. That battery also goes to the server, so the band and the
// page never disagree.

/** The ranking list: fast-charge power of this model and the fleet winter factor. Empty on failure. */
const topModels = ref<TopModelPreview[]>([])
/** Same threshold as the backend: a season counts from 10 logs. */
const MIN_LOGS_PER_SEASON = 10
/** Open ranges instead of four chips, so 7.000 km or 45 % motorway fit as well */
const ANNUAL_SLIDER = { min: 5_000, max: 50_000, step: 1_000, start: 15_000 }
const HIGHWAY_SLIDER = { min: 0, max: 100, step: 5, start: 30 }
const COMMUTE_CHIPS = [20, 40, 60, 100] as const
const LONG_TRIP_CHIPS = [250, 500, 800, 1_200] as const
const QUESTION_CHAPTER: Record<QuestionKey, ChapterKey> = { annualKm: 'consumption', highwayShare: 'consumption', commute: 'season', longTrip: 'longTrip', place: 'cost' }
type QuestionKey = 'annualKm' | 'highwayShare' | 'commute' | 'longTrip' | 'place'
const QUESTION_COUNT = Object.keys(QUESTION_CHAPTER).length
const OWN_PRICE_MIN = 0.05
const OWN_PRICE_MAX = 1.5

const variantLabel = computed(() => {
  const v = selectedVariant.value
  return v ? (v.displayLabel || v.variantName || `${v.batteryCapacityKwh} kWh`) : ''
})

const topEntry = computed(() => topModels.value.find(m => m.model === stats.value?.model) ?? null)

/** Median winter/summer ratio over the models that have both seasons, for models without winter data. */
const fleetWinterFactor = computed(() => {
  const ratios = topModels.value
    .flatMap(m => m.summerConsumptionKwhPer100km && m.winterConsumptionKwhPer100km
      ? [m.winterConsumptionKwhPer100km / m.summerConsumptionKwhPer100km] : [])
    .sort((a, b) => a - b)
  if (ratios.length < 3) return null
  const mid = Math.floor(ratios.length / 2)
  return { factor: ratios.length % 2 ? ratios[mid] : (ratios[mid - 1] + ratios[mid]) / 2, models: ratios.length }
})

const storyInput = computed((): StoryInput => {
  const st = stats.value!
  const v = selectedVariant.value
  const season = st.seasonalDistribution
  const seasonOk = season?.summerConsumptionKwhPer100km && season?.winterConsumptionKwhPer100km
    && season.summerLogCount >= MIN_LOGS_PER_SEASON && season.winterLogCount >= MIN_LOGS_PER_SEASON
  const rt = st.routeTypeConsumption
  return {
    modelName: st.modelDisplayName, brandName: st.brandDisplayName,
    logCount: st.logCount, carCount: st.uniqueCars, driverCount: st.uniqueContributors,
    netBatteryKwh: v?.batteryCapacityKwh ?? null,
    wltpKwhPer100km: v?.officialConsumptionKwhPer100km ?? worstOfficialConsumption.value,
    wltpRangeKm: v?.officialRangeKm ?? bestOfficialRange.value,
    avgKwhPer100km: st.avgConsumptionKwhPer100km,
    minKwhPer100km: topEntry.value?.minRealConsumptionKwhPer100km ?? v?.realConsumptionMinKwhPer100km ?? null,
    maxKwhPer100km: topEntry.value?.maxRealConsumptionKwhPer100km ?? v?.realConsumptionMaxKwhPer100km ?? null,
    routeType: rt ? { highway: rt.highwayKwhPer100km, city: rt.cityKwhPer100km, combined: rt.combinedKwhPer100km,
      highwayCars: rt.highwayCarCount, cityLogs: rt.cityLogCount, combinedLogs: rt.combinedLogCount } : null,
    seasonal: seasonOk ? { summer: season.summerConsumptionKwhPer100km!, winter: season.winterConsumptionKwhPer100km!,
      summerLogs: season.summerLogCount, winterLogs: season.winterLogCount } : null,
    fastChargePowerKw: topEntry.value?.fastChargePowerKw ?? null,
    avgChargingPowerKw: st.avgChargingPowerKw,
    acPricePerKwh: st.acAvgCostPerKwh, dcPricePerKwh: st.dcAvgCostPerKwh, avgPricePerKwh: st.avgCostPerKwh,
    fleetWinterFactor: fleetWinterFactor.value,
  }
})

/** First sentence on its own line: "Der Prospekt sagt 434 km." then "48 Fahrer sagen 280 km." */
const heroLines = computed(() => {
  const text = keepUnits(story.value.heroSentence)
  const i = text.indexOf('. ')
  return i < 0 ? [text] : [text.slice(0, i + 1), text.slice(i + 2)]
})

/** Fast-charging stop 10 to 80 % at the DC price; chapter 4, SEO text and FAQ show the same figure */
const stopCost = computed(() => fastStop(selectedVariant.value?.batteryCapacityKwh ?? null,
  stats.value?.dcAvgCostPerKwh ?? stats.value?.avgCostPerKwh ?? null))

const story = computed(() => buildModelStory(storyInput.value, {
  t: (key, params) => t(key, params ?? {}),
  formatConsumption: v => formatConsumption(v),
  formatConsumptionValue: v => formatConsumption(v, { showUnit: false }),
  formatDistance: km => formatDistance(km),
  formatCostPerKwh,
  formatCostPerDistance,
  formatCurrency: v => formatCurrency(v, { decimals: 0 }),
  formatNumber,
}))

/** The reader's answers; the battery follows the variant switcher. Unanswered ones hold the slider start. */
const answers = reactive({ annualKm: ANNUAL_SLIDER.start, highwayShare: HIGHWAY_SLIDER.start, commuteKmPerDay: 40, homeCharging: true, longTripKm: 500, pricePerKwh: null as number | null })
/** Per question, so answering the yearly mileage does not mark the motorway row as answered too */
const answered = reactive(new Set<QuestionKey>())
const answeredCount = computed(() => answered.size)
const storyInputs = computed((): PersonalRangeInputs => ({
  commuteKmPerDay: answers.commuteKmPerDay,
  highwaySharePercent: answers.highwayShare,
  homeCharging: answers.homeCharging,
  longTripKm: answers.longTripKm,
  pricePerKwh: answers.pricePerKwh,
  annualKm: answers.annualKm,
  netBatteryKwh: selectedVariant.value?.batteryCapacityKwh ?? null,
}))
const ownPriceText = ref('')
const calculatorRef = ref<InstanceType<typeof PersonalRangeCalculator> | null>(null)

const communityPricePlaceholder = computed(() => stats.value?.avgCostPerKwh ? formatDecimal(stats.value.avgCostPerKwh, 2) : '')

function chipsOf(key: QuestionKey): { label: string; value: number | boolean }[] {
  switch (key) {
    case 'annualKm':
    case 'highwayShare': return []
    case 'commute': return COMMUTE_CHIPS.map(km => ({ value: km, label: formatDistance(km) }))
    case 'longTrip': return LONG_TRIP_CHIPS.map(km => ({ value: km, label: formatDistance(km) }))
    case 'place': {
      const st = stats.value
      const ac = st?.acAvgCostPerKwh ? ` · ${formatCostPerKwh(st.acAvgCostPerKwh)}` : ''
      const dc = st?.dcAvgCostPerKwh ? ` · ${formatCostPerKwh(st.dcAvgCostPerKwh)}` : ''
      return [
        { value: true, label: t('model.story.place_home') + ac },
        { value: false, label: t('model.story.place_public') + dc },
      ]
    }
  }
}

function valueOf(key: QuestionKey): number | boolean {
  switch (key) {
    case 'annualKm': return answers.annualKm
    case 'highwayShare': return answers.highwayShare
    case 'commute': return answers.commuteKmPerDay
    case 'longTrip': return answers.longTripKm
    case 'place': return answers.homeCharging
  }
}

/** "30 % · 4.500 km": the share and what it means in kilometres at the yearly mileage */
function formatHighway(share: number): string {
  return `${formatNumber(share)} % · ${formatDistance(Math.round(answers.annualKm * share / 100 / 100) * 100)}`
}

function sliderOf(key: QuestionKey): StoryQuestion['slider'] {
  if (key === 'annualKm') return { ...ANNUAL_SLIDER, format: km => formatDistance(km) }
  if (key === 'highwayShare') return {
    ...HIGHWAY_SLIDER,
    format: formatHighway,
    short: share => `${formatNumber(share)} %`,
    detail: share => t('model.story.slider_highway_km', { km: formatDistance(Math.round(answers.annualKm * share / 100 / 100) * 100) }),
  }
  return undefined
}

function question(key: QuestionKey): StoryQuestion {
  // Nothing shows as chosen before the reader chose it; the calculation still has defaults behind the scenes
  return { key, question: t(`model.story.q_${key}`), chips: chipsOf(key), slider: sliderOf(key), value: answered.has(key) ? valueOf(key) : null }
}

function questionsFor(chapter: ChapterKey): StoryQuestion[] {
  return (Object.keys(QUESTION_CHAPTER) as QuestionKey[]).filter(k => QUESTION_CHAPTER[k] === chapter).map(question)
}

/** Rows for the profile card: the chosen answer per question, null while the question is open */
const profileRows = computed(() => (Object.keys(QUESTION_CHAPTER) as QuestionKey[]).map(key => {
  const slider = sliderOf(key)
  const label = slider ? slider.format(valueOf(key) as number) : chipsOf(key).find(c => c.value === valueOf(key))?.label
  let value = answered.has(key) && label ? label : null
  // Own price replaces the community price behind the place, no extra label
  if (key === 'place' && value && answers.pricePerKwh != null) value = `${t(answers.homeCharging ? 'model.story.place_home' : 'model.story.place_public')} · ${formatCostPerKwh(answers.pricePerKwh)}`
  return { key, label: t(`model.story.profile_${key}`), value }
}))

function markAnswered(key: QuestionKey) {
  if (answered.size === 0) analytics.track('personal_range_started', { model: stats.value?.modelDisplayName ?? model })
  answered.add(key)
  analytics.track('story_chip_answered', { model: stats.value?.modelDisplayName ?? model, chapter: QUESTION_CHAPTER[key], question: key })
}

function answer(key: string, value: number | boolean) {
  switch (key as QuestionKey) {
    case 'annualKm': answers.annualKm = value as number; break
    case 'highwayShare': answers.highwayShare = value as number; break
    case 'commute': answers.commuteKmPerDay = value as number; break
    case 'longTrip': answers.longTripKm = value as number; break
    case 'place': answers.homeCharging = value as boolean; break
  }
  markAnswered(key as QuestionKey)
}

/** Own price in EUR/kWh; an empty or out-of-range entry falls back to the community price. */
function onOwnPrice() {
  const parsed = Number(ownPriceText.value.replace(',', '.'))
  const valid = Number.isFinite(parsed) && parsed >= OWN_PRICE_MIN && parsed <= OWN_PRICE_MAX
  answers.pricePerKwh = valid ? Math.round(parsed * 100) / 100 : null
  if (!valid) ownPriceText.value = ''
  markAnswered('place')
}

function nearest(values: readonly number[], target: number): number {
  return values.reduce((best, v) => Math.abs(v - target) < Math.abs(best - target) ? v : best)
}

/** Onto the slider: within its range and on its step */
function snap(slider: { min: number; max: number; step: number }, value: number): number {
  return Math.min(slider.max, Math.max(slider.min, Math.round(value / slider.step) * slider.step))
}

/** A stored profile is the reader's own answers: sliders and chips move to the nearest value and count as answered. */
function applyProfile(p: PersonalRangeInputs) {
  answers.annualKm = snap(ANNUAL_SLIDER, p.annualKm ?? answers.annualKm)
  answers.highwayShare = snap(HIGHWAY_SLIDER, p.highwaySharePercent)
  answers.commuteKmPerDay = nearest(COMMUTE_CHIPS, p.commuteKmPerDay)
  answers.longTripKm = nearest(LONG_TRIP_CHIPS, p.longTripKm)
  answers.homeCharging = p.homeCharging
  answers.pricePerKwh = p.pricePerKwh ?? null
  ownPriceText.value = p.pricePerKwh != null ? String(p.pricePerKwh).replace('.', ',') : ''
  for (const key of Object.keys(QUESTION_CHAPTER) as QuestionKey[]) answered.add(key)
}

function openResult() {
  calculatorRef.value?.request()
  const el = calculatorRef.value?.$el as HTMLElement | undefined
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

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

  if (storyInput.value.wltpRangeKm && story.value.realRangeKm != null && selectedVariant.value) {
    items.push({
      question: t('model.faq_q_range', { model: name }),
      answer: t('model.faq_a_range', {
        model: name,
        wltpRange: formatDistance(storyInput.value.wltpRangeKm),
        battery: selectedVariant.value.batteryCapacityKwh,
        consumption: formatConsumption(stats.value.avgConsumptionKwhPer100km),
        realRange: formatDistance(story.value.realRangeKm),
        ratingLabel: ratingLabel.value
      })
    })
  }

  // Same stop as chapter 4, plus the mean session for those who mostly top up at home
  if (stats.value.avgCostPerKwh && stopCost.value && stats.value.avgKwhPerSession) {
    items.push({
      question: t('model.faq_q_cost', { model: name }),
      answer: t('model.faq_a_cost', {
        price: formatCostPerKwh(stats.value.avgCostPerKwh),
        kwh: formatNumber(Math.round(stopCost.value.kwh)),
        cost: formatCurrency(stopCost.value.eur, { decimals: 0 }),
        sessionKwh: formatNumber(Math.round(stats.value.avgKwhPerSession)),
        sessionCost: formatCurrency(stats.value.avgCostPerKwh * stats.value.avgKwhPerSession, { decimals: 0 }),
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

  const productJsonLd = buildProductJsonLd({
    name,
    brand: stats.value.brandDisplayName,
    url: canonicalUrl,
    driverLabel: t('rating.public.driver', { model: shortModelName.value }),
    ratings: ratings.value,
  })

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
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: title },
      { name: 'twitter:description', content: description },
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
        : []),
      ...(productJsonLd ? [{ type: 'application/ld+json', innerHTML: JSON.stringify(productJsonLd) }] : []),
    ]
  }
}))

onMounted(async () => {
  if (sessionStorage.getItem('ev_from') === 'lp_v2') {
    showBackPill.value = true
    sessionStorage.removeItem('ev_from')
  }

  // Bewertungen parallel und im selben Lade-Flow, damit sie im Prerender-HTML stehen
  const ratingsRequest = ratingService.getPublicRatings(brand, model).catch(() => null)
  const topRequest = getTopModels(200).catch((): TopModelPreview[] => [])
  try {
    const [data, ratingData, top] = await Promise.all([getModelStats(brand, model), ratingsRequest, topRequest])
    ratings.value = ratingData
    topModels.value = top
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
  loadRatingCta()
})

function deltaLabelClass(real: number | null, wltp: number | null): string {
  if (!real || !wltp) return ''
  const pct = ((real - wltp) / wltp) * 100
  if (pct <= 0) return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400'
  if (pct <= 15) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
}

function reload() { window.location.reload() }

function goBackToLpV2() {
  sessionStorage.setItem('ev_back_slide', '1')
  router.back()
}

</script>

<style scoped>
/* ── Typo-Skala der Modellseite ──────────────────────────────────────────
   Nur Größe/Gewicht/Tracking/Leading - Farbe bleibt per Tailwind-Utility.
   Ersetzt die zuvor 11 ad-hoc Größen durch ein konsistentes Set.        */
.t-eyebrow  { font-size: .6875rem; line-height: 1rem;    font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
.t-block    { font-size: .875rem;  line-height: 1.25rem;  font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
.t-title    { font-size: 1.125rem; line-height: 1.55rem; font-weight: 700; }
.t-value    { font-size: 1.25rem;  line-height: 1.6rem;  font-weight: 700; }
.t-value-lg { font-size: 1.5rem;   line-height: 1.6rem;  font-weight: 800; }
.t-value-xl { font-size: 1.875rem; line-height: 1;       font-weight: 800; }
.t-metric   { font-size: 2.5rem;   line-height: 1;       font-weight: 800; }
@media (min-width: 640px) { .t-metric { font-size: 3rem; } }

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
