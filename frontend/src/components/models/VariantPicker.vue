<template>
  <div ref="root" class="relative">
    <!-- Two lines, so it reads as a choice: "Variante wählen" over the battery the page is calculated with -->
    <button ref="trigger" type="button" data-testid="variant-picker"
            class="btn-3d cta-shadow inline-flex max-w-full items-center gap-3 rounded-2xl border-2 border-gray-900 bg-white py-1.5 pl-3.5 pr-3 text-left text-gray-900 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:border-gray-200 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800"
            aria-haspopup="dialog" :aria-expanded="open" @click="toggle">
      <span class="grid min-w-0 leading-tight">
        <span class="text-[11.5px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ t('model.page.variant_button') }}</span>
        <span class="truncate text-[14.5px] font-bold">{{ current ? `${current.label}, ${current.battery}` : '' }}</span>
      </span>
      <ChevronDownIcon class="h-5 w-5 flex-none transition-transform" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
    </button>

    <!-- Desktop: a popover under the button -->
    <div v-if="open && desktop" ref="panel" role="dialog" :aria-label="t('model.page.variant_title')"
         class="absolute right-0 top-[calc(100%+8px)] z-40 grid w-[min(640px,calc(100vw-48px))] gap-3 rounded-2xl bg-white p-4 edge-box dark:border-gray-700 dark:bg-gray-900">
      <p class="text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.page.variant_title') }}</p>
      <div class="grid grid-cols-2 gap-2">
        <VariantPickerItem v-for="(o, i) in options" :key="o.key" :option="o" :selected="i === modelValue" @choose="choose(i)" />
      </div>
    </div>

    <!-- Phone: a bottom sheet; teleported, the page wrapper animates with a transform -->
    <Teleport to="body">
      <template v-if="open && !desktop">
        <div class="fixed inset-0 z-50 bg-gray-900/45 dark:bg-black/60" aria-hidden="true" @click="close()"></div>
        <div ref="panel" role="dialog" aria-modal="true" :aria-label="t('model.page.variant_title')"
             class="fixed inset-x-0 bottom-0 z-50 grid max-h-[80vh] gap-3 overflow-y-auto rounded-t-3xl bg-white px-4 pb-[calc(env(safe-area-inset-bottom)+20px)] pt-2.5 shadow-[0_-10px_30px_rgba(0,0,0,0.2)] dark:bg-gray-900">
          <span class="h-1 w-10 justify-self-center rounded-full bg-gray-200 dark:bg-gray-700" aria-hidden="true"></span>
          <div class="flex items-center justify-between">
            <p class="text-[17px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.page.variant_title') }}</p>
            <button type="button" class="grid h-11 w-11 place-items-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800" :aria-label="t('model.page.variant_close')" @click="close()">
              <XMarkIcon class="h-6 w-6" aria-hidden="true" />
            </button>
          </div>
          <div class="grid gap-2">
            <VariantPickerItem v-for="(o, i) in options" :key="o.key" :option="o" :selected="i === modelValue" @choose="choose(i)" />
          </div>
        </div>
      </template>
    </Teleport>
  </div>
</template>

<script lang="ts">
/** One battery as the picker shows it; range null when the model has no measured consumption */
export interface VariantOption {
  key: string
  label: string
  battery: string
  range: string | null
  prospect: string
}
</script>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, type PropType } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDownIcon, XMarkIcon } from '@heroicons/vue/24/outline'

/**
 * The battery of the model page as one button: "Akku Pro, 59 kWh". Opens a bottom sheet on the
 * phone and a popover from md up, each battery a card with its range and the prospectus figure.
 * Escape, a click outside and a choice close it; the focus goes back to the button.
 */
const props = defineProps<{ options: VariantOption[]; modelValue: number }>()
const emit = defineEmits<{ 'update:modelValue': [index: number] }>()
const { t } = useI18n()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const current = computed(() => props.options[props.modelValue] ?? null)

/** md breakpoint, the same as Tailwind's; without matchMedia (tests) the phone sheet */
const desktopQuery = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(min-width: 768px)') : null
const desktop = ref(desktopQuery?.matches ?? false)
const onQuery = (e: MediaQueryListEvent) => { desktop.value = e.matches }

async function toggle() {
  if (open.value) return close()
  open.value = true
  await nextTick()
  panel.value?.querySelector<HTMLElement>('[aria-pressed="true"]')?.focus()
}

function close(focusBack = true) {
  open.value = false
  if (focusBack) void nextTick(() => trigger.value?.focus())
}

function choose(i: number) {
  if (i !== props.modelValue) emit('update:modelValue', i)
  close()
}

function onKey(e: KeyboardEvent) {
  if (open.value && e.key === 'Escape') close()
}
/** Desktop popover: a click elsewhere closes it, the focus stays where the reader clicked */
function onPointer(e: PointerEvent) {
  if (open.value && desktop.value && root.value && !root.value.contains(e.target as Node)) close(false)
}

onMounted(() => {
  document.addEventListener('keydown', onKey)
  document.addEventListener('pointerdown', onPointer)
  desktopQuery?.addEventListener('change', onQuery)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.removeEventListener('pointerdown', onPointer)
  desktopQuery?.removeEventListener('change', onQuery)
})

const VariantPickerItem = defineComponent({
  props: { option: { type: Object as PropType<VariantOption>, required: true }, selected: Boolean },
  emits: ['choose'],
  setup(p, { emit: emitItem }) {
    return () => h('button', {
      type: 'button',
      'data-variant': p.option.key,
      'aria-pressed': p.selected,
      class: ['grid min-h-14 grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 rounded-2xl border-[1.5px] px-3.5 py-2.5 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600',
        p.selected ? 'border-green-600 bg-green-50 dark:border-green-500 dark:bg-green-900/30' : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-700 dark:bg-gray-900 dark:hover:border-gray-600'],
      onClick: () => emitItem('choose'),
    }, [
      h('span', { class: 'grid' }, [
        h('b', { class: 'text-[15px] font-semibold text-gray-900 dark:text-gray-100' }, p.option.label),
        ' ',
        h('small', { class: 'text-[12.5px] text-gray-500 dark:text-gray-400' }, p.option.battery),
      ]),
      ' ',
      h('span', { class: 'grid text-right' }, [
        p.option.range ? h('b', { class: 'text-[15px] font-bold tabular-nums text-gray-900 dark:text-gray-100' }, p.option.range) : null,
        p.option.range ? ' ' : null,
        h('small', { class: 'text-[12.5px] tabular-nums text-gray-500 dark:text-gray-400' }, p.option.prospect),
      ]),
    ])
  },
})
</script>
