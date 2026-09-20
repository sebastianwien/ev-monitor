<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import api from '../../api/axios'
import { analytics } from '../../services/analytics'
import { useAuthStore } from '../../stores/auth'
import { useCountryStore } from '../../stores/country'
import { onboardingSeenKey, useOnboardingState } from '../../composables/useOnboardingState'
import { useCarQuickAdd } from '../../composables/useCarQuickAdd'
import { useInlineChargingCard } from '../../composables/useInlineChargingCard'
import { usePwaInstall } from '../../composables/usePwaInstall'
import { useIsMobile } from '../../composables/useIsMobile'
import { useVisualViewportBox } from '../../composables/useVisualViewportBox'
import StepWelcome from './steps/StepWelcome.vue'
import StepCar from './steps/StepCar.vue'
import StepChargingCard from './steps/StepChargingCard.vue'
import StepInstall from './steps/StepInstall.vue'

/**
 * Einfuehrung fuer neu registrierte User. Vier Schritte mit einem Ziel: am Ende steht ein
 * angelegtes Fahrzeug, optional eine Ladekarte und die Seite auf dem Homescreen.
 *
 * Import und AutoSync bewusst nicht hier - beides steht im DashboardEmptyState, auf dem
 * der User unmittelbar nach dem Abschluss landet.
 */
type StepKey = 'welcome' | 'car' | 'card' | 'install'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const countryStore = useCountryStore()
const { isOnboardingVisible } = useOnboardingState()

const quickAdd = useCarQuickAdd()
const { isStandalone, platform, canPrompt, promptInstall } = usePwaInstall()

// Preise werden in der Subeinheit getippt (ct, p) und in der Hauptwaehrung gespeichert.
const subunitDivisor = computed(() => countryStore.unitSystem.currencySubunitDivisor)
const priceUnit = computed(() =>
  countryStore.unitSystem.currencySubunit ?? countryStore.unitSystem.currencySymbol)
const card = useInlineChargingCard(
  (typed: number) => typed / subunitDivisor.value,
  (eur: number) => Math.round(eur * subunitDivisor.value * 10) / 10)
const savedCardName = ref<string | null>(null)

const visible = ref(false)
const steps = ref<StepKey[]>([])
const stepIndex = ref(0)
const direction = ref<'forward' | 'backward'>('forward')
const currentStep = computed<StepKey | undefined>(() => steps.value[stepIndex.value])

const installing = ref(false)
const installOutcome = ref<'accepted' | 'dismissed' | null>(null)

const panel = ref<HTMLElement | null>(null)
const scroller = ref<HTMLElement | null>(null)

// Mobil den Rahmen an den sichtbaren Ausschnitt binden: iOS Safari schiebt bei offener
// Tastatur nur diesen Ausschnitt, sonst wandert der Kopf aus dem Bild (Ladekarten-Schritt).
const isMobile = useIsMobile()
const viewport = useVisualViewportBox()
const frameStyle = computed(() => isMobile.value && viewport.value
  ? { top: `${viewport.value.top}px`, height: `${viewport.value.height}px` }
  : undefined)

const busy = computed(() => quickAdd.creating.value || card.saving.value || installing.value)

const onboardingKey = () => onboardingSeenKey(authStore.user?.sub)

interface FooterConfig {
  primaryLabel: string
  canProceed: boolean
  primaryAction: () => void | Promise<void>
  skipLabel?: string
  skipAction?: () => void
}

const footer = computed<FooterConfig>(() => {
  switch (currentStep.value) {
    case 'car':
      if (quickAdd.created.value) return { primaryLabel: t('onboarding.next_btn'), canProceed: true, primaryAction: next }
      return {
        primaryLabel: t('onboarding.car_create_btn'),
        canProceed: quickAdd.canCreate.value,
        primaryAction: quickAdd.createCar,
        skipLabel: t('onboarding.car_skip'),
        skipAction: next,
      }
    case 'card':
      if (savedCardName.value) return { primaryLabel: t('onboarding.next_btn'), canProceed: true, primaryAction: next }
      return {
        primaryLabel: t('onboarding.card_save_btn'),
        canProceed: card.canSave.value,
        primaryAction: saveCard,
        skipLabel: t('onboarding.card_skip'),
        skipAction: next,
      }
    case 'install':
      // Der Homescreen-Button gehoert an die prominenteste Stelle, nicht in den Fliesstext.
      if (canPrompt.value && installOutcome.value === null) return {
        primaryLabel: installing.value ? t('onboarding.install_pending') : t('onboarding.install_btn'),
        canProceed: true,
        primaryAction: runInstall,
        skipLabel: t('onboarding.install_skip'),
        skipAction: complete,
      }
      return { primaryLabel: t('onboarding.finish_btn'), canProceed: true, primaryAction: complete }
    default:
      return { primaryLabel: t('onboarding.welcome_cta'), canProceed: true, primaryAction: next }
  }
})

