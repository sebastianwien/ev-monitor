<template>
  <div class="max-w-6xl mx-auto bg-gray-50/85 dark:bg-gray-900/75 backdrop-blur-md rounded-sm p-4 md:p-8 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-black/5 dark:shadow-black/40">
    <!-- Headline -->
    <div class="text-center mb-8">
      <span class="inline-block text-[11px] font-bold bg-indigo-600 text-white px-3 py-1 rounded-full tracking-wide mb-3">EV MONITOR</span>
      <h1 class="text-2xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-2">{{ t(isPublic ? 'upgrade.public_headline' : 'upgrade.tier_headline') }}</h1>
      <p class="text-gray-500 dark:text-gray-400 text-sm md:text-base">{{ t(isPublic ? 'upgrade.public_subheadline' : 'upgrade.tier_subtitle') }}</p>
    </div>

    <!-- Active-Plan Banner (nur eingeloggt mit bestehendem Abo) -->
    <div v-if="tier !== 'NONE'" class="max-w-2xl mx-auto mb-6 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-sm px-4 py-3">
      <div class="flex flex-col sm:flex-row items-center justify-center gap-3">
        <div class="flex items-center gap-2">
          <CheckCircleIcon class="w-5 h-5 text-green-600 dark:text-green-400 shrink-0" />
          <p class="text-sm text-gray-700 dark:text-gray-200">{{ t(activeBannerKey) }}</p>
        </div>
        <button
          @click="emit('manage')"
          :disabled="portalLoading"
          class="text-sm font-medium bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 text-white px-4 py-2 rounded-sm shadow-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
        >
          {{ portalLoading ? '...' : t('upgrade.tier_active_manage') }}
        </button>
      </div>
      <p v-if="portalError" class="text-xs text-red-600 dark:text-red-400 text-center mt-2">{{ portalError }}</p>
    </div>

    <!-- Plan Toggle -->
    <div v-if="showPlanToggle" class="flex justify-center mb-6">
      <div class="inline-flex bg-gray-100 dark:bg-gray-800 p-1 rounded-sm">
        <button
          @click="selectedPlan = 'monthly'"
          :class="selectedPlan === 'monthly'
            ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
            : 'text-gray-600 dark:text-gray-400'"
          class="px-4 py-2 rounded-sm text-sm font-medium transition-colors"
        >{{ t('upgrade.tier_toggle_monthly') }}</button>
        <button
          @click="selectedPlan = 'yearly'"
          :class="selectedPlan === 'yearly'
            ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
            : 'text-gray-600 dark:text-gray-400'"
          class="px-4 py-2 rounded-sm text-sm font-medium transition-colors flex items-center gap-1.5"
        >
          {{ t('upgrade.tier_toggle_yearly') }}
          <span class="text-[10px] bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-400 px-1.5 py-0.5 rounded font-bold">{{ t('upgrade.tier_toggle_yearly_savings') }}</span>
        </button>
      </div>
    </div>

    <!-- Tier Cards: Free + AutoSync fuer alle; die Live-Karte erscheint nur noch als
         Aktiv-Anzeige fuer Bestandsabonnenten (nicht mehr kaufbar) -->
    <div class="grid grid-cols-1 gap-4 md:gap-5" :class="isLiveActive ? 'md:grid-cols-3' : 'md:grid-cols-2'">

      <!-- FREE -->
      <div class="bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-6 flex flex-col order-3 md:order-1">
        <div class="mb-4">
          <p class="text-xs font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-1">{{ t('upgrade.tier_free_label') }}</p>
          <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_free_title') }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('upgrade.tier_free_subtitle') }}</p>
        </div>
        <div class="mb-5">
          <p class="text-3xl font-bold text-gray-900 dark:text-gray-100">
            {{ t('upgrade.tier_free_price') }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_free_price_unit') }}</span>
          </p>
        </div>
        <ul class="space-y-2.5 text-sm text-gray-700 dark:text-gray-300 mb-6 flex-1">
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_manual') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_xpeng') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_imports') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_api') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_stats') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_soh') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-gray-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_free_feat_cars') }}</span></li>
        </ul>
        <button
          v-if="isPublic"
          @click="emit('register')"
          class="w-full bg-gray-900 hover:bg-gray-800 dark:bg-gray-100 dark:hover:bg-white text-white dark:text-gray-900 font-semibold py-3 rounded-sm text-sm shadow-[0_4px_0_0_#374151] dark:shadow-[0_4px_0_0_#9ca3af] active:translate-y-1 active:shadow-none transition"
        >
          {{ t('upgrade.public_cta_free') }}
        </button>
        <button
          v-else
          disabled
          class="w-full bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 font-semibold py-3 rounded-sm text-sm cursor-default"
        >
          {{ tier === 'NONE' ? t('upgrade.tier_free_cta_current') : t('upgrade.tier_free_cta_included') }}
        </button>
      </div>

      <!-- AUTOSYNC -->
      <div
        :class="isAutoSyncActive
          ? 'border-green-600 dark:border-green-500 shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] shadow-green-500/25 md:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:md:shadow-[5px_5px_0_rgba(255,255,255,0.35)] md:shadow-green-500/10'
          : tier === 'NONE'
            ? 'border-green-500 dark:border-green-400 shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] shadow-green-500/25 md:shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:md:shadow-[5px_5px_0_rgba(255,255,255,0.35)] md:shadow-green-500/10 md:-mt-4'
            : 'border-gray-200 dark:border-gray-700'"
        class="bg-white dark:bg-gray-900 rounded-sm border-2 p-6 flex flex-col relative order-1 md:order-2"
      >
        <span v-if="tier === 'NONE'" class="absolute -top-3 left-1/2 -translate-x-1/2 text-[10px] font-bold bg-green-600 dark:bg-green-500 text-white px-3 py-1 rounded-full tracking-wider whitespace-nowrap">{{ t('upgrade.tier_badge_recommended') }}</span>
        <div class="mb-4">
          <p class="text-xs font-bold text-green-600 dark:text-green-400 uppercase tracking-wider mb-1">{{ t('upgrade.tier_autosync_label') }}</p>
          <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_autosync_title') }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('upgrade.tier_autosync_subtitle') }}</p>
        </div>
        <div class="mb-5">
          <Transition name="price-fade" mode="out-in">
            <p :key="selectedPlan + tier" class="text-3xl font-bold text-gray-900 dark:text-gray-100">
              <template v-if="selectedPlan === 'yearly' && tier === 'NONE'">{{ pricing.yearly }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_per_year') }}</span></template>
              <template v-else>{{ pricing.monthly }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_autosync_price_unit') }}</span></template>
            </p>
          </Transition>
          <p v-if="!(selectedPlan === 'yearly' && tier === 'NONE')" class="text-xs text-green-600 dark:text-green-400 font-medium mt-0.5">{{ t('upgrade.tier_autosync_yearly_hint', { yearly: pricing.yearly }) }}</p>
        </div>
        <ul class="space-y-2.5 text-sm text-gray-700 dark:text-gray-300 mb-4">
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_autosync_feat_smartcar') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_autosync_feat_connection') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_autosync_feat_insights') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_autosync_feat_drain') }}</span></li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_autosync_feat_curves') }}</span></li>
          <li class="flex items-start gap-2 text-gray-500 dark:text-gray-400"><span class="mt-0.5">+</span><span><em>{{ t('upgrade.tier_autosync_feat_inherits') }}</em></span></li>
        </ul>

        <div class="mb-5 rounded-sm border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/40 p-3">
          <p class="text-[11px] font-bold uppercase tracking-[0.12em] text-gray-500 dark:text-gray-400 mb-2">{{ t('upgrade.tier_autosync_brands_title') }}</p>
          <ul class="flex flex-wrap gap-1.5">
            <li
              v-for="brand in autosyncBrandChips" :key="brand"
              class="text-[11px] font-semibold bg-white dark:bg-gray-900 text-gray-600 dark:text-gray-300 border border-gray-200 dark:border-gray-700 px-1.5 py-0.5 rounded-sm"
            >{{ brand }}</li>
          </ul>
          <p class="text-[11px] text-gray-400 dark:text-gray-500 mt-2 leading-snug">{{ t('upgrade.tier_autosync_brands_note') }}</p>
        </div>

        <div class="flex-1"></div>
        <button
          v-if="isPublic"
          @click="emit('register')"
          class="w-full bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 text-white dark:text-gray-900 font-semibold py-3 rounded-sm text-sm shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
        >
          {{ t('upgrade.public_cta') }}
        </button>
        <template v-else-if="tier === 'NONE'">
          <button
            @click="emit('checkout')"
            :disabled="checkoutLoading || !premiumEnabled"
            class="w-full bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white dark:text-gray-900 font-semibold py-3 rounded-sm text-sm shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
          >
            <span v-if="checkoutLoading">{{ t('upgrade.cta_loading') }}</span>
            <span v-else-if="!premiumEnabled">{{ t('upgrade.cta_coming_soon') }}</span>
            <span v-else>{{ t('upgrade.tier_autosync_cta_trial') }}</span>
          </button>
          <p class="text-[11px] text-gray-400 dark:text-gray-500 text-center mt-2">{{ t('upgrade.tier_autosync_disclaimer') }}</p>
          <p v-if="checkoutError" class="text-xs text-red-600 dark:text-red-400 text-center mt-2">{{ checkoutError }}</p>
        </template>
        <button
          v-else
          disabled
          class="w-full bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 font-semibold py-3 rounded-sm text-sm cursor-default flex items-center justify-center gap-1.5"
        >
          <CheckCircleIcon class="w-4 h-4" />
          {{ isAutoSyncActive ? t('upgrade.tier_autosync_cta_active') : t('upgrade.tier_autosync_cta_included') }}
        </button>
      </div>

      <!-- LIVE: nicht mehr kaufbar - nur noch Aktiv-Anzeige fuer Bestandsabonnenten.
           Verwaltet/gekuendigt wird ueber den Manage-Button im Aktiv-Banner oben. -->
      <div
        v-if="isLiveActive"
        class="bg-white dark:bg-gray-900 rounded-sm border-2 border-indigo-600 dark:border-indigo-400 shadow-[5px_5px_0_rgba(0,0,0,0.35)] dark:shadow-[5px_5px_0_rgba(255,255,255,0.35)] shadow-indigo-500/10 p-6 flex flex-col relative order-2 md:order-3"
      >
        <div class="mb-4">
          <p class="text-xs font-bold text-indigo-600 dark:text-indigo-400 uppercase tracking-wider mb-1">{{ t('upgrade.tier_live_label') }}</p>
          <h2 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_live_card_title') }}</h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ t('upgrade.tier_live_card_subtitle') }}</p>
        </div>
        <div class="mb-5">
          <p class="text-3xl font-bold text-gray-900 dark:text-gray-100">{{ pricing.liveMonthly }}<span class="text-base font-normal text-gray-400 dark:text-gray-500"> {{ t('upgrade.tier_live_price_unit') }}</span></p>
        </div>
        <ul class="space-y-2.5 text-sm text-gray-700 dark:text-gray-300 mb-6 flex-1">
          <!-- Tesla-only Features (Live-Power-Stream nur via Tesla Telemetry) -->
          <template v-if="showTeslaOnlyFeatures">
            <li class="space-y-1">
              <div class="flex items-start gap-2">
                <CheckCircleIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
                <span class="flex-1">{{ t('upgrade.tier_live_feat_live_view') }}</span>
                <button v-if="hasPreview('live_view')" type="button" @click="togglePreview('live_view')"
                  :aria-label="t('upgrade.tier_live_feat_preview_hint')"
                  class="shrink-0 p-0.5 text-indigo-400 dark:text-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-300 transition">
                  <ChevronDownIcon v-if="expandedPreview !== 'live_view'" class="w-3.5 h-3.5" />
                  <ChevronUpIcon v-else class="w-3.5 h-3.5" />
                </button>
              </div>
              <img v-if="expandedPreview === 'live_view' && hasPreview('live_view')" :src="'/upgrade-previews/live-view.png'" loading="lazy"
                :alt="t('upgrade.tier_live_feat_live_view')"
                @error="onPreviewError('live_view')"
                class="mt-1 ml-5 rounded-sm border border-indigo-200 dark:border-indigo-800 shadow-sm max-w-full" />
            </li>
            <li class="space-y-1">
              <div class="flex items-start gap-2">
                <CheckCircleIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
                <span class="flex-1">{{ t('upgrade.tier_live_feat_curves') }}</span>
                <button v-if="hasPreview('curves')" type="button" @click="togglePreview('curves')"
                  :aria-label="t('upgrade.tier_live_feat_preview_hint')"
                  class="shrink-0 p-0.5 text-indigo-400 dark:text-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-300 transition">
                  <ChevronDownIcon v-if="expandedPreview !== 'curves'" class="w-3.5 h-3.5" />
                  <ChevronUpIcon v-else class="w-3.5 h-3.5" />
                </button>
              </div>
              <img v-if="expandedPreview === 'curves' && hasPreview('curves')" :src="'/upgrade-previews/curves.png'" loading="lazy"
                :alt="t('upgrade.tier_live_feat_curves')"
                @error="onPreviewError('curves')"
                class="mt-1 ml-5 rounded-sm border border-indigo-200 dark:border-indigo-800 shadow-sm max-w-full" />
            </li>
          </template>
          <!-- Universelle Live-Features (Tesla + Polestar) -->
          <li class="space-y-1">
            <div class="flex items-start gap-2">
              <CheckCircleIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
              <span class="flex-1">{{ t('upgrade.tier_live_feat_insights') }}</span>
              <button v-if="hasPreview('insights')" type="button" @click="togglePreview('insights')"
                :aria-label="t('upgrade.tier_live_feat_preview_hint')"
                class="shrink-0 p-0.5 text-indigo-400 dark:text-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-300 transition">
                <ChevronDownIcon v-if="expandedPreview !== 'insights'" class="w-3.5 h-3.5" />
                <ChevronUpIcon v-else class="w-3.5 h-3.5" />
              </button>
            </div>
            <img v-if="expandedPreview === 'insights' && hasPreview('insights')" :src="'/upgrade-previews/insights.png'" loading="lazy"
              :alt="t('upgrade.tier_live_feat_insights')"
              @error="onPreviewError('insights')"
              class="mt-1 ml-5 rounded-sm border border-indigo-200 dark:border-indigo-800 shadow-sm max-w-full" />
          </li>
          <li class="space-y-1">
            <div class="flex items-start gap-2">
              <CheckCircleIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
              <span class="flex-1">{{ t('upgrade.tier_live_feat_calendar') }}</span>
              <button v-if="hasPreview('calendar')" type="button" @click="togglePreview('calendar')"
                :aria-label="t('upgrade.tier_live_feat_preview_hint')"
                class="shrink-0 p-0.5 text-indigo-400 dark:text-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-300 transition">
                <ChevronDownIcon v-if="expandedPreview !== 'calendar'" class="w-3.5 h-3.5" />
                <ChevronUpIcon v-else class="w-3.5 h-3.5" />
              </button>
            </div>
            <img v-if="expandedPreview === 'calendar' && hasPreview('calendar')" :src="'/upgrade-previews/calendar.png'" loading="lazy"
              :alt="t('upgrade.tier_live_feat_calendar')"
              @error="onPreviewError('calendar')"
              class="mt-1 ml-5 rounded-sm border border-indigo-200 dark:border-indigo-800 shadow-sm max-w-full" />
          </li>
          <li class="space-y-1">
            <div class="flex items-start gap-2">
              <CheckCircleIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
              <span class="flex-1">{{ t('upgrade.tier_live_feat_drain') }}</span>
              <button v-if="hasPreview('drain')" type="button" @click="togglePreview('drain')"
                :aria-label="t('upgrade.tier_live_feat_preview_hint')"
                class="shrink-0 p-0.5 text-indigo-400 dark:text-indigo-500 hover:text-indigo-600 dark:hover:text-indigo-300 transition">
                <ChevronDownIcon v-if="expandedPreview !== 'drain'" class="w-3.5 h-3.5" />
                <ChevronUpIcon v-else class="w-3.5 h-3.5" />
              </button>
            </div>
            <img v-if="expandedPreview === 'drain' && hasPreview('drain')" :src="'/upgrade-previews/drain.png'" loading="lazy"
              :alt="t('upgrade.tier_live_feat_drain')"
              @error="onPreviewError('drain')"
              class="mt-1 ml-5 rounded-sm border border-indigo-200 dark:border-indigo-800 shadow-sm max-w-full" />
          </li>
          <li class="flex items-start gap-2"><CheckCircleIcon class="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" /><span>{{ t('upgrade.tier_live_feat_brands') }}</span></li>
          <li class="flex items-start gap-2 text-gray-500 dark:text-gray-400"><span class="mt-0.5">+</span><span><em>{{ t('upgrade.tier_live_feat_inherits') }}</em></span></li>
        </ul>
        <button
          disabled
          class="w-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-400 font-semibold py-3 rounded-sm text-sm cursor-default flex items-center justify-center gap-1.5"
        >
          <CheckCircleIcon class="w-4 h-4" />
          {{ t('upgrade.tier_live_cta_active') }}
        </button>
      </div>
    </div>

    <!-- Supporter entry-point: the full pitch lives on the dedicated /supporter page (account only) -->
    <div
      v-if="tier === 'NONE' && !isPublic"
      class="mt-6 bg-white dark:bg-gray-900 rounded-sm border border-amber-200 dark:border-amber-700/40 shadow-sm dark:shadow-none p-4 md:p-5 flex flex-col sm:flex-row sm:items-center gap-4"
    >
      <div class="flex items-start gap-3 flex-1 min-w-0">
        <HeartIcon class="w-5 h-5 text-amber-500 dark:text-amber-400 shrink-0 mt-0.5" />
        <div class="min-w-0">
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('upgrade.tier_supporter_title') }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('upgrade.tier_supporter_subtitle') }}</p>
        </div>
      </div>
      <router-link
        to="/supporter"
        class="shrink-0 w-full sm:w-auto inline-flex items-center justify-center gap-1.5 bg-amber-500 hover:bg-amber-600 dark:bg-amber-500 dark:hover:bg-amber-400 text-white font-semibold px-5 py-2.5 rounded-sm text-sm shadow-[0_4px_0_0_#b45309] dark:shadow-[0_4px_0_0_#92400e] active:translate-y-1 active:shadow-none transition"
      >
        <HeartIcon class="w-4 h-4" />{{ t('upgrade.tier_supporter_cta') }}
      </router-link>
    </div>

    <!-- AutoSync / Smartcar erklaert - raeumt Vertrauens-Bedenken vor dem Kauf aus -->
    <div v-if="showSmartcarFaq" class="max-w-2xl mx-auto mt-6">
      <SmartcarFaq variant="soft" />
    </div>

    <!-- Trust + Payments -->
    <div v-if="tier === 'NONE'" class="mt-8 text-center">
      <p class="text-xs text-gray-400 dark:text-gray-500 mb-3">{{ t('upgrade.tier_trust_hint') }}</p>
      <div class="flex flex-wrap justify-center gap-1.5">
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Visa</span>
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Mastercard</span>
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Apple Pay</span>
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Google Pay</span>
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Amazon Pay</span>
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">Klarna</span>
        <span class="text-[11px] text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded px-2 py-0.5">PayPal</span>
      </div>
      <p class="text-xs text-gray-400 dark:text-gray-500 mt-3">
        {{ t('upgrade.support_hint') }}
        <a href="mailto:support@ev-monitor.net" class="underline hover:no-underline">support@ev-monitor.net</a>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRef } from 'vue';
