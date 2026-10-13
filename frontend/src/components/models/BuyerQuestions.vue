<template>
  <!-- The four questions the page answers, asked by a buyer. From md up the buyer stands in the
       middle with two speech bubbles on each side; on the phone it reads like a messenger thread,
       questions on the left, EV Monitor answering on the right. Only one layout is displayed at a time. -->
  <div class="grid gap-4">
    <div class="hidden gap-5 md:grid" data-testid="buyer-desktop">
      <h2 class="text-center text-[22px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('model.page.cta_title') }}</h2>
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

    <!-- Phone: a messenger thread in the look of WhatsApp, the buyer on the left, EV Monitor answering on the right -->
    <div class="chat-wall -mx-4 -my-5 grid gap-1.5 px-3 pb-6 pt-4 md:hidden" data-testid="buyer-phone">
      <h2 class="mb-2 text-center text-[19px] font-bold tracking-tight text-gray-900 dark:text-gray-100">{{ t('model.page.cta_title') }}</h2>
      <p v-for="(q, i) in CHAT_QUESTIONS" :key="q.key"
         class="relative mr-10 flex items-end gap-2 justify-self-start rounded-lg bg-white py-1.5 pl-2.5 pr-2 text-[15px] leading-snug text-gray-900 shadow-[0_1px_0.5px_rgba(0,0,0,0.13)] dark:bg-gray-800 dark:text-gray-100"
         :class="i === 0 ? 'ml-2 rounded-tl-none' : 'ml-2'">
        <!-- WhatsApp's tail sits at the top of the first message of a group -->
        <svg v-if="i === 0" class="absolute -left-2 top-0 h-[13px] w-2 text-white dark:text-gray-800" viewBox="0 0 8 13" aria-hidden="true">
          <path d="M8 0H1.5C.4 0-.2 1.3.6 2.1L8 9.5Z" fill="currentColor" />
        </svg>
        <span class="flex items-center gap-1.5 py-0.5" data-question>
          <component :is="q.icon" class="h-[17px] w-[17px] flex-none text-green-600 dark:text-green-400" aria-hidden="true" />{{ t(`model.page.cta_${q.key}`) }}
        </span>
        <span class="shrink-0 translate-y-0.5 text-[11px] tabular-nums text-gray-400" aria-hidden="true">{{ time }}</span>
      </p>
      <a href="#profil" class="relative ml-10 mr-2 mt-2 flex items-end gap-2 justify-self-end rounded-lg rounded-tr-none bg-[#d9fdd3] py-2 pl-3 pr-2 text-[16px] font-bold text-gray-900 shadow-[0_1px_0.5px_rgba(0,0,0,0.13)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-[#005c4b] dark:text-white"
         @click="go">
        <svg class="absolute -right-2 top-0 h-[13px] w-2 text-[#d9fdd3] dark:text-[#005c4b]" viewBox="0 0 8 13" aria-hidden="true">
          <path d="M0 0h6.5c1.1 0 1.7 1.3.9 2.1L0 9.5Z" fill="currentColor" />
        </svg>
        <span class="grid gap-0.5">
          <span class="text-[12.5px] font-semibold text-green-700 dark:text-green-300">{{ t('model.page.cta_from') }}</span>
          <span class="inline-flex items-center gap-1.5">{{ t('model.page.cta_reply') }}<ArrowDownIcon class="h-[18px] w-[18px]" aria-hidden="true" /></span>
        </span>
        <span class="inline-flex shrink-0 translate-y-0.5 items-center gap-0.5 text-[11px] font-normal tabular-nums text-gray-500 dark:text-gray-300" aria-hidden="true">
          {{ time }}
          <svg class="h-3.5 w-4 text-sky-500" viewBox="0 0 16 11" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="m1 6 3 3 6-8M6 8.5 7 9.5l6-8" /></svg>
        </span>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDownIcon, BanknotesIcon, BoltIcon, CloudIcon, MapIcon } from '@heroicons/vue/24/outline'
import { scrollToElement } from '../../utils/smoothScroll'

const emit = defineEmits<{ jump: [] }>()
const { t, locale } = useI18n()

/** The time stamp in every bubble, as a messenger shows it: now, in the reader's locale */
const time = new Intl.DateTimeFormat(locale.value, { hour: '2-digit', minute: '2-digit' }).format(new Date())

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
/** In the chat the rhythm comes first: the everyday question opens the thread */
const CHAT_QUESTIONS = [QUESTIONS[1], QUESTIONS[0], QUESTIONS[2], QUESTIONS[3]]
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
.dark .bubble { filter: drop-shadow(0 1px 1px rgb(0 0 0 / 0.4)) drop-shadow(0 4px 10px rgb(0 0 0 / 0.3)); }
/* Chat wallpaper: the light green of the page with a faint dot pattern, like a messenger background */
.chat-wall {
  background-color: rgb(240 253 244);
  background-image: radial-gradient(rgb(22 163 74 / 0.10) 1px, transparent 1.2px);
  background-size: 14px 14px;
}
.dark .chat-wall {
  background-color: rgb(5 46 22 / 0.35);
  background-image: radial-gradient(rgb(74 222 128 / 0.10) 1px, transparent 1.2px);
}
</style>
