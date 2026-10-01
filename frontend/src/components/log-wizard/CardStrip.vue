<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircleIcon, PlusIcon, ClockIcon, GiftIcon } from '@heroicons/vue/24/outline'
import type { ChargingProvider } from '../../composables/useChargingProviders'
import { providerPriceForType } from '../../utils/chargingProviderPricing'
import { cardContainerStyle } from '../../composables/useChargingCardDesign'
import ChargingCardTile from '../shared/ChargingCardTile.vue'

/** Der letzte Preis an diesem Ort, mit der Karte von damals - ohne Karte nur der Preis. */
export interface CommunityPrice { eurPerKwh: number; providerId: string | null }
export type CardChoice =
  | { kind: 'provider'; provider: ChargingProvider; eurPerKwh: number | null }
  | { kind: 'community'; price: CommunityPrice }
  | { kind: 'free' }
  | { kind: 'new' }

/**
 * Ladekarten als wischbarer Streifen: "Zuletzt hier", die Karten im Kreditkarten-Look
 * (Karten ohne Tarif mit Punkt), Gratis, neue Karte. Die gewählte trägt Ring und Haken.
 * Steht in Schritt 1 unter der gewählten Säule - Säule und Karte sind eine Entscheidung.
 */
const props = defineProps<{
  providers: ChargingProvider[]
  isPublic: boolean
  chargingType: 'AC' | 'DC'
  community: CommunityPrice | null
  /** Provider-ID, 'community' oder 'free' */
  selected: string | null
  priceLabel: (eur: number) => string
}>()
const emit = defineEmits<{ choose: [choice: CardChoice] }>()
const { t } = useI18n()

/** Öffentlich die Ladekarten, zuhause nur der Heimtarif - auch Karten ohne Tarif, die holen ihn sich beim Tap. */
const strip = computed(() => props.providers.filter(p => props.isPublic ? !p.isPrivate : p.isPrivate))
/** "Zuletzt hier" nur, wenn die Karte von damals nicht selbst im Streifen steht - sonst ist sie die Vorauswahl. */
const communityTile = computed(() => props.community && !strip.value.some(p => p.id === props.community!.providerId) ? props.community : null)
const price = (p: ChargingProvider) => providerPriceForType(p, props.chargingType)
const TILE = 'btn-3d snap-start relative flex-shrink-0 h-[4.5rem] rounded-sm'
const PLAIN = 'p-2.5 text-left flex flex-col justify-between bg-gray-100 dark:bg-gray-700'
const on = (key: string) => props.selected === key ? 'active ring-2 ring-inset ring-indigo-500' : ''
</script>

<template>
  <div data-testid="card-strip" @click.stop
    class="flex gap-2.5 overflow-x-auto snap-x snap-mandatory -mx-4 px-4 scroll-pl-4 pt-1 pb-2 md:mx-0 md:px-0 md:scroll-pl-0 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
    <button v-if="communityTile" type="button" :aria-pressed="selected === 'community'" @click="emit('choose', { kind: 'community', price: communityTile })"
      :class="[TILE, PLAIN, 'w-28', on('community')]">
      <ClockIcon class="h-4 w-4 text-gray-500 dark:text-gray-300" />
      <span><b class="block text-[11px] font-bold leading-tight text-gray-800 dark:text-gray-100">{{ t('logwizard.price_community') }}</b>
        <span class="block text-[10px] leading-tight text-gray-500 dark:text-gray-400 tabular-nums">{{ priceLabel(communityTile.eurPerKwh) }}</span></span>
      <CheckCircleIcon v-if="selected === 'community'" class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
    </button>
    <button v-for="p in strip" :key="p.id" type="button" :aria-pressed="selected === p.id" :data-testid="`card-${p.id}`"
      @click="emit('choose', { kind: 'provider', provider: p, eurPerKwh: price(p) })"
      :class="[TILE, 'w-28', on(p.id)]" :style="{ '--btn-shadow-color': cardContainerStyle(p.id)['--btn-shadow-color'] }">
      <ChargingCardTile class="w-full h-full" :id="p.id" :title="p.label || p.providerName"
        :subtitle="price(p) != null ? priceLabel(price(p)!) : t('logfields.card_no_price_dot')" />
      <CheckCircleIcon v-if="selected === p.id" class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
      <span v-else-if="price(p) == null" class="absolute top-1 right-1 h-2.5 w-2.5 rounded-full bg-amber-400 ring-2 ring-white dark:ring-gray-800" aria-hidden="true" />
    </button>
    <button type="button" :aria-pressed="selected === 'free'" @click="emit('choose', { kind: 'free' })" :class="[TILE, PLAIN, 'w-20', on('free')]">
      <GiftIcon class="h-4 w-4 text-gray-500 dark:text-gray-300" />
      <b class="block text-[11px] font-bold leading-tight text-gray-800 dark:text-gray-100">{{ t('logwizard.price_free') }}</b>
      <CheckCircleIcon v-if="selected === 'free'" class="absolute top-1 right-1 h-5 w-5 rounded-full bg-white text-indigo-600 dark:bg-gray-800" aria-hidden="true" />
    </button>
    <button v-if="isPublic" type="button" data-testid="charging-card-prompt-open" @click="emit('choose', { kind: 'new' })"
      class="snap-start flex-shrink-0 w-20 h-[4.5rem] rounded-sm border-2 border-dashed border-gray-300 dark:border-gray-600 p-2 flex flex-col items-start justify-between text-left text-gray-500 dark:text-gray-400 hover:border-indigo-400 hover:text-indigo-600">
      <PlusIcon class="h-4 w-4" />
      <span class="text-[11px] font-semibold leading-tight">{{ t('logwizard.card_add') }}</span>
    </button>
  </div>
</template>