import { useI18n } from 'vue-i18n';
import { CheckCircleIcon } from '@heroicons/vue/24/solid';
import { ChevronDownIcon, ChevronUpIcon, HeartIcon } from '@heroicons/vue/24/outline';
import { AUTOSYNC_BRANDS } from '../config/smartcarBrands';
import type { PricingInfo } from '../config/pricingConfig';
import { useUpgradeTierState, type SubscriptionTier } from '../composables/useUpgradeTierState';
import SmartcarFaq from './SmartcarFaq.vue';

const { t } = useI18n();

const props = withDefaults(defineProps<{
    /** 'account' = eingeloggter Transaktions-Flow (Stripe), 'public' = Marketing-Seite (CTAs -> Registrieren). */
    mode?: 'account' | 'public';
    tier?: SubscriptionTier;
    premiumEnabled?: boolean;
    pricing: PricingInfo;
    showTeslaOnlyFeatures?: boolean;
    showSmartcarFaq?: boolean;
    checkoutLoading?: boolean;
    checkoutError?: string;
    portalLoading?: boolean;
    portalError?: string;
}>(), {
    mode: 'account',
    tier: 'NONE',
    premiumEnabled: false,
    showTeslaOnlyFeatures: true,
    showSmartcarFaq: true,
    checkoutLoading: false,
    checkoutError: '',
    portalLoading: false,
    portalError: '',
});

