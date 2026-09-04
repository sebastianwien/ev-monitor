<template>
    <!-- Ohne Abo: die AutoSync-Erzählseite (Tesla-Fahrer werden darin zum Supporter geleitet). -->
    <AutoSyncPitch v-if="!loading && tier === 'NONE'" />

    <div v-else class="py-6 md:py-12 px-4">
        <div v-if="loading" class="max-w-md mx-auto text-center py-16 text-gray-500 dark:text-gray-400">
            {{ t('upgrade.loading') }}
        </div>

        <!-- Mit aktivem Abo gibt es im Zwei-Tier-Modell nichts zu verkaufen (AutoSync
             enthaelt die Auswertungen, Supporter ist orthogonal). Statt eines Rasters
             eine ruhige Status-Karte; Verwalten/Kuendigen liegt in den Einstellungen. -->
        <div v-else class="max-w-md mx-auto bg-white dark:bg-gray-900 rounded-sm border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-6 md:p-8 text-center">
            <CheckBadgeIcon class="w-12 h-12 text-green-500 dark:text-green-400 mx-auto mb-3" />
            <p class="text-[13px] font-semibold uppercase tracking-wider text-green-600 dark:text-green-400 mb-1">{{ t('upgrade.active_title') }}</p>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ tierLabel }}</p>
            <p class="text-sm text-gray-500 dark:text-gray-400 mt-3 leading-relaxed">{{ t('upgrade.active_body') }}</p>
            <div class="mt-6 flex flex-col gap-2.5">
                <button
                    @click="handleManageSubscription" :disabled="portalLoading"
                    class="w-full bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white dark:text-gray-900 font-semibold py-3 rounded-sm text-sm shadow-[0_4px_0_0_#166534] dark:shadow-[0_4px_0_0_#064e3b] active:translate-y-1 active:shadow-none transition"
                >
                    {{ portalLoading ? '…' : t('upgrade.tier_active_manage') }}
                </button>
                <router-link
                    to="/settings"
                    class="w-full inline-flex items-center justify-center py-3 rounded-sm text-sm font-medium text-gray-600 dark:text-gray-300 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                    {{ t('upgrade.active_settings_cta') }}
                </router-link>
            </div>
            <p v-if="portalError" class="text-sm text-red-600 dark:text-red-400 mt-3">{{ portalError }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { CheckBadgeIcon } from '@heroicons/vue/24/outline';
import { subscriptionService } from '../api/subscriptionService';
import { analytics } from '../services/analytics';
import { type SubscriptionTier } from '../composables/useUpgradeTierState';
import AutoSyncPitch from '../components/AutoSyncPitch.vue';

const { t } = useI18n();

const tier = ref<SubscriptionTier>('NONE');
const loading = ref(true);
const portalLoading = ref(false);
const portalError = ref('');

// Produktnamen - nicht uebersetzt.
const TIER_LABELS: Record<string, string> = {
    AUTOSYNC: 'AutoSync',
    AUTOSYNC_LIVE: 'AutoSync Live',
    SUPPORTER: 'Supporter',
};
const tierLabel = computed(() => TIER_LABELS[tier.value] ?? tier.value);

onMounted(async () => {
    try {
        const status = await subscriptionService.getStatus();
        tier.value = status.tier ?? 'NONE';
        analytics.trackUpgradePageViewed();
    } finally {
        loading.value = false;
    }
});

async function handleManageSubscription() {
    portalLoading.value = true;
    portalError.value = '';
    try {
        const result = await subscriptionService.createPortalSession();
        window.location.href = result.portalUrl;
    } catch {
        portalError.value = t('upgrade.tier_portal_error');
        portalLoading.value = false;
    }
}
</script>
