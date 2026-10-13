<template>
  <!-- The four questions the page answers, asked by a buyer. From md up the buyer stands in the
       middle with two speech bubbles on each side; on the phone it reads like a messenger thread
       with ev-monitor answering. Only one of the two layouts is displayed at a time. -->
  <div class="grid gap-4">
    <div class="hidden gap-5 md:grid" data-testid="buyer-desktop">
      <p class="text-center text-[12px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('model.page.cta_title') }}</p>
      <div class="grid grid-cols-[minmax(0,1fr)_128px_minmax(0,1fr)] items-center gap-x-5 gap-y-3.5">
        <img :src="PERSON" alt="" width="128" height="128" class="col-start-2 row-span-2 row-start-1 h-32 w-32 justify-self-center rounded-full bg-green-100 dark:bg-green-900/40" />
        <p v-for="(q, i) in QUESTIONS" :key="q.key" data-question
           class="bubble relative flex items-center gap-2 rounded-[18px] border border-gray-200 bg-white px-4 py-2.5 text-[16px] font-semibold leading-snug text-gray-900 shadow-[0_1px_2px_rgba(0,0,0,0.05)] dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
           :class="DESKTOP_PLACE[i]">
          <component :is="q.icon" class="h-5 w-5 flex-none text-green-600 dark:text-green-400" aria-hidden="true" />{{ t(`model.page.cta_${q.key}`) }}
        </p>
      </div>
      <a href="#profil" class="group inline-flex min-h-12 items-center justify-center gap-2 justify-self-center rounded-xl bg-green-600 px-6 text-[16px] font-bold text-white shadow-[0_6px_16px_rgba(22,163,74,0.25)] transition-colors hover:bg-green-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
         data-testid="hero-jump" @click="go">
        {{ t('model.page.cta_button') }}<ArrowDownIcon class="h-5 w-5 motion-safe:transition-transform motion-safe:group-hover:translate-y-0.5" aria-hidden="true" />
      </a>
    </div>

    <div class="grid gap-4 md:hidden" data-testid="buyer-phone">
      <p class="text-[12px] font-semibold uppercase tracking-[0.08em] text-green-700 dark:text-green-400">{{ t('model.page.cta_title') }}</p>
      <div class="grid grid-cols-[44px_minmax(0,1fr)] items-end gap-x-2.5 gap-y-2">
        <img :src="PERSON" alt="" width="44" height="44" class="col-start-1 row-span-4 row-start-1 h-11 w-11 self-end rounded-full bg-green-100 dark:bg-green-900/40" />
        <p v-for="(q, i) in QUESTIONS" :key="q.key" data-question
           class="col-start-2 flex items-center gap-2 justify-self-start rounded-[18px] border border-gray-200 bg-white px-3.5 py-2.5 text-[15px] font-semibold leading-snug text-gray-900 shadow-[0_1px_2px_rgba(0,0,0,0.05)] dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
           :class="i === QUESTIONS.length - 1 ? 'rounded-bl-md' : ''">
          <component :is="q.icon" class="h-[18px] w-[18px] flex-none text-green-600 dark:text-green-400" aria-hidden="true" />{{ t(`model.page.cta_${q.key}`) }}
        </p>
      </div>
      <div class="grid justify-items-end gap-1.5">
        <span class="text-[12px] font-semibold text-gray-500 dark:text-gray-400">{{ t('model.page.cta_from') }}</span>
        <a href="#profil" class="group inline-flex min-h-12 items-center justify-center gap-2 rounded-[18px] rounded-br-md bg-green-600 px-5 text-[16px] font-bold text-white shadow-[0_6px_16px_rgba(22,163,74,0.25)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600"
           @click="go">
          {{ t('model.page.cta_reply') }}<ArrowDownIcon class="h-5 w-5" aria-hidden="true" />
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
/** Two bubbles left, two right of the buyer, the lower ones slightly further in; tails point at the buyer */
const DESKTOP_PLACE = [
  'bubble-right col-start-1 row-start-1 justify-self-end',
  'bubble-right col-start-1 row-start-2 justify-self-end mr-6',
  'bubble-left col-start-3 row-start-1 justify-self-start',
  'bubble-left col-start-3 row-start-2 justify-self-start ml-6',
] as const
</script>

<style scoped>
/* The tail of a speech bubble, towards the buyer in the middle */
.bubble-right::after,
.bubble-left::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  background: inherit;
  border: inherit;
  transform: translateY(-50%) rotate(45deg);
}
.bubble-right::after { right: -7px; border-left: 0; border-bottom: 0; }
.bubble-left::after { left: -7px; border-right: 0; border-top: 0; }
</style>
