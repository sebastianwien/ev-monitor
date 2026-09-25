<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { siPaypal, siVisa, siMastercard, siApplepay, siGooglepay } from 'simple-icons'
import { detectExpressWallet } from '../composables/useExpressWallet'

/**
 * Vertrauenssignal unter der Preiskarte: monochrome Wortmarken statt Text-Pills.
 * Reihenfolge nach Verbreitung im deutschsprachigen Kernmarkt. Es werden nur
 * Zahlarten gezeigt, die Stripe Checkout tatsächlich anbietet.
 */
const props = withDefaults(defineProps<{ note?: string; size?: 'sm' | 'md' }>(), { note: '', size: 'md' })

const { t } = useI18n()

// scale gleicht die optische Masse der Marken an: das PayPal-Monogramm füllt die
// 24er-Box komplett, die Wortmarken von Visa und Google Pay nur zur Hälfte.
const methods = [
  { title: 'PayPal', icon: siPaypal, scale: 0.8 },
  { title: 'Visa', icon: siVisa, scale: 1.1 },
  { title: 'Mastercard', icon: siMastercard, scale: 1 },
  { title: 'Apple Pay', icon: siApplepay, scale: 1 },
  { title: 'Google Pay', icon: siGooglepay, scale: 1.15 },
]

const wallet = detectExpressWallet()
// Eine Zeile: Stripe-Hinweis plus, wenn erkennbar, die Express-Wallet des Geräts.
const line = computed(() => {
  const parts = [props.note]
  if (wallet === 'apple') parts.push(t('payment_methods.express_apple'))
  if (wallet === 'google') parts.push(t('payment_methods.express_google'))
  return parts.filter(Boolean).join(' · ')
})

const baseHeight = computed(() => props.size === 'sm' ? 20 : 36)
</script>

<template>
  <div class="text-center">
    <p v-if="line" class="text-sm text-gray-500 dark:text-gray-400 mb-3">{{ line }}</p>
    <ul :aria-label="t('payment_methods.aria')" class="flex items-center justify-center gap-5 sm:gap-6 text-gray-500 dark:text-gray-400">
      <li v-for="m in methods" :key="m.title" class="flex items-center">
        <svg role="img" viewBox="0 0 24 24" :style="{ height: `${Math.round(baseHeight * m.scale)}px` }" class="w-auto fill-current" aria-hidden="false">
          <title>{{ m.title }}</title>
          <path :d="m.icon.path" />
        </svg>
      </li>
    </ul>
  </div>
</template>
