<template>
    <div class="py-6 md:py-12 px-4">
        <PricingTiers
            mode="public"
            :pricing="pricing"
            v-model:selected-plan="selectedPlan"
            @register="goToRegister"
        />
    </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { useHead } from '@unhead/vue';
import { useCountryStore } from '../stores/country';
import { getPricing } from '../config/pricingConfig';
import { analytics } from '../services/analytics';
import PricingTiers from '../components/PricingTiers.vue';

const { t } = useI18n();
const router = useRouter();
const countryStore = useCountryStore();

// Preise sind reine statische Config (kein API-Call) und funktionieren daher
// ohne Login - Voraussetzung fuer die oeffentliche Seite.
const pricing = computed(() => getPricing(countryStore.country));
const selectedPlan = ref<'monthly' | 'yearly'>('yearly');

function goToRegister() {
    analytics.track('cta_register_clicked', { source: 'pricing_page' });
    router.push('/register');
}

useHead(computed(() => ({
    title: t('upgrade.public_meta_title'),
    meta: [
        { name: 'description', content: t('upgrade.public_meta_description') },
        { name: 'robots', content: 'index, follow' },
        { property: 'og:title', content: t('upgrade.public_meta_title') },
        { property: 'og:description', content: t('upgrade.public_meta_description') },
        { property: 'og:type', content: 'website' },
        { property: 'og:url', content: 'https://ev-monitor.net/preise' },
    ],
    link: [
        { rel: 'canonical', href: 'https://ev-monitor.net/preise' },
    ],
})));
</script>
