<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { QuestionMarkCircleIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { GLOSSARY } from './seoGlossary'
import { TOPIC_HELP, type SeoTopic } from './seoHelp'

/** Anleitung je Ansicht. Beim ersten Besuch einer Ansicht offen, danach eingeklappt (gemerkt im Browser). */
const props = defineProps<{ topic: SeoTopic }>()

const STORAGE_PREFIX = 'seo-help-closed-'
const open = ref(false)

function readClosed(topic: SeoTopic): boolean {
  try {
    return localStorage.getItem(STORAGE_PREFIX + topic) === '1'
  } catch {
    return false
  }
}

function setOpen(value: boolean) {
  open.value = value
  try {
    if (value) localStorage.removeItem(STORAGE_PREFIX + props.topic)
    else localStorage.setItem(STORAGE_PREFIX + props.topic, '1')
  } catch {
    // ohne Speicher bleibt die Hilfe einfach offen
  }
}

watch(() => props.topic, t => { open.value = !readClosed(t) }, { immediate: true })

const help = computed(() => TOPIC_HELP[props.topic])
const terms = computed(() => help.value.terms.map(k => GLOSSARY[k]).filter(Boolean))
</script>

<template>
  <div>
    <button v-if="!open" type="button"
            class="inline-flex items-center gap-1.5 text-xs text-indigo-300 hover:text-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm"
            @click="setOpen(true)">
      <QuestionMarkCircleIcon class="h-4 w-4" aria-hidden="true" />
      So nutzt du diese Ansicht
    </button>
    <section v-else class="bg-indigo-950/40 border border-indigo-900 rounded-sm p-4 text-sm text-gray-200" aria-label="Anleitung">
      <div class="flex items-start justify-between gap-3">
        <h3 class="font-semibold text-indigo-200">{{ help.question }}</h3>
        <button type="button" class="p-1 -m-1 text-gray-400 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 rounded-sm"
                aria-label="Anleitung schließen" @click="setOpen(false)">
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
      <div class="grid md:grid-cols-2 gap-4 mt-3">
        <div>
          <h4 class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">So liest du sie</h4>
          <ul class="list-disc pl-5 space-y-1">
            <li v-for="(t, i) in help.read" :key="i">{{ t }}</li>
          </ul>
        </div>
        <div>
          <h4 class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-1">So gehst du vor</h4>
          <ul class="list-disc pl-5 space-y-1">
            <li v-for="(t, i) in help.act" :key="i">{{ t }}</li>
          </ul>
        </div>
      </div>
      <details class="mt-3">
        <summary class="cursor-pointer text-xs font-semibold uppercase tracking-wide text-gray-400">Begriffe</summary>
        <dl class="mt-2 grid md:grid-cols-2 gap-x-6 gap-y-2">
          <div v-for="t in terms" :key="t.term">
            <dt class="font-medium text-gray-100">{{ t.term }}</dt>
            <dd class="text-gray-300">{{ t.text }}</dd>
          </div>
        </dl>
      </details>
    </section>
  </div>
</template>
