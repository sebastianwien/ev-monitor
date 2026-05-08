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
import { LockClosedIcon, CheckCircleIcon, ChevronDownIcon, TruckIcon } from '@heroicons/vue/24/outline'
import type { Car } from '../../api/carService'
import smartcarService, { type SmartcarConnectionStatus } from '../../api/smartcarService'
import teslaFleetService, { type TeslaConnectionStatus } from '../../api/teslaFleetService'
import { autoSyncProviderFor, type AutoSyncProvider } from '../../composables/useCarAutoSyncProvider'
import { enumToLabel, carDisplayName } from '../../utils/enumLabel'
import LicensePlate from '../car/LicensePlate.vue'

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
const failedImageIds = ref<Set<string>>(new Set())

function onImageError(carId: string) {
    const next = new Set(failedImageIds.value)
    next.add(carId)
    failedImageIds.value = next
}

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
    if (car.year) return String(car.year)
    return ''
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
            <div v-if="props.cars.length === 0" class="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 p-6 text-center">
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ t('imports.autosync_no_cars_hint') }}</p>
                <router-link
                    to="/cars"
                    class="inline-block mt-2 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
                >
                    {{ t('imports.autosync_no_cars_cta') }}
                </router-link>
            </div>

            <!-- Tiles -->
            <div v-else class="space-y-3">
                <div
                    v-for="car in sortedCars"
                    :key="car.id"
                    class="relative rounded-xl bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700/60 overflow-hidden transition-colors"
                    :class="{
                        'opacity-60': tileStateFor(car) === 'locked' || tileStateFor(car) === 'unavailable',
                    }"
                >
                    <!-- Left accent bar -->
                    <span
                        class="absolute left-0 top-0 bottom-0 w-1.5 z-10"
                        :class="{
                            'bg-emerald-500': tileStateFor(car) === 'active',
                            'bg-amber-500/60': tileStateFor(car) === 'locked',
                            'bg-slate-400 dark:bg-slate-600': tileStateFor(car) === 'available' || tileStateFor(car) === 'unavailable',
                        }"
                    ></span>

                    <!-- Tile header -->
                    <button
                        type="button"
                        class="w-full flex items-stretch text-left hover:bg-gray-50 dark:hover:bg-white/5 transition"
                        @click="toggleExpand(car.id)"
                    >
                        <!-- Car image / car-icon fallback -->
                        <div class="shrink-0 w-16 md:w-20 bg-gradient-to-br from-gray-100 to-gray-200 dark:from-slate-700 dark:to-slate-800 flex items-center justify-center overflow-hidden">
                            <img
                                v-if="car.imageUrl && !failedImageIds.has(car.id)"
                                :src="car.imageUrl"
                                alt=""
                                class="w-full h-full object-cover"
                                @error="onImageError(car.id)"
                            />
                            <TruckIcon v-else class="h-7 w-7 text-gray-400 dark:text-slate-500" />
                        </div>

                        <!-- Name + details -->
                        <div class="flex-1 flex items-center gap-3 px-4 py-3.5 min-w-0">
                            <div class="flex-1 min-w-0">
                                <div class="flex items-center gap-2 flex-wrap">
                                    <span class="font-medium text-sm text-gray-900 dark:text-white">
                                        {{ carLabel(car) }}
                                    </span>
                                    <span v-if="car.trim" class="hidden md:inline text-xs text-slate-500 dark:text-slate-400">{{ car.trim }}</span>
                                </div>
                                <div class="flex items-center gap-2 mt-0.5">
                                    <p v-if="carDetails(car)" class="text-xs text-slate-500 dark:text-slate-400">
                                        {{ carDetails(car) }}
                                    </p>
                                    <div v-if="car.licensePlate" class="hidden md:block">
                                        <LicensePlate :plate="car.licensePlate" />
                                    </div>
                                </div>
                            </div>

                            <!-- State badge -->
                            <span
                                v-if="tileStateFor(car) === 'active'"
                                class="shrink-0 inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full bg-emerald-500/20 text-emerald-400 uppercase tracking-wide"
                            >
                                <CheckCircleIcon class="h-3 w-3" />
                                {{ t('imports.autosync_state_active') }}
                            </span>
                            <span
                                v-else-if="tileStateFor(car) === 'locked'"
                                class="shrink-0 inline-flex items-center gap-1 text-[10px] font-semibold px-2.5 py-1 rounded-full bg-amber-500/20 text-amber-400 uppercase tracking-wide"
                            >
                                <LockClosedIcon class="h-3 w-3" />
                                {{ t('imports.autosync_state_locked') }}
                            </span>
                            <span
                                v-else-if="tileStateFor(car) === 'unavailable'"
                                class="shrink-0 text-[10px] font-semibold px-2.5 py-1 rounded-full bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-400 uppercase tracking-wide"
                            >
                                {{ t('imports.autosync_state_unavailable') }}
                            </span>

                            <ChevronDownIcon
                                class="h-4 w-4 text-slate-400 shrink-0 transition-transform duration-200"
                                :class="expandedCarId === car.id ? 'rotate-180' : ''"
                            />
                        </div>
                    </button>

                    <!-- Expanded body -->
                    <Transition name="accordion">
                        <div v-if="expandedCarId === car.id" class="border-t border-slate-100 dark:border-slate-700/40">
                            <!-- Locked: another car holds the connection -->
                            <div v-if="tileStateFor(car) === 'locked' && activeCar" class="p-4 md:p-5">
                                <div class="flex items-start gap-3">
                                    <LockClosedIcon class="h-5 w-5 text-amber-500 dark:text-amber-400 mt-0.5 shrink-0" />
                                    <div class="text-sm space-y-1">
                                        <p class="font-medium text-gray-900 dark:text-white">
                                            {{ t('imports.autosync_locked_title') }}
                                        </p>
                                        <p
                                            class="text-gray-700 dark:text-slate-300"
                                            v-html="t('imports.autosync_locked_desc', { activeCar: carLabel(activeCar) })"
                                        />
                                    </div>
                                </div>
                            </div>

                            <!-- Unavailable: brand has no AutoSync provider -->
                            <div v-else-if="tileStateFor(car) === 'unavailable'" class="p-4 md:p-5">
                                <p class="text-sm text-gray-700 dark:text-gray-300">
                                    {{ t('imports.autosync_unavailable_desc', { brand: car.brand }) }}
                                </p>
                                <p class="text-xs text-slate-500 dark:text-slate-400 mt-2">
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

                            <!-- Inline Live upsell on the active Tesla tile only.
                                 Hidden once user is on Live or not paying yet. -->
                            <a
                                v-if="autoSyncProviderFor(car) === 'TESLA'
                                      && tileStateFor(car) === 'active'
                                      && props.tier === 'AUTOSYNC'"
                                href="#"
                                @click.prevent="emit('live-upgrade-requested')"
                                class="block px-4 py-2.5 bg-indigo-900/30 border-t border-indigo-700/40 text-xs text-indigo-700 dark:text-indigo-300 hover:bg-indigo-900/50 transition-colors"
                            >
                                <span class="font-medium">{{ t('imports.tile_live_upsell') }}</span>
                                <span class="text-indigo-500 dark:text-indigo-400 float-right">{{ t('imports.tile_live_upsell_price') }} →</span>
                            </a>
                        </div>
                    </Transition>
                </div>
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
