<template>
  <div v-if="offer" data-testid="code-teaser"
       class="relative flex items-center gap-3 rounded-xl border-2 border-gray-800 dark:border-gray-200 bg-green-50 dark:bg-green-900/30 px-4 py-3">
    <GiftIcon class="h-6 w-6 shrink-0 text-green-700 dark:text-green-400" />
    <router-link :to="`/upgrade?code=${encodeURIComponent(offer.code)}`" class="flex-1 min-w-0 pr-6">
      <span class="block text-sm font-bold text-gray-900 dark:text-gray-100">{{ title }}</span>
      <span class="block text-sm text-green-800 dark:text-green-300 underline">{{ t('promo.banner_cta') }}</span>
    </router-link>
    <button type="button" data-testid="code-teaser-close" :aria-label="t('promo.banner_close')" @click="dismiss"
            class="absolute top-0 right-0 flex h-11 w-11 items-center justify-center rounded text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-100">
      <XMarkIcon class="h-5 w-5" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { GiftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useAuthStore } from '../../stores/auth'
import { subscriptionService, type PromoOffer } from '../../api/subscriptionService'

/**
 * Teaser for a promo code made for the user's sign-up source (FIRSTEV for firstev readers).
 * Asks the backend once per session, only for logged-in users without subscription. Closing
 * hides it for good on this device.
 */
const { t, te } = useI18n()
const authStore = useAuthStore()

const SESSION_KEY = 'promoOffer'
const dismissedKey = (code: string) => `promoBannerDismissed:${code}`
const offer = ref<PromoOffer | null>(null)

const title = computed(() => {
  if (!offer.value) return ''
  const key = `promo.banner_${offer.value.code}`
  return te(key) ? t(key) : t('promo.banner_generic', { days: offer.value.trialDays })
})

function readStorage(storage: Storage, key: string): string | null {
  try { return storage.getItem(key) } catch { return null }
}

function writeStorage(storage: Storage, key: string, value: string) {
  try { storage.setItem(key, value) } catch { /* storage blocked: banner just shows again */ }
}

async function loadOffer(): Promise<PromoOffer | null> {
  const cached = readStorage(sessionStorage, SESSION_KEY)
  if (cached !== null) return cached ? JSON.parse(cached) as PromoOffer : null
  const fresh = await subscriptionService.getPromoOffer()
  writeStorage(sessionStorage, SESSION_KEY, fresh ? JSON.stringify(fresh) : '')
  return fresh
}

onMounted(async () => {
  if (!authStore.token || authStore.isPremium) return
  try {
    const found = await loadOffer()
    if (found && !readStorage(localStorage, dismissedKey(found.code))) offer.value = found
  } catch { /* no banner */ }
})

function dismiss() {
  if (offer.value) writeStorage(localStorage, dismissedKey(offer.value.code), '1')
  offer.value = null
}
</script>
