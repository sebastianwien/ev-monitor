<script setup lang="ts">
/**
 * Tile-based picker for AutoSync setup. Replaces the legacy stacked-providers
 * layout in the AutoSync accordion. Each owned car becomes a tile; clicking a
 * tile expands an inline accordion with the appropriate provider component
 * (TeslaFleetIntegration for Tesla, SmartcarIntegration for everything else).
 *
 * AutoSync-Slots: ein Abo deckt ein Fahrzeug. Sind alle Plätze belegt, werden weitere Kacheln
 * gesperrt, mit Hinweis auf ein weiteres Abo oder das Trennen eines verbundenen Autos. Die
 * Server-Prüfung in Connectors (409 AUTOSYNC_SLOTS_EXHAUSTED) ist die Sicherheitsgrenze, diese
 * UI ist die freundliche Erklärung. Bei einem Auto und einem Abo bleibt die Sperre unsichtbar.
 */
import { ref, computed, onMounted, watch, defineAsyncComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { LockClosedIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import LazyCollapse from '../shared/LazyCollapse.vue'
import { CI_BTN_PRIMARY, CI_BTN_SECONDARY, CI_LABEL, CI_NOTE } from '../../utils/ciClasses'
import type { Car } from '../../api/carService'
import smartcarService, { connectionsOf, type SmartcarConnectionStatus } from '../../api/smartcarService'
import { subscriptionService } from '../../api/subscriptionService'
import { autoSyncTileState, type AutoSyncTileState } from '../../composables/useAutoSyncTiles'
import { autoSyncProviderFor } from '../../composables/useCarAutoSyncProvider'
import { carDisplayName } from '../../utils/enumLabel'

const SmartcarIntegration = defineAsyncComponent(() => import('./SmartcarIntegration.vue'))

const { t } = useI18n()

const props = defineProps<{
    cars: Car[]
    premiumEnabled: boolean
    isPremium: boolean
    /** Premium OR privileged role (ADMIN/BETA_TESTER/TESLA_FOUNDER). Tiles render
     *  for anyone with access; pure-Premium pitch only for users without it. */
    hasAutoSyncAccess: boolean
}>()

const emit = defineEmits<{
    (e: 'active-car-label', label: string | null): void
}>()

const smartcarStatus = ref<SmartcarConnectionStatus | null>(null)
const slots = ref(1)
const statusesLoaded = ref(false)
const expandedCarId = ref<string | null>(null)

onMounted(async () => {
    if (!props.hasAutoSyncAccess) {
        // Users without AutoSync access (no Premium and no privileged role) only
        // see the upgrade pitch; skip status calls to avoid 401s in their logs.
        return
    }
    try {
        const [status, subscription] = await Promise.all([
            smartcarService.getStatus().catch(() => null),
            subscriptionService.getStatus().catch(() => null),
        ])
        smartcarStatus.value = status
        slots.value = Math.max(1, subscription?.autoSyncSlots ?? 1)
    } finally {
        statusesLoaded.value = true
        // Auto-expand: active connection takes priority, else single-car shortcut.
        if (connectedCarIds.value.length > 0) {
            expandedCarId.value = connectedCarIds.value[0]
        } else if (props.cars.length === 1) {
            expandedCarId.value = props.cars[0].id
        }
    }
})

/** Autos mit laufender Smartcar-Verbindung (eine je Fahrzeug). */
const connectedCarIds = computed<string[]>(() =>
    connectionsOf(smartcarStatus.value).map(c => c.carId).filter((id): id is string => !!id),
)

const activeCars = computed<Car[]>(() =>
    props.cars.filter(c => connectedCarIds.value.includes(c.id)),
)

/** Erstes verbundenes Auto, für die Kopfzeile "Aktiv für ...". */
const activeCar = computed<Car | null>(() => activeCars.value[0] ?? null)

const sortedCars = computed(() => {
    if (connectedCarIds.value.length === 0) return props.cars
    return [...props.cars].sort((a, b) => {
        const ai = connectedCarIds.value.includes(a.id) ? 0 : 1
        const bi = connectedCarIds.value.includes(b.id) ? 0 : 1
        return ai - bi
    })
})

function tileStateFor(car: Car): AutoSyncTileState {
    return autoSyncTileState(car, connectedCarIds.value, slots.value)
}

const activeCarsLabel = computed(() => activeCars.value.map(carLabel).join(', '))

/** Zweitkauf direkt in den Stripe-Checkout: /upgrade zeigt Abonnenten nur die Abo-Verwaltung. */
const checkoutBusy = ref(false)
const checkoutError = ref<string | null>(null)
async function buyAnotherSubscription(plan: 'monthly' | 'yearly') {
    checkoutBusy.value = true
    checkoutError.value = null
    try {
        const { checkoutUrl } = await subscriptionService.createCheckoutSession(plan, 'autosync')
        window.location.href = checkoutUrl
    } catch {
        checkoutError.value = t('upgrade.error')
        checkoutBusy.value = false
    }
}

/** Nach Verbinden oder Trennen in einer Kachel den Status neu laden, damit Sperren stimmen. */
async function refreshStatus() {
    smartcarStatus.value = await smartcarService.getStatus().catch(() => null)
}

function carLabel(car: Car): string {
    return carDisplayName(car)
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

watch(activeCar, (car) => {
    emit('active-car-label', car ? car.modelDisplayName : null)
}, { immediate: true })

function toggleExpand(carId: string) {
    // Locked + unavailable tiles still expand to show their explanation. We
    // could disable them, but expanding makes the WHY visible inline instead of
    // a tooltip, which mobile-first prefers.
    expandedCarId.value = expandedCarId.value === carId ? null : carId
}


</script>

<template>
    <div class="grid gap-4">
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
            <div v-if="props.cars.length === 0" :class="[CI_NOTE, 'grid justify-items-center gap-3 p-6 text-center']">
                <p>{{ t('imports.autosync_no_cars_hint') }}</p>
                <router-link to="/cars" :class="CI_BTN_PRIMARY">{{ t('imports.autosync_no_cars_cta') }}</router-link>
            </div>

            <!-- Eine Karte je Auto; nicht verfügbare ohne Kante, sie lassen sich nur zur Erklärung aufklappen -->
            <div v-else class="grid gap-3">
                <div v-for="car in sortedCars" :key="car.id" class="overflow-hidden rounded-2xl"
                    :class="tileStateFor(car) === 'unavailable' ? 'bg-gray-100 dark:bg-gray-800/60' : 'bg-white edge-card dark:bg-gray-700/50'">
                    <button type="button" :aria-expanded="expandedCarId === car.id"
                        class="flex min-h-16 w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-white/5"
                        @click="toggleExpand(car.id)">
                        <!-- Marke als Kürzel, aktiv dunkel -->
                        <span class="grid h-12 w-14 shrink-0 place-items-center rounded-xl text-center"
                            :class="tileStateFor(car) === 'active' ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900' : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-200'">
                            <span>
                                <span class="block text-[17px] font-extrabold leading-none tracking-tight">{{ brandInitials(car) }}</span>
                                <span class="mt-1 block text-[9px] font-bold uppercase tracking-wide opacity-75">{{ providerLabel(car) }}</span>
                            </span>
                        </span>

                        <span class="min-w-0 flex-1">
                            <span class="block truncate text-[15px] font-bold text-gray-900 dark:text-gray-100">{{ carLabel(car) }}</span>
                            <span v-if="carDetails(car)" class="mt-0.5 block truncate text-[12px] text-gray-500 dark:text-gray-400">{{ carDetails(car) }}</span>
                            <!-- Zustand: Grün nur für aktiv (Ergebnis), Amber für gesperrt (es fehlt ein Platz) -->
                            <span class="mt-1 inline-flex items-center gap-1.5 text-[12.5px] font-semibold"
                                :class="tileStateFor(car) === 'active' ? 'text-green-700 dark:text-green-400'
                                  : tileStateFor(car) === 'locked' ? 'text-amber-700 dark:text-amber-400'
                                  : 'text-gray-500 dark:text-gray-400'">
                                <LockClosedIcon v-if="tileStateFor(car) === 'locked'" class="h-3.5 w-3.5" aria-hidden="true" />
                                <span v-else class="h-2 w-2 rounded-full" aria-hidden="true"
                                    :class="tileStateFor(car) === 'active' ? 'bg-green-600 dark:bg-green-400' : 'border border-gray-400'" />
                                {{ tileStateFor(car) === 'active' ? t('imports.autosync_state_active')
                                  : tileStateFor(car) === 'locked' ? t('imports.autosync_state_locked')
                                  : tileStateFor(car) === 'unavailable' ? t('imports.autosync_state_unavailable')
                                  : t('imports.autosync_state_available') }}
                            </span>
                        </span>

                        <ChevronDownIcon class="h-5 w-5 shrink-0 text-gray-400 transition-transform duration-300 motion-reduce:transition-none"
                            :class="expandedCarId === car.id ? 'rotate-180 ease-out' : 'ease-in'" aria-hidden="true" />
                    </button>

                    <LazyCollapse :open="expandedCarId === car.id">
                        <div class="border-t border-gray-200 p-4 dark:border-white/10">
                            <!-- Locked: alle AutoSync-Plätze belegt (ein Abo je Fahrzeug) -->
                            <div v-if="tileStateFor(car) === 'locked' && activeCar" class="grid gap-3 rounded-2xl bg-amber-50 p-4 dark:bg-amber-900/20">
                                <p :class="CI_LABEL">{{ t('imports.autosync_locked_title', { count: slots }) }}</p>
                                <p class="text-sm leading-relaxed text-gray-800 dark:text-gray-100" v-html="t('imports.autosync_locked_desc', { activeCars: activeCarsLabel })" />
                                <div v-if="props.premiumEnabled" class="grid gap-2">
                                    <p class="text-[13px] font-semibold text-gray-700 dark:text-gray-300">{{ t('imports.autosync_locked_cta') }}</p>
                                    <div class="flex flex-wrap gap-2">
                                        <button v-for="plan in (['monthly', 'yearly'] as const)" :key="plan" type="button"
                                            :class="CI_BTN_SECONDARY" :disabled="checkoutBusy" @click="buyAnotherSubscription(plan)">
                                            {{ t(plan === 'monthly' ? 'upgrade.plan_monthly' : 'upgrade.plan_yearly') }}
                                        </button>
                                    </div>
                                    <p v-if="checkoutError" role="alert" class="text-[13px] font-semibold text-gray-900 dark:text-gray-100">{{ checkoutError }}</p>
                                </div>
                            </div>

                            <!-- Unavailable: brand has no AutoSync provider -->
                            <div v-else-if="tileStateFor(car) === 'unavailable'" class="grid gap-2">
                                <p class="text-sm text-gray-700 dark:text-gray-300">{{ t('imports.autosync_unavailable_desc', { brand: car.brand }) }}</p>
                                <p class="text-[13px] leading-relaxed text-gray-500 dark:text-gray-400">{{ t('imports.autosync_unavailable_alternatives') }}</p>
                            </div>

                            <!-- Active or Available: show provider component. Tesla pairs
                                 in its own free "Tesla Telemetry" tab, so the picker only
                                 ever routes Smartcar brands here. -->
                            <SmartcarIntegration
                                v-else-if="autoSyncProviderFor(car) === 'SMARTCAR'"
                                :premium-enabled="props.premiumEnabled"
                                :is-premium="props.isPremium"
                                :embedded="true"
                                :forced-car-id="car.id"
                                @disconnected="refreshStatus"
                            />
                        </div>
                    </LazyCollapse>
                </div>
            </div>

            <!-- Footer-Hinweis nur bei 2+ Autos: erst dann ist die Slot-Regel für den Nutzer relevant -->
            <p v-if="sortedCars.length >= 2" :class="[CI_NOTE, 'py-3 text-[13px]']">
                <strong class="font-semibold text-gray-900 dark:text-gray-100">{{ t('imports.autosync_footer_label') }} ·</strong>
                {{ t('imports.autosync_footer_hint') }}
            </p>
        </template>
    </div>
</template>