function next() {
  if (stepIndex.value >= steps.value.length - 1) { complete(); return }
  direction.value = 'forward'
  stepIndex.value++
}

/** Im Fahrzeug-Schritt zuerst durch dessen eigene Phasen zurueck, erst dann eine Stufe hoch. */
function handleBack() {
  if (currentStep.value === 'car' && !quickAdd.created.value && quickAdd.phase.value !== 'brand-select') {
    quickAdd.back()
    return
  }
  if (stepIndex.value === 0) return
  direction.value = 'backward'
  stepIndex.value--
}

async function saveCard() {
  const provider = await card.save()
  if (provider) savedCardName.value = provider.providerName
}

async function runInstall() {
  installing.value = true
  try {
    installOutcome.value = await promptInstall()
  } finally {
    installing.value = false
  }
}

function close() {
  visible.value = false
  isOnboardingVisible.value = false
  localStorage.setItem(onboardingKey(), 'true')
}

function skip() {
  analytics.trackOnboardingSkipped(stepIndex.value + 1)
  close()
}

function complete() {
  analytics.trackOnboardingCompleted(stepIndex.value + 1)
  close()
  // Auf dem Dashboard stehen Import und AutoSync als naechste Schritte bereit.
  router.push('/dashboard')
}

function open() {
  // Reihenfolge einmalig festlegen: installiert der User waehrend des Schritts, soll die
  // Bestaetigung stehen bleiben statt den Schritt unter ihm wegzuziehen.
  steps.value = isStandalone.value
    ? ['welcome', 'car', 'card']
    : ['welcome', 'car', 'card', 'install']
  visible.value = true
  isOnboardingVisible.value = true
  analytics.trackOnboardingStarted()
  analytics.trackOnboardingStepViewed(1)
  // Erst jetzt laden, nicht bei jedem App-Start eines Bestandsusers.
  // loadBrands faengt seinen Fehler selbst ab und setzt brandsFailed - hier bleibt nichts offen.
  void quickAdd.loadBrands()
}

onMounted(async () => {
  if (localStorage.getItem('onboarding-force') === 'true') {
    localStorage.removeItem('onboarding-force')
    open()
    return
  }
  // Ohne User-ID laesst sich der Gesehen-Status nicht zuordnen - dann lieber gar nichts zeigen.
  if (!authStore.user?.sub) return
  if (localStorage.getItem(onboardingKey())) return

  try {
    const response = await api.get('/cars')
    if (Array.isArray(response.data) && response.data.length > 0) {
      localStorage.setItem(onboardingKey(), 'true')
      return
    }
    open()
  } catch {
    // Nicht als gesehen markieren - beim naechsten Laden erneut versuchen.
  }
})

watch(stepIndex, (index) => {
  analytics.trackOnboardingStepViewed(index + 1)
  // Jeder Schritt beginnt oben, sonst startet ein kurzer Schritt mitten im Text.
  if (scroller.value) scroller.value.scrollTop = 0
})

const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])'

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { skip(); return }
  if (event.key !== 'Tab' || !panel.value) return
  const items = Array.from(panel.value.querySelectorAll<HTMLElement>(FOCUSABLE))
    .filter(el => el.offsetParent !== null)
  if (items.length === 0) return
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === panel.value)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

let previousOverflow = ''