const emit = defineEmits<{
    (e: 'checkout'): void;
    (e: 'manage'): void;
    (e: 'register'): void;
}>();

/** Zwei-Wege-Bindung des gewaehlten Abrechnungszeitraums (monatlich/jaehrlich). */
const selectedPlan = defineModel<'monthly' | 'yearly'>('selectedPlan', { default: 'yearly' });

const isPublic = computed(() => props.mode === 'public');

// Tesla wird nicht mehr ueber AutoSync verkauft (Fleet Telemetry laeuft gratis) - daher
// display-seitig aus den Marken-Chips raus. AUTOSYNC_BRANDS selbst bleibt unangetastet,
// weil es an anderer Stelle "welche Marken koennen ueberhaupt auto-syncen" bedeutet.
const autosyncBrandChips = computed(() => AUTOSYNC_BRANDS.filter(b => b !== 'Tesla'));

const {
    isAutoSyncActive,
    isLiveActive,
    showPlanToggle,
    activeBannerKey,
} = useUpgradeTierState(toRef(props, 'tier'));

// Feature-Preview-Expand: nur eines gleichzeitig sichtbar. AVAILABLE_PREVIEWS listet
// vorhandene PNGs in /public/upgrade-previews/; fehlende erzeugen sonst 404 + Layout-Shift.
const AVAILABLE_PREVIEWS = new Set<string>(['live_view', 'insights', 'drain']);
const expandedPreview = ref<string | null>(null);
const failedPreviews = ref(new Set<string>());
function hasPreview(key: string) {
    return AVAILABLE_PREVIEWS.has(key) && !failedPreviews.value.has(key);
}
function togglePreview(key: string) {
    expandedPreview.value = expandedPreview.value === key ? null : key;
}
function onPreviewError(key: string) {
    failedPreviews.value.add(key);
    failedPreviews.value = new Set(failedPreviews.value);
}
</script>

<style scoped>
.price-fade-enter-active,
.price-fade-leave-active {
    transition: opacity 150ms ease-out, transform 150ms ease-out;
}
.price-fade-enter-from {
    opacity: 0;
    transform: translateY(4px);
}
.price-fade-leave-to {
    opacity: 0;
    transform: translateY(-4px);
}
</style>
