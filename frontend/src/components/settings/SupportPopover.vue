<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { HeartIcon } from '@heroicons/vue/24/solid'
import { analytics } from '../../services/analytics'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  variant?: 'nav' | 'footer' | 'block'
}>(), {
  variant: 'nav'
})
const { t } = useI18n()

const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const KOFI_URL = 'https://ko-fi.com/ev_monitor'
const PAYPAL_URL = 'https://www.paypal.com/donate/?hosted_button_id=HYWKA4WJJ9CE8'

const openKofi = () => {
  analytics.track('kofi_clicked')
  window.open(KOFI_URL, '_blank', 'noopener,noreferrer,width=550,height=650,left=200,top=100')
  open.value = false
}

const openPaypal = () => {
  analytics.track('paypal_clicked')
  window.open(PAYPAL_URL, '_blank', 'noopener,noreferrer')
  open.value = false
}

const handleOutsideClick = (e: MouseEvent) => {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', handleOutsideClick))
onUnmounted(() => document.removeEventListener('click', handleOutsideClick))
</script>

<template>
  <!-- Block variant (Settings page) -->
  <div v-if="variant === 'block'" class="flex gap-3">
    <button
      @click="openPaypal"
      class="btn-3d flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 rounded-lg hover:bg-indigo-200 dark:hover:bg-indigo-900/60 transition font-medium shadow-[0_4px_0_0_#a5b4fc] dark:shadow-[0_4px_0_0_#312e81] active:shadow-none active:translate-y-1" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
      <img src="https://www.paypalobjects.com/webstatic/icon/pp16.png" alt="PayPal" class="h-4 w-4" />
      <span>Per PayPal</span>
    </button>
    <button
      @click="openKofi"
      class="btn-3d flex-1 flex items-center justify-center gap-2 px-4 py-3 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 rounded-lg hover:bg-indigo-200 dark:hover:bg-indigo-900/60 transition font-medium shadow-[0_4px_0_0_#a5b4fc] dark:shadow-[0_4px_0_0_#312e81] active:shadow-none active:translate-y-1" style="transition: transform 0.075s ease, box-shadow 0.075s ease;">
      <HeartIcon class="h-4 w-4" />
      <span>Per Ko-fi</span>
    </button>
  </div>

  <!-- Nav/Footer popover variant -->
  <div v-else ref="containerRef" class="relative">
    <!-- Trigger button -->
    <button
      v-if="variant === 'nav'"
      @click.stop="open = !open"
      class="inline-flex items-center gap-1.5 px-3 py-1.5 text-white hover:text-indigo-200 transition text-sm font-medium cursor-pointer"
      :title="t('support.footer_btn')">
      <HeartIcon class="h-4 w-4 text-red-400" />
      {{ t('support.nav_btn') }}
    </button>
    <button
      v-else
      @click.stop="open = !open"
      class="inline-flex items-center gap-1.5 text-red-500 hover:text-red-600 transition font-medium cursor-pointer">
      <HeartIcon class="h-4 w-4" />
      {{ t('support.footer_btn') }}
    </button>

    <!-- Popover -->
    <Transition
      enter-active-class="transition ease-out duration-150"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition ease-in duration-100"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95">
      <div
        v-if="open"
        class="absolute z-50 mt-2 w-52 rounded-xl shadow-lg bg-white dark:bg-gray-800 ring-1 ring-black/10 overflow-hidden"
        :class="variant === 'footer' ? 'bottom-full mb-2 left-1/2 -translate-x-1/2' : 'right-0'">
        <div class="p-1">
          <button
            @click="openPaypal"
            class="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-blue-50 dark:hover:bg-blue-900/30 hover:text-blue-700 transition cursor-pointer">
            <img src="https://www.paypalobjects.com/webstatic/icon/pp16.png" alt="PayPal" class="h-4 w-4" />
            PayPal
          </button>
          <button
            @click="openKofi"
            class="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-red-50 dark:hover:bg-red-900/30 hover:text-red-600 transition cursor-pointer">
            <HeartIcon class="h-4 w-4 text-red-400" />
            Ko-fi
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
