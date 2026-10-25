<template>
  <div ref="root" class="relative">
    <!-- Two lines, so it reads as a choice: "Variante wählen" over the battery the page is calculated with -->
    <button ref="trigger" type="button" data-testid="variant-picker"
            class="btn-3d cta-shadow-sm inline-flex max-w-full items-center rounded-2xl border-2 border-edge bg-white py-1.5 text-left text-gray-900 transition-colors hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/15"
            :class="compact ? 'min-h-11 justify-between gap-1.5 pl-3 pr-2 md:gap-3 md:pl-3.5 md:pr-3' : 'gap-3 pl-3.5 pr-3'"
            aria-haspopup="dialog" :aria-expanded="open" @click="toggle">
      <!-- compact (narrow compare columns on the phone): just the variant's name, the battery stands in the table below -->
      <span class="grid min-w-0 leading-tight">
        <span class="text-[11.5px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400" :class="compact ? 'hidden md:block' : ''">{{ t('model.page.variant_button') }}</span>
        <span class="truncate font-bold" :class="compact ? 'text-[13.5px] md:text-[14.5px]' : 'text-[14.5px]'">
          <template v-if="current"><span :class="compact ? 'md:hidden' : 'hidden'">{{ current.short ?? current.label }}</span><span :class="compact ? 'hidden md:inline' : ''">{{ current.label }}</span><span :class="compact ? 'hidden md:inline' : ''">, {{ current.battery }}</span></template>
        </span>
      </span>
      <ChevronDownIcon class="h-5 w-5 flex-none transition-transform" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
    </button>

    <!-- Desktop: a popover under the button, rising a few pixels as it fades in -->
    <Transition name="pop">
    <div v-if="open && desktop" ref="panel" role="dialog" :aria-label="t('model.page.variant_title')"
         :class="ALIGN[align]" :style="panelLeft != null ? { left: `${panelLeft}px`, right: 'auto', translate: 'none' } : undefined" class="absolute top-[calc(100%+8px)] z-40 grid w-[min(640px,calc(100vw-48px))] gap-3 rounded-2xl bg-white p-4 edge-box dark:border-gray-700 dark:bg-gray-900">
      <p class="text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ t('model.page.variant_title') }}</p>
      <div class="grid grid-cols-2 gap-2">
        <VariantPickerItem v-for="(o, i) in options" :key="o.key" :option="o" :selected="i === modelValue" @choose="choose(i)" />
      </div>
    </div>
    </Transition>

    <!-- Phone: a bottom sheet that slides up from below over a fading veil; teleported, the page
         wrapper animates with a transform -->
    <Teleport to="body">
      <Transition name="veil">
        <div v-if="open && !desktop" class="fixed inset-0 z-50 bg-gray-900/45 dark:bg-black/60" aria-hidden="true" @click="close()"></div>
      </Transition>
      <Transition name="sheet">
        <div v-if="open && !desktop" ref="panel" role="dialog" aria-modal="true" :aria-label="t('model.page.variant_title')"
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
      </Transition>
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
  /** compact trigger below md: a shorter name ("Alle"); falls back to label */
  short?: string
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
const props = withDefaults(defineProps<{
  options: VariantOption[]
  modelValue: number
  /** Which edge of the button the desktop popover lines up with; the compare page aligns by column */
  align?: 'left' | 'center' | 'right'
  /** Narrow columns below md: no "Variante wählen" line, only the name; from md as on the model page */
  compact?: boolean
}>(), { align: 'right', compact: false })
const ALIGN = { left: 'left-0', center: 'left-1/2 -translate-x-1/2', right: 'right-0' }
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

/** Desktop popover position inside the button's box once measured; null = the align class alone */
const panelLeft = ref<number | null>(null)
const EDGE = 24
/** Keeps the popover inside the window: in a narrow column (compare page) the align edge alone can push it out */
function fitPanel() {
  if (!desktop.value || !panel.value || !root.value) return
  const box = root.value.getBoundingClientRect()
  const width = panel.value.offsetWidth
  const wanted = props.align === 'left' ? 0 : props.align === 'center' ? (box.width - width) / 2 : box.width - width
  const left = Math.min(Math.max(box.left + wanted, EDGE), window.innerWidth - EDGE - width)
  panelLeft.value = left - box.left
}

async function toggle() {
  if (open.value) return close()
  panelLeft.value = null
  open.value = true
  await nextTick()
  fitPanel()
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

<style scoped>
/* Bottom sheet: up from below with a soft landing, down a little faster; the veil fades alongside */
.sheet-enter-active { transition: transform 0.28s cubic-bezier(0.2, 0.8, 0.2, 1); }
.sheet-leave-active { transition: transform 0.2s cubic-bezier(0.4, 0, 1, 1); }
.sheet-enter-from, .sheet-leave-to { transform: translateY(100%); }
.veil-enter-active, .veil-leave-active { transition: opacity 0.24s ease; }
.veil-enter-from, .veil-leave-to { opacity: 0; }
/* Popover from md up: fades in while rising a few pixels */
.pop-enter-active { transition: opacity 0.16s ease, transform 0.16s ease-out; }
.pop-leave-active { transition: opacity 0.12s ease; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(6px); }
@media (prefers-reduced-motion: reduce) {
  .sheet-enter-active, .sheet-leave-active, .veil-enter-active, .veil-leave-active, .pop-enter-active, .pop-leave-active { transition: none; }
}
</style>
