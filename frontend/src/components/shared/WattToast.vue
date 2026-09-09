<script setup lang="ts">
/** "+n Watt erhalten!" nach einer Aktion. Aufrufer: ref.show(n); rendert bei 0 nichts. */
import { ref } from 'vue'
import { BoltIcon } from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const visible = ref(false)
const amount = ref(0)
const note = ref('')
let timer: ReturnType<typeof setTimeout> | null = null

/** @param extra zusaetzliche Zeile, z.B. "3 weitere Ladungen bepreist" - zeigt den Toast auch bei 0 Watt. */
function show(n: number, extra = '') {
  if (n <= 0 && !extra) return
  amount.value = n
  note.value = extra
  visible.value = true
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { visible.value = false }, 4000)
}
defineExpose({ show })
</script>

<template>
  <div v-if="visible" role="status" class="fixed bottom-6 right-6 z-50 animate-slide-in">
    <div class="bg-green-600 text-white px-5 py-3 rounded-sm shadow-[6px_6px_0_rgba(0,0,0,0.40)] dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)] flex items-center gap-2">
      <BoltIcon class="h-5 w-5 flex-shrink-0" aria-hidden="true" />
      <span class="text-sm">
        <span v-if="amount > 0" class="font-medium">{{ t('watt.toast', { n: amount }) }}</span>
        <span v-if="note" :class="amount > 0 ? 'block text-xs text-white/80' : 'font-medium'">{{ note }}</span>
      </span>
    </div>
  </div>
</template>