watch(visible, (isOpen) => {
  if (isOpen) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', onKeydown)
    nextTick(() => panel.value?.focus())
  } else {
    document.body.style.overflow = previousOverflow
    document.removeEventListener('keydown', onKeydown)
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  if (visible.value) document.body.style.overflow = previousOverflow
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-300"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0">
      <div v-if="visible"
        class="fixed inset-0 z-50 md:flex md:items-center md:justify-center md:p-4 md:bg-black/70 md:backdrop-blur-sm">
        <!-- Mobil ueber den ganzen Schirm, ab md als Dialog mit Rahmen wie im Log-Assistenten. -->
        <div ref="panel" :style="frameStyle" tabindex="-1" role="dialog" aria-modal="true"
          aria-labelledby="onboarding-title" data-testid="onboarding-wizard"
          class="fixed inset-0 flex flex-col bg-white dark:bg-gray-800 pt-[env(safe-area-inset-top)] focus:outline-none
                 md:static md:pt-0 md:w-full md:max-w-2xl md:max-h-[90dvh] md:rounded-sm
                 md:shadow-[6px_6px_0_rgba(0,0,0,0.40)] md:dark:shadow-[6px_6px_0_rgba(255,255,255,0.40)]">

          <header class="px-4 pt-3 pb-3 md:px-8 md:pt-6">
            <div class="flex items-center justify-between gap-3">
              <span class="text-xs font-semibold uppercase tracking-widest text-indigo-500 dark:text-indigo-400">
                {{ t('onboarding.badge') }}
              </span>
              <button type="button" data-testid="onboarding-close" :aria-label="t('onboarding.close_label')"
                @click="skip"
                class="w-11 h-11 -mr-3 flex items-center justify-center rounded-sm text-gray-400 transition
                       hover:text-gray-600 hover:bg-gray-100 dark:hover:text-gray-200 dark:hover:bg-gray-700">
                <XMarkIcon class="h-6 w-6" />
              </button>
            </div>
            <div class="flex gap-1 mt-1" role="progressbar" :aria-valuenow="stepIndex + 1" :aria-valuemin="1"
              :aria-valuemax="steps.length"
              :aria-label="t('onboarding.progress_label', { step: stepIndex + 1, total: steps.length })">
              <i v-for="(stepKey, i) in steps" :key="stepKey"
                :class="['flex-1 h-1 rounded-sm transition-colors duration-300',
                         i <= stepIndex ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-700']" />
            </div>
          </header>

          <div ref="scroller" class="flex-1 min-h-0 overflow-y-auto">
            <Transition :name="direction === 'forward' ? 'slide-forward' : 'slide-backward'" mode="out-in">
              <StepWelcome v-if="currentStep === 'welcome'" key="welcome" />
              <StepCar v-else-if="currentStep === 'car'" key="car" :flow="quickAdd" />
              <StepChargingCard v-else-if="currentStep === 'card'" key="card" :card="card" :price-unit="priceUnit"
                :saved-name="savedCardName" />
              <StepInstall v-else-if="currentStep === 'install'" key="install" :platform="platform"
                :is-standalone="isStandalone" :outcome="installOutcome" />
            </Transition>
          </div>

          <footer
            class="border-t border-gray-200 dark:border-gray-700 px-4 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] md:px-8 md:pb-5">
            <div class="flex items-center gap-2">
              <button v-if="stepIndex > 0" type="button" data-testid="onboarding-back" @click="handleBack"
                class="px-3 py-3 text-sm font-medium text-gray-500 dark:text-gray-400 inline-flex items-center gap-1
                       rounded-sm transition hover:text-gray-800 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700">
                <ChevronLeftIcon class="h-4 w-4" />{{ t('onboarding.back_btn') }}
              </button>
              <button type="button" data-testid="onboarding-primary" :disabled="!footer.canProceed || busy"
                @click="footer.primaryAction()"
                :class="['flex-1 bg-indigo-600 text-white p-3 rounded-sm btn-3d font-semibold transition',
                         !footer.canProceed || busy ? 'opacity-40 cursor-not-allowed' : 'hover:bg-indigo-700']">
                {{ footer.primaryLabel }}
              </button>
            </div>
            <!-- Das Ueberspringen steht bewusst kleiner und unter dem eigentlichen Ziel. -->
            <button v-if="footer.skipLabel" type="button" data-testid="onboarding-skip" :disabled="busy"
              @click="footer.skipAction?.()"
              class="mt-1 w-full py-2.5 text-sm text-gray-500 dark:text-gray-400 rounded-sm transition
                     hover:text-gray-800 dark:hover:text-gray-100">
              {{ footer.skipLabel }}
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.slide-forward-enter-active,
.slide-forward-leave-active,
.slide-backward-enter-active,
.slide-backward-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease;
}

.slide-forward-enter-from { transform: translateX(48px); opacity: 0; }
.slide-forward-leave-to { transform: translateX(-48px); opacity: 0; }
.slide-backward-enter-from { transform: translateX(-48px); opacity: 0; }
.slide-backward-leave-to { transform: translateX(48px); opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .slide-forward-enter-active,
  .slide-forward-leave-active,
  .slide-backward-enter-active,
  .slide-backward-leave-active {
    transition: opacity 0.15s ease;
  }
  .slide-forward-enter-from,
  .slide-forward-leave-to,
  .slide-backward-enter-from,
  .slide-backward-leave-to {
    transform: none;
  }
}
</style>
