<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon, ChevronDownIcon, TruckIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'
import LicensePlate from './LicensePlate.vue'
import AnchoredPanel from '../shared/AnchoredPanel.vue'
import { panelAnchor, type PanelAnchor } from '../../utils/panelAnchor'
import { carDisplayName } from '../../utils/enumLabel'

/**
 * Kopfzeile einer Box im neuen Design (Übersicht, Logs, Fahrzeuge): welches Auto, und bei mehreren Autos
 * der Wechsel. Eine Zeile statt einer Auto-Kachel; die Daten des Autos stehen auf /cars.
 * Bei mehreren Autos klappt der Name die Liste an Ort und Stelle auf (AnchoredPanel): das Panel legt sich
 * deckungsgleich über die Kopfzeile und öffnet nach unten, der Rest der Seite dunkelt ab.
 */
const props = withDefaults(defineProps<{
  cars: Pick<Car, 'id' | 'displayName' | 'model' | 'trim' | 'licensePlate'>[]
  /** Gewähltes Auto (v-model). */
  modelValue: string | null
  carImageUrls: Record<string, string>
  /** Lädt das gewählte Auto gerade? */
  charging?: boolean
}>(), { charging: false })
const emit = defineEmits<{ 'update:modelValue': [carId: string] }>()
const { t } = useI18n()
const listId = useId()

const selected = computed(() => props.cars.find(c => c.id === props.modelValue) ?? props.cars[0] ?? null)
const switchable = computed(() => props.cars.length > 1)
const open = ref(false)
const trigger = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)
const anchor = ref<PanelAnchor>({ top: 0, left: 0, width: 0, shift: 0 })

function options(): HTMLButtonElement[] {
  return [...(list.value?.querySelectorAll<HTMLButtonElement>('[role="option"]') ?? [])]
}

function show() {
  anchor.value = panelAnchor(trigger.value!.getBoundingClientRect(), window.innerWidth, { width: 448, align: 'left' })
  open.value = true
  nextTick(() => options().find(o => o.getAttribute('aria-selected') === 'true')?.focus({ preventScroll: true }))
}

function hide(returnFocus = true) {
  if (!open.value) return
  open.value = false
  if (returnFocus) trigger.value?.focus()
}

function choose(carId: string) {
  if (carId !== props.modelValue) emit('update:modelValue', carId)
  hide()
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Tab') { hide(false); return }
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp' && e.key !== 'Home' && e.key !== 'End') return
  e.preventDefault()
  const all = options()
  const at = all.indexOf(document.activeElement as HTMLButtonElement)
  const next = e.key === 'Home' ? 0
    : e.key === 'End' ? all.length - 1
      : (at + (e.key === 'ArrowDown' ? 1 : -1) + all.length) % all.length
  all[next]?.focus({ preventScroll: true })
}

</script>

<template>
  <div v-if="selected" class="flex min-w-0 items-center gap-2">
    <component :is="switchable ? 'button' : 'div'" ref="trigger"
      :type="switchable ? 'button' : undefined"
      :data-testid="switchable ? 'car-switch' : undefined"
      :aria-expanded="switchable ? open : undefined"
      :aria-haspopup="switchable ? 'listbox' : undefined"
      :aria-controls="switchable ? listId : undefined"
      class="flex min-w-0 items-center gap-1.5 text-left"
      :class="switchable ? '-mx-2 min-h-11 rounded-xl px-2 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-green-600 dark:hover:bg-white/10' : ''"
      @click="switchable && (open ? hide() : show())">
      <span class="truncate text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ carDisplayName(selected) }}</span>
      <span v-if="selected.trim" class="hidden shrink-0 text-[13px] text-gray-500 sm:inline dark:text-gray-400">{{ selected.trim }}</span>
      <ChevronDownIcon v-if="switchable" class="h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" aria-hidden="true" />
    </component>
    <span v-if="selected.licensePlate" class="hidden shrink-0 sm:inline-flex"><LicensePlate :plate="selected.licensePlate" size="sm" /></span>
    <span v-if="charging" data-testid="car-switch-charging" class="flex shrink-0 items-center gap-1.5 text-[13px] text-story-accent dark:text-story-accent-dark">
      <span class="h-1.5 w-1.5 rounded-full bg-current motion-safe:animate-pulse" aria-hidden="true" />{{ t('story.live_charging') }}
    </span>

    <AnchoredPanel v-if="switchable" :open="open" :anchor="anchor" testid="car-switch" @close="hide">
      <template #default="{ shiftStyle }">
        <div @keydown="onKey">
          <!-- Die Kopfzeile wie der Auslöser darunter, der Pfeil zeigt nach oben: ein Tipp schließt -->
          <button type="button" tabindex="-1" class="flex min-h-11 w-full items-center gap-1.5 rounded-xl px-2 text-left"
            :style="shiftStyle" @click="hide()">
            <span class="truncate text-[15px] font-bold text-gray-900 dark:text-gray-50">{{ carDisplayName(selected) }}</span>
            <span v-if="selected.trim" class="hidden shrink-0 text-[13px] text-gray-500 sm:inline dark:text-gray-400">{{ selected.trim }}</span>
            <ChevronDownIcon class="h-4 w-4 shrink-0 rotate-180 text-gray-500 dark:text-gray-400" aria-hidden="true" />
          </button>
          <ul :id="listId" ref="list" role="listbox" :aria-label="t('cars.switcher_label')"
            class="mt-1 max-h-[60vh] overflow-y-auto border-t border-gray-200 pt-1 dark:border-gray-700">
            <li v-for="car in cars" :key="car.id" role="none">
              <button type="button" role="option" data-testid="car-switch-option"
                :aria-selected="car.id === selected.id ? 'true' : 'false'"
                class="flex min-h-14 w-full items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-gray-50 focus:outline-none focus-visible:bg-gray-100 dark:hover:bg-white/5 dark:focus-visible:bg-white/10"
                @click="choose(car.id)">
                <span class="grid h-10 w-14 shrink-0 place-items-center overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
                  <img v-if="carImageUrls[car.id]" :src="carImageUrls[car.id]" alt="" class="h-full w-full object-cover" />
                  <TruckIcon v-else class="h-5 w-5 text-gray-400" aria-hidden="true" />
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-[15px] font-semibold text-gray-900 dark:text-gray-100">{{ carDisplayName(car) }}</span>
                  <span v-if="car.trim" class="block truncate text-[13px] text-gray-500 dark:text-gray-400">{{ car.trim }}</span>
                </span>
                <LicensePlate v-if="car.licensePlate" :plate="car.licensePlate" size="sm" class="shrink-0" />
                <CheckIcon class="h-5 w-5 shrink-0 text-story-accent dark:text-story-accent-dark"
                  :class="car.id === selected.id ? '' : 'invisible'" aria-hidden="true" />
              </button>
            </li>
          </ul>
        </div>
      </template>
    </AnchoredPanel>
  </div>
</template>
