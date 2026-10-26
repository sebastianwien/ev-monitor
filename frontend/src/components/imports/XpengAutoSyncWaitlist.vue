<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { BellAlertIcon, CheckCircleIcon, ExclamationCircleIcon, BoltIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { CI_BTN_SECONDARY, CI_LABEL, CI_CARD, CI_NOTE } from '../../utils/ciClasses'
import { useWaitlist } from '../../composables/useWaitlist'

const { t } = useI18n()
// bare: ohne eigene Box (Rahmen/Schatten/Tint) - fuer die Einbettung in eine bestehende Karte.
const props = defineProps<{ bare?: boolean }>()
const { onWaitlist, loaded, busy, error, load, join, leave } = useWaitlist('XPENG_AUTOSYNC')

onMounted(load)
</script>

<template>
  <!-- Marketing-Teaser: der manuelle ZIP-Upload ist heute noch Handarbeit - hier sammeln
       wir Interessenten fuer den kommenden automatischen Import (Opt-in Warteliste). -->
  <section
    v-if="loaded"
    :class="props.bare
      ? ''
      : CI_CARD">

    <template v-if="!onWaitlist">
      <p
        :class="[CI_LABEL, 'mb-2 flex items-center gap-1.5', props.bare ? 'justify-center' : '']"
>
        <BoltIcon class="h-4 w-4" aria-hidden="true" />
        {{ t('xpeng.waitlist.eyebrow') }}
      </p>
      <h3 class="mb-2 text-[18px] font-bold tracking-tight text-gray-900 dark:text-gray-100">
        {{ t('xpeng.waitlist.title') }}
      </h3>
      <p class="text-gray-600 dark:text-gray-300 text-sm leading-relaxed mb-4">
        {{ t('xpeng.waitlist.body') }}
      </p>

      <div v-if="error" role="alert"
           :class="[CI_NOTE, 'mb-4 flex gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
        <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
        <span>{{ error }}</span>
      </div>

      <button
        type="button"
        v-haptic
        :disabled="busy"
        :class="[CI_BTN_SECONDARY, 'w-full sm:w-auto']"
        @click="join">
        <ArrowPathIcon v-if="busy" class="h-5 w-5 animate-spin" aria-hidden="true" />
        <BellAlertIcon v-else class="h-5 w-5" aria-hidden="true" />
        <span>{{ busy ? t('xpeng.waitlist.joining') : t('xpeng.waitlist.cta') }}</span>
      </button>
    </template>

    <template v-else>
      <div class="flex items-start gap-3" :class="props.bare ? 'flex-col items-center text-center' : ''">
        <CheckCircleIcon class="h-6 w-6 shrink-0 text-green-700 dark:text-green-400" aria-hidden="true" />
        <div class="min-w-0">
          <h3 class="mb-1 text-lg font-bold tracking-tight text-gray-900 dark:text-gray-100">
            {{ t('xpeng.waitlist.joined_title') }}
          </h3>
          <p class="text-gray-600 dark:text-gray-300 text-sm leading-relaxed">
            {{ t('xpeng.waitlist.joined_body') }}
          </p>
          <div v-if="error" role="alert"
               :class="[CI_NOTE, 'mt-3 flex gap-2 py-3 font-semibold text-gray-900 dark:text-gray-100']">
            <ExclamationCircleIcon class="h-5 w-5 shrink-0 text-gray-600 dark:text-gray-300" aria-hidden="true" />
            <span>{{ error }}</span>
          </div>
          <button
            type="button"
            :disabled="busy"
            class="mt-2 inline-flex min-h-11 items-center text-[13px] font-semibold text-gray-600 underline hover:text-gray-900 disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-100"
            @click="leave">
            {{ t('xpeng.waitlist.leave') }}
          </button>
        </div>
      </div>
    </template>
  </section>
</template>
