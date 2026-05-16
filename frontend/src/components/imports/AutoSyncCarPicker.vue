<script setup lang="ts">
/**
 * Tile-based picker for AutoSync setup. Replaces the legacy stacked-providers
 * layout in the AutoSync accordion. Each owned car becomes a tile; clicking a
 * tile expands an inline accordion with the appropriate provider component
 * (TeslaFleetIntegration for Tesla, SmartcarIntegration for everything else).
 *
 * Premium-=-1-active-connection limit is surfaced UI-side: when a connection
 * is active for car A, tiles for other cars are locked with a clear hint that
 * the user must disconnect car A first. Backend soft-enforcement is the
 * security boundary; this UI is the visible-exit and the friendly explanation.
 */
import { ref, computed, onMounted, watch, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'
import smartcarService, { type SmartcarConnectionStatus } from '../../api/smartcarService'
import teslaFleetService, { type TeslaConnectionStatus } from '../../api/teslaFleetService'
import { autoSyncProviderFor, type AutoSyncProvider } from '../../composables/useCarAutoSyncProvider'
import { enumToLabel, carDisplayName } from '../../utils/enumLabel'

const TeslaFleetIntegration = defineAsyncComponent(() => import('./TeslaFleetIntegration.vue'))
const SmartcarIntegration = defineAsyncComponent(() => import('./SmartcarIntegration.vue'))

const { t } = useI18n()

const props = defineProps<{
    cars: Car[]
    premiumEnabled: boolean
    isPremium: boolean
    /** Premium OR privileged role (ADMIN/BETA_TESTER/TESLA_FOUNDER). Tiles render
     *  for anyone with access; pure-Premium pitch only for users without it. */
    hasAutoSyncAccess: boolean
    /** Subscription tier - drives the Live-upgrade tile-banner under the Tesla tile.
     *  Banner shows only when tier === 'AUTOSYNC' (i.e. paying user not yet on Live). */
    tier?: 'NONE' | 'AUTOSYNC' | 'AUTOSYNC_LIVE'
}>()

const emit = defineEmits<{
    (e: 'active-car-label', label: string | null): void
    (e: 'live-upgrade-requested'): void
}>()

const teslaStatus = ref<TeslaConnectionStatus | null>(null)
const smartcarStatus = ref<SmartcarConnectionStatus | null>(null)
const statusesLoaded = ref(false)
const expandedCarId = ref<string | null>(null)

onMounted(async () => {
    if (!props.hasAutoSyncAccess) {
        // Users without AutoSync access (no Premium and no privileged role) only
        // see the upgrade pitch; skip status calls to avoid 401s in their logs.
        return
    }
    try {
        const [tesla, smartcar] = await Promise.all([
            teslaFleetService.getStatus().catch(() => null),
            smartcarService.getStatus().catch(() => null),
        ])
        teslaStatus.value = tesla
        smartcarStatus.value = smartcar
    } finally {
        statusesLoaded.value = true
        // Auto-expand: active connection takes priority, else single-car shortcut.
        if (activeConnection.value) {
            expandedCarId.value = activeConnection.value.carId
        } else if (props.cars.length === 1) {
            expandedCarId.value = props.cars[0].id
        }
    }
})

interface ActiveConnection {
    provider: AutoSyncProvider
    carId: string
}

const activeConnection = computed<ActiveConnection | null>(() => {
    if (teslaStatus.value?.connected && teslaStatus.value.carId) {
        return { provider: 'TESLA', carId: teslaStatus.value.carId }
    }
    if (smartcarStatus.value?.connected && smartcarStatus.value.carId) {
        return { provider: 'SMARTCAR', carId: smartcarStatus.value.carId }
    }
    return null
})

const activeCar = computed<Car | null>(() => {
    if (!activeConnection.value) return null
    return props.cars.find(c => c.id === activeConnection.value!.carId) ?? null
})

const sortedCars = computed(() => {
    if (!activeConnection.value) return props.cars
    return [...props.cars].sort((a, b) => {
        if (a.id === activeConnection.value!.carId) return -1
        if (b.id === activeConnection.value!.carId) return 1
        return 0
    })
})

type TileState = 'available' | 'active' | 'locked' | 'unavailable'

function tileStateFor(car: Car): TileState {
    if (autoSyncProviderFor(car) === 'NONE') return 'unavailable'
    if (activeConnection.value?.carId === car.id) return 'active'
    if (activeConnection.value) return 'locked'
    return 'available'
}

function carLabel(car: Car): string {
    return carDisplayName(car.brand, car.model)
}

function carDetails(car: Car): string {
    const parts: string[] = []
    if (car.year) parts.push(String(car.year))
    if (car.licensePlate) parts.push(car.licensePlate)
    return parts.join(' · ')
}

/** Provider-Label fuer den Brand-Tag links. */
function providerLabel(car: Car): string {
    const p = autoSyncProviderFor(car)
    if (p === 'TESLA') return 'Tesla'
    if (p === 'SMARTCAR') return 'Smartcar'
    return t('imports.autosync_state_unavailable')
}

/** Brand-Initialen fuer den Tag-Block, max 2 Zeichen. */
function brandInitials(car: Car): string {
    const raw = (car.brand || '?').replace(/_/g, ' ').trim()
    // Multi-word brands wie "ALFA_ROMEO" -> "AR", single-word -> erster Buchstabe
    const words = raw.split(/\s+/).filter(Boolean)
    if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase()
    return raw.slice(0, words[0].length <= 3 ? words[0].length : 2).toUpperCase()
}

/** Hintergrundfarbe des Brand-Tags basierend auf TileState - mit den
 *  bestehenden State-Farben (emerald aktiv, neutral verfuegbar etc.). */
function brandTagClasses(car: Car): string {
    const state = tileStateFor(car)
    if (state === 'active') return 'bg-gray-950 dark:bg-white text-white dark:text-gray-950'
    if (state === 'unavailable') return 'bg-yellow-400 text-gray-950'
    if (state === 'locked') return 'bg-gray-300 dark:bg-gray-700 text-gray-700 dark:text-gray-300'
    return 'bg-indigo-600 text-white'
}

watch(activeCar, (car) => {
    emit('active-car-label', car ? enumToLabel(car.model) : null)
}, { immediate: true })

function toggleExpand(carId: string) {
    // Locked + unavailable tiles still expand to show their explanation. We
    // could disable them, but expanding makes the WHY visible inline instead of
    // a tooltip, which mobile-first prefers.
    expandedCarId.value = expandedCarId.value === carId ? null : carId
}


</script>

<template>
    <div class="space-y-4">
        <!-- Non-premium: render the existing Smartcar teaser/pitch (brand list,
             FAQ, upgrade CTA). Tiles only make sense for Premium users with an
             actual active subscription. -->
        <SmartcarIntegration
            v-if="!props.hasAutoSyncAccess"
            :premium-enabled="props.premiumEnabled"
            :is-premium="false"
        />

        <template v-else>
            <!-- Empty state: no cars -->
            <div v-if="props.cars.length === 0"
                 class="border-2 border-dashed border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 rounded-sm p-6 text-center">
                <p class="text-sm text-gray-600 dark:text-gray-400 font-medium">{{ t('imports.autosync_no_cars_hint') }}</p>
                <router-link
                    to="/cars"
                    class="inline-block mt-3 bg-amber-500 hover:bg-amber-400 text-gray-950 font-bold uppercase tracking-wider text-[11px] px-4 py-2 rounded-sm border-2 border-amber-500 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75"
                >
                    {{ t('imports.autosync_no_cars_cta') }}
                </router-link>
            </div>

            <!-- Stacked Rows -->
            <div v-else class="space-y-4">
                <div
                    v-for="car in sortedCars"
                    :key="car.id"
                    class="rounded-sm overflow-hidden transition-[box-shadow] duration-150"
                    :class="tileStateFor(car) === 'active'
                        ? 'border-2 border-gray-900 dark:border-white bg-white dark:bg-gray-900 shadow-[2px_2px_0_0_#0a0a0a] dark:shadow-[2px_2px_0_0_#ffffff]'
                        : tileStateFor(car) === 'unavailable'
                            ? 'border-2 border-dashed border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 opacity-75'
                            : 'border-2 border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-[2px_2px_0_0_#d1d5db] dark:shadow-[2px_2px_0_0_#374151]'"
                >
                    <!-- Row header -->
                    <button
                        type="button"
                        class="w-full flex items-stretch text-left hover:bg-gray-50 dark:hover:bg-white/5 transition-colors"
                        @click="toggleExpand(car.id)"
                    >
                        <!-- Brand-Tag (links) -->
                        <div
                            class="px-3 py-4 flex flex-col justify-center items-center min-w-[80px] md:min-w-[88px] shrink-0 border-r-2"
                            :class="[
                                brandTagClasses(car),
                                tileStateFor(car) === 'active' ? 'border-gray-900 dark:border-white'
                                  : tileStateFor(car) === 'unavailable' ? 'border-dashed border-gray-300 dark:border-gray-700'
                                  : 'border-gray-300 dark:border-gray-700'
                            ]"
                        >
                            <p class="text-xl md:text-2xl font-extrabold tracking-tight">{{ brandInitials(car) }}</p>
                            <p class="text-[9px] font-bold uppercase tracking-wider mt-0.5 opacity-75">{{ providerLabel(car) }}</p>
                        </div>

                        <!-- Content -->
                        <div class="flex-1 flex items-center gap-3 px-4 py-3.5 min-w-0">
                            <div class="flex-1 min-w-0">
                                <h3 class="text-base font-bold text-gray-900 dark:text-white truncate">{{ carLabel(car) }}</h3>
                                <p v-if="carDetails(car)" class="text-xs text-gray-500 dark:text-gray-400 font-mono mt-0.5 truncate">{{ carDetails(car) }}</p>
                            </div>

                            <!-- State badge -->
                            <span
                                v-if="tileStateFor(car) === 'active'"
                                class="shrink-0 text-[10px] font-bold uppercase tracking-wider bg-green-600 text-white px-2 py-1 rounded-sm whitespace-nowrap"
                            >● {{ t('imports.autosync_state_active') }}</span>
                            <span
                                v-else-if="tileStateFor(car) === 'locked'"
                                class="shrink-0 inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-amber-500 text-gray-950 px-2 py-1 rounded-sm whitespace-nowrap"
                            >
                                <LockClosedIcon class="h-3 w-3" />
                                {{ t('imports.autosync_state_locked') }}
                            </span>
                            <span
                                v-else-if="tileStateFor(car) === 'unavailable'"
                                class="shrink-0 text-[10px] font-bold uppercase tracking-wider bg-gray-300 dark:bg-gray-700 text-gray-600 dark:text-gray-400 px-2 py-1 rounded-sm whitespace-nowrap"
                            >— {{ t('imports.autosync_state_unavailable') }}</span>
                            <span
                                v-else
                                class="shrink-0 text-[10px] font-bold uppercase tracking-wider bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 px-2 py-1 rounded-sm whitespace-nowrap"
                            >○ {{ t('imports.autosync_state_available') }}</span>

                            <ChevronDownIcon
                                class="h-4 w-4 text-gray-500 dark:text-gray-400 shrink-0 transition-transform duration-200"
                                :class="expandedCarId === car.id ? 'rotate-180' : ''"
                            />
                        </div>
                    </button>

                    <!-- Expanded body -->
                    <Transition name="accordion">
                        <div v-if="expandedCarId === car.id" class="border-t-2 border-gray-200 dark:border-gray-700">
                            <!-- Locked: another car holds the connection -->
                            <div v-if="tileStateFor(car) === 'locked' && activeCar" class="p-4 md:p-5">
                                <div class="border-l-2 border-amber-500 bg-amber-50 dark:bg-amber-950/30 px-4 py-3 rounded-r-sm">
                                    <p class="text-[11px] font-bold uppercase tracking-wider text-amber-700 dark:text-amber-400 mb-1">
                                        {{ t('imports.autosync_locked_title') }}
                                    </p>
                                    <p
                                        class="text-sm text-gray-700 dark:text-gray-200 font-medium leading-relaxed"
                                        v-html="t('imports.autosync_locked_desc', { activeCar: carLabel(activeCar) })"
                                    />
                                </div>
                            </div>

                            <!-- Unavailable: brand has no AutoSync provider -->
                            <div v-else-if="tileStateFor(car) === 'unavailable'" class="p-4 md:p-5">
                                <p class="text-sm text-gray-700 dark:text-gray-300 font-medium">
                                    {{ t('imports.autosync_unavailable_desc', { brand: car.brand }) }}
                                </p>
                                <p class="text-xs text-gray-500 dark:text-gray-400 mt-2 leading-relaxed">
                                    {{ t('imports.autosync_unavailable_alternatives') }}
                                </p>
                            </div>

                            <!-- Active or Available: show provider component -->
                            <div v-else class="p-4 md:p-5">
                                <TeslaFleetIntegration
                                    v-if="autoSyncProviderFor(car) === 'TESLA'"
                                    :embedded="true"
                                    :forced-car-id="car.id"
                                />
                                <SmartcarIntegration
                                    v-else-if="autoSyncProviderFor(car) === 'SMARTCAR'"
                                    :premium-enabled="props.premiumEnabled"
                                    :is-premium="props.isPremium"
                                    :embedded="true"
                                    :forced-car-id="car.id"
                                />
                            </div>
                        </div>
                    </Transition>

                    <!-- Inline Live upsell strip on the active Tesla row only.
                         Hidden once user is on Live or not paying yet. -->
                    <div
                        v-if="autoSyncProviderFor(car) === 'TESLA'
                              && tileStateFor(car) === 'active'
                              && props.tier === 'AUTOSYNC'"
                        class="border-t-2 border-gray-900 dark:border-white bg-indigo-50 dark:bg-indigo-950/40 px-4 md:px-5 py-3 flex items-center justify-between gap-3 flex-wrap"
                    >
                        <div class="flex items-center gap-3 flex-1 min-w-0">
                            <span class="inline-flex w-7 h-7 bg-indigo-600 text-white rounded-sm items-center justify-center text-sm font-extrabold shrink-0">+</span>
                            <div class="flex-1 min-w-0">
                                <p class="text-[11px] font-bold uppercase tracking-[0.14em] text-indigo-700 dark:text-indigo-400">{{ t('imports.tile_live_upsell') }}</p>
                                <p class="text-xs text-gray-700 dark:text-gray-300 font-medium font-mono">{{ t('imports.tile_live_upsell_price') }}</p>
                            </div>
                        </div>
                        <button
                            type="button"
                            @click="emit('live-upgrade-requested')"
                            class="bg-indigo-600 hover:bg-indigo-700 text-white font-bold uppercase tracking-wider text-[11px] px-3 py-2 rounded-sm border-2 border-indigo-600 shadow-[2px_2px_0_0_#030712] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none transition-[transform,box-shadow] duration-75 whitespace-nowrap"
                        >
                            {{ t('imports.tile_live_upsell_cta') }} →
                        </button>
                    </div>
                </div>
            </div>

            <!-- Footer-Hint (nur wenn 2+ Autos, da 1-Auto-Constraint relevant) -->
            <div v-if="sortedCars.length >= 2" class="border-l-2 border-gray-300 dark:border-gray-600 bg-gray-100 dark:bg-gray-800/40 px-4 py-3 rounded-r-sm">
                <p class="text-xs text-gray-600 dark:text-gray-400 leading-relaxed">
                    <strong class="font-bold uppercase tracking-wider text-[11px]">{{ t('imports.autosync_footer_label') }} ·</strong>
                    {{ t('imports.autosync_footer_hint') }}
                </p>
            </div>
        </template>
    </div>
</template>

<style scoped>
.accordion-enter-active,
.accordion-leave-active {
    transition: opacity 0.18s ease, max-height 0.22s ease;
    max-height: 1200px;
    overflow: hidden;
}
.accordion-enter-from,
.accordion-leave-to {
    opacity: 0;
    max-height: 0;
}
</style>
