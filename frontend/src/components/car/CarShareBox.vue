<template>
  <div class="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/60 px-3 py-2.5">
    <div class="flex items-start gap-3">
      <div class="min-w-0 flex-1">
        <p class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ t('share_car.label') }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ t('share_car.hint') }}</p>
      </div>
      <button
        type="button"
        role="switch"
        :aria-checked="!!share"
        :aria-label="t('share_car.label')"
        :disabled="busy"
        @click="toggle"
        class="relative shrink-0 -m-2.5 p-2.5 min-h-[44px] min-w-[44px] flex items-center disabled:opacity-60">
        <span :class="['relative inline-flex h-6 w-11 items-center rounded-full transition',
                       share ? 'bg-green-500' : 'bg-gray-300 dark:bg-gray-600']">
          <span :class="['inline-block h-5 w-5 transform rounded-full bg-white shadow transition',
                         share ? 'translate-x-5' : 'translate-x-0.5']" />
        </span>
      </button>
    </div>

    <div v-if="share" class="mt-2.5 flex items-center gap-2">
      <a :href="share.url" target="_blank" rel="noopener"
        class="min-w-0 flex-1 truncate text-xs text-indigo-600 dark:text-indigo-300 hover:underline">
        {{ share.url.replace(/^https?:\/\//, '') }}
      </a>
      <button type="button" v-haptic @click="onShare"
        class="btn-3d [--btn-shadow-color:#a5b4fc] dark:[--btn-shadow-color:#3730a3] inline-flex items-center gap-1.5 bg-indigo-100 dark:bg-indigo-700 text-indigo-800 dark:text-white px-3 py-2 rounded-sm text-sm font-semibold min-h-[44px]">
        <CheckIcon v-if="outcome === 'copied'" class="w-4 h-4" />
        <ShareIcon v-else class="w-4 h-4" />
        {{ outcome === 'copied' ? t('share_car.link_copied') : t('share_car.share_link') }}
      </button>
    </div>
    <p v-if="error" class="mt-2 text-xs text-red-600 dark:text-red-400">{{ t('share_car.error_failed') }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShareIcon, CheckIcon } from '@heroicons/vue/24/outline'
import { useCarShare, type CarShareOutcome } from '../../composables/useCarShare'

const props = defineProps<{ carId: string; title: string }>()
const { t } = useI18n()
const { share, busy, error, load, enable, revoke, shareLink } = useCarShare()
const outcome = ref<CarShareOutcome | null>(null)

onMounted(() => load(props.carId))

async function toggle() {
  if (share.value) await revoke(props.carId)
  else await enable(props.carId)
}

async function onShare() {
  if (!share.value) return
  outcome.value = await shareLink(share.value.url, props.title)
  if (outcome.value === 'copied') setTimeout(() => { outcome.value = null }, 2000)
}
</script>
