<template>
  <!-- The four questions the page answers, asked by a buyer. From md up the buyer stands in the
       middle with two speech bubbles on each side; on the phone it reads like a messenger thread,
       questions on the left, EV Monitor answering on the right. Only one layout is displayed at a time. -->
  <div class="grid gap-4">
    <div class="hidden gap-5 md:grid" data-testid="buyer-desktop">
      <p class="text-center text-[12px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('model.page.cta_title') }}</p>
      <div class="grid grid-cols-[minmax(0,1fr)_128px_minmax(0,1fr)] items-center gap-x-5 gap-y-3.5">
        <img :src="PERSON" alt="" width="128" height="128" class="col-start-2 row-span-2 row-start-1 h-32 w-32 justify-self-center rounded-full bg-green-100 dark:bg-green-900/40" />
        <p v-for="(q, i) in QUESTIONS" :key="q.key" data-question
           class="bubble relative flex items-center gap-2 rounded-[22px] bg-white px-4 py-3 text-[16px] font-semibold leading-snug text-gray-900 dark:bg-gray-900 dark:text-gray-100"
           :class="DESKTOP_PLACE[i]">
          <component :is="q.icon" class="h-5 w-5 flex-none text-green-600 dark:text-green-400" aria-hidden="true" />{{ t(`model.page.cta_${q.key}`) }}
          <!-- Tail towards the buyer in the middle -->
          <svg class="absolute bottom-0 h-[30px] w-[26px] text-white dark:text-gray-900" :class="i < 2 ? '-right-[18px]' : '-left-[18px] -scale-x-100'" viewBox="0 0 26 30" aria-hidden="true">
            <path d="M0 0C8 11 15 22 26 30C15 31 6 30 0 27Z" fill="currentColor" />
          </svg>
        </p>
      </div>
      <a href="#profil" class="group inline-flex min-h-12 items-center justify-center gap-2 justify-self-center rounded-xl bg-green-600 px-6 text-[16px] font-bold text-white shadow-[0_6px_16px_rgba(22,163,74,0.25)] transition-colors hover:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
         data-testid="hero-jump" @click="go">
        {{ t('model.page.cta_button') }}<ArrowDownIcon class="h-5 w-5 motion-safe:transition-transform motion-safe:group-hover:translate-y-0.5" aria-hidden="true" />
      </a>
    </div>

    <div class="grid gap-4 md:hidden" data-testid="buyer-phone">
      <p class="text-center text-[12px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('model.page.cta_title') }}</p>
      <!-- The buyer's side of the thread, left-aligned like the answer is right-aligned -->
      <div class="grid justify-items-start gap-2">
        <p v-for="(q, i) in QUESTIONS" :key="q.key" data-question
           class="bubble relative flex items-center gap-2 rounded-[22px] bg-white px-4 py-2.5 text-[15px] font-semibold leading-snug text-gray-900 dark:bg-gray-900 dark:text-gray-100"
           :class="i === QUESTIONS.length - 1 ? 'rounded-bl-[6px]' : ''">
          <component :is="q.icon" class="h-[18px] w-[18px] flex-none text-green-600 dark:text-green-400" aria-hidden="true" />{{ t(`model.page.cta_${q.key}`) }}
          <!-- The last question of the thread has the tail, as in a messenger -->
          <svg v-if="i === QUESTIONS.length - 1" class="absolute -left-[14px] bottom-0 h-[30px] w-[26px] -scale-x-100 text-white dark:text-gray-900" viewBox="0 0 26 30" aria-hidden="true">
            <path d="M0 0C8 11 15 22 26 30C15 31 6 30 0 27Z" fill="currentColor" />
          </svg>
        </p>
      </div>
      <div class="grid justify-items-end gap-1.5">
        <span class="text-[12px] font-semibold text-gray-500 dark:text-gray-400">{{ t('model.page.cta_from') }}</span>
        <a href="#profil" class="bubble relative inline-flex min-h-12 items-center justify-center gap-2 rounded-[22px] rounded-br-[6px] bg-green-600 px-5 text-[16px] font-bold text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
           @click="go">
          {{ t('model.page.cta_reply') }}<ArrowDownIcon class="h-5 w-5" aria-hidden="true" />
          <svg class="absolute -right-[14px] bottom-0 h-[30px] w-[26px] text-green-600" viewBox="0 0 26 30" aria-hidden="true">
            <path d="M0 0C8 11 15 22 26 30C15 31 6 30 0 27Z" fill="currentColor" />
          </svg>
        </a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDownIcon, BanknotesIcon, BoltIcon, CloudIcon, MapIcon } from '@heroicons/vue/24/outline'
import { scrollToElement } from '../../utils/smoothScroll'

const emit = defineEmits<{ jump: [] }>()
const { t } = useI18n()

/** Glides to the driving profile instead of jumping; the link still works without script */
function go(event: MouseEvent) {
  const profile = document.getElementById('profil')
  if (profile) {
    event.preventDefault()
    scrollToElement(profile)
  }
  emit('jump')
}

/** Open Peeps by Pablo Stanley, CC0 1.0, see public/illustrations/LICENSE.md */
const PERSON = '/illustrations/buyer.svg'
/** Same order and icons as the answers in the result box */
const QUESTIONS = [
  { key: 'winter', icon: CloudIcon },
  { key: 'rhythm', icon: BoltIcon },
  { key: 'trip', icon: MapIcon },
  { key: 'cost', icon: BanknotesIcon },
] as const
/** Two bubbles left, two right of the buyer, the lower ones slightly further in; the corner at the tail is flatter */
const DESKTOP_PLACE = [
  'col-start-1 row-start-1 justify-self-end rounded-br-[6px]',
  'col-start-1 row-start-2 justify-self-end mr-6 rounded-br-[6px]',
  'col-start-3 row-start-1 justify-self-start rounded-bl-[6px]',
  'col-start-3 row-start-2 justify-self-start ml-6 rounded-bl-[6px]',
] as const
</script>

<style scoped>
/* One soft shadow around bubble and tail together, so the tail has no seam */
.bubble { filter: drop-shadow(0 1px 1px rgb(0 0 0 / 0.06)) drop-shadow(0 4px 10px rgb(0 0 0 / 0.07)); }
:global(.dark) .bubble { filter: drop-shadow(0 1px 1px rgb(0 0 0 / 0.4)) drop-shadow(0 4px 10px rgb(0 0 0 / 0.3)); }
</style>
