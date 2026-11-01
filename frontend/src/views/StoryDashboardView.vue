<script setup lang="ts">
import { ref, computed, defineAsyncComponent, nextTick, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from '../composables/useLocaleFormat'
import { useRoute, useRouter } from 'vue-router'
import { ArrowPathIcon, ArrowRightIcon, ArrowUturnLeftIcon } from '@heroicons/vue/24/outline'
import { useCarContext } from '../composables/useCarContext'
import { useMonthSwipe } from '../composables/useMonthSwipe'
import { useDashboardStory, type StoryMonth, type StoryCarContext } from '../composables/useDashboardStory'
import { useStoryOptIn } from '../composables/useStoryOptIn'
import { useChargingLive } from '../composables/useChargingLive'
import { registerSoftReload } from '../composables/usePullToRefresh'
import { useAuthStore } from '../stores/auth'
import { useAnalyticsUpsellTarget } from '../composables/useUpsellTarget'
import { useVehicleCharging } from '../composables/useVehicleCharging'
import { useMomentView } from '../composables/useMomentView'
import { useHaptic } from '../composables/useHaptic'
import { useIsMobile } from '../composables/useIsMobile'
import { analytics } from '../services/analytics'
import type { TileId } from '../composables/storyModel'
import { autoSyncProviderFor, autoSyncOfferFor } from '../composables/useCarAutoSyncProvider'
import { purchasesAvailable } from '../utils/iapPolicy'
import { shareOrCopy, type ShareOutcome } from '../utils/shareOrCopy'
import { carDisplayName } from '../utils/enumLabel'
import { isPreviewHost, mainAppOrigin } from '../utils/previewHost'
import { DAY, winterOutlook, type Moment } from '../composables/storyMoments'
import StoryRecapHero from '../components/story/StoryRecapHero.vue'
import BoldNumbers from '../components/story/BoldNumbers.vue'
import { useMomentSeen } from '../composables/useMomentSeen'
import StoryNewsStack from '../components/story/StoryNewsStack.vue'
import StoryNewsList from '../components/story/StoryNewsList.vue'
import StoryCommunityMonth from '../components/story/StoryCommunityMonth.vue'
import StoryHero from '../components/story/StoryHero.vue'
import StoryLatestCards from '../components/story/StoryLatestCards.vue'
import StoryLiveHero from '../components/story/StoryLiveHero.vue'
import StoryTileRow from '../components/story/StoryTileRow.vue'
import StoryFeed from '../components/story/StoryFeed.vue'
import DataQualityLine from '../components/story/DataQualityLine.vue'
import StoryMonthStepper from '../components/story/StoryMonthStepper.vue'
import CarSwitch from '../components/car/CarSwitch.vue'
import api from '../api/axios'
import { teaserParts, type PlacePeriod } from '../composables/storyPlaces'
import type { ChargingBenchmark } from '../api/chargingBenchmarkService'
import type { EvLogResponse } from '../components/dashboard/EditLogModal.vue'
import type { SummarySection } from '../components/log-wizard/LogSummary.vue'
import { dayKeyOf } from '../composables/storyDay'
import { useLogsRefreshStore } from '../stores/logsRefresh'

const StorySheet = defineAsyncComponent(() => import('../components/story/StorySheet.vue'))
const StoryTilePane = defineAsyncComponent(() => import('../components/story/StoryTilePane.vue'))
const StoryPriceSheet = defineAsyncComponent(() => import('../components/story/StoryPriceSheet.vue'))
const PlacesSheet = defineAsyncComponent(() => import('../components/story/places/PlacesSheet.vue'))
const EditTripModal = defineAsyncComponent(() => import('../components/dashboard/EditTripModal.vue'))
const EditLogModal = defineAsyncComponent(() => import('../components/dashboard/EditLogModal.vue'))
const PricelessLogsModal = defineAsyncComponent(() => import('../components/dashboard/PricelessLogsModal.vue'))
const StoryDaySheet = defineAsyncComponent(() => import('../components/story/day/StoryDaySheet.vue'))
const LogFormModal = defineAsyncComponent(() => import('../components/log-form/LogFormModal.vue'))

const { t, locale } = useI18n()
const { formatDistance } = useLocaleFormat()
const router = useRouter()
const authStore = useAuthStore()
const { haptic } = useHaptic()
// Supporter-Bitte am Meilenstein: nur wer die Daten schon gratis hat (Ziel /supporter) und kaufen kann
const upsellTarget = useAnalyticsUpsellTarget()
const supporterAsk = computed(() => purchasesAvailable() && upsellTarget.value === '/supporter')
const { set: setOptIn } = useStoryOptIn()

// Das Template hat genau ein Wurzelelement, ohne Kommentar davor: die Routen-Transition in App.vue
// (mode out-in) kann ein Fragment nicht ausblenden, der Leave endet nie und die router-view bleibt leer.
// Linker Tab im CarContextLayout: Kopf und Chips bringt WorkspaceFrame aus App.vue, den Car-Context teilt
// die Uebersicht mit /logs. Mobil liegt sie auch auf /logs gemountet im Pager daneben.
// Boxen mobil randlos, ab md mit Kante auf dem Wallpaper.
const ctx = useCarContext()
const { selectedCarId, cars, carImageUrls, wltp, currentOdometerKm, smartcarStatus, teslaStatus } = ctx

const selectedCar = computed(() => cars.value.find((c: any) => c.id === selectedCarId.value) ?? cars.value[0] ?? null)
const sohPercent = computed(() => selectedCar.value?.batteryDegradationPercent != null ? 100 - selectedCar.value.batteryDegradationPercent : null)
const effectiveCapacity = computed<number | null>(() => selectedCar.value?.effectiveBatteryCapacityKwh ?? null)

// Live-Laden: abgefragt wird nur bei Marken mit Live-Quelle (Tesla, Smartcar-Marken inkl. Polestar).
// Ob wirklich geladen wird, entscheidet der Connector.
const liveGate = computed(() => !!selectedCar.value && autoSyncProviderFor(selectedCar.value) !== 'NONE')
const liveCarId = computed(() => liveGate.value ? selectedCarId.value : null)
const { data: live, isLive: liveActive } = useChargingLive(liveCarId)
watch(liveActive, (v) => { if (v) analytics.track('story_live_shown') })

// AutoSync-Satz im Stille-Moment und im leeren Monat: Marke hat einen Weg, dieses Auto ist nicht verbunden.
const autoSyncOffer = computed(() => autoSyncOfferFor(selectedCar.value, smartcarStatus.value, teslaStatus.value))

const storyCtx = (): StoryCarContext => ({
  wltpConsumption: wltp.value?.officialConsumptionKwhPer100km ?? null,
  sohPercent: sohPercent.value,
  odometerKm: currentOdometerKm.value,
  vehicleCategory: selectedCar.value?.category ?? null,
  effectiveBatteryCapacityKwh: effectiveCapacity.value,
  // Stand wie die Energie-Split-Kachel des alten Dashboards: Analytics oder Probemonat
  canViewEnergySplit: authStore.canViewEnergySplit,
  autoSync: autoSyncOffer.value,
  supporterAsk: supporterAsk.value,
})
const story = useDashboardStory(selectedCarId, storyCtx)
const { month, offset, tiles, feed, days, monthStats, hasCharges, loading, error, layers, switching } = story


const { isVehicleCharging } = useVehicleCharging(cars, smartcarStatus)
const charging = computed(() => selectedCar.value ? isVehicleCharging(selectedCar.value) : false)

const monthLabel = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'long' })
const monthLabelShort = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value, { month: 'short' })
/** Der Hero-Satz nennt den Monat statt der Pfeile, daher bei einem anderen Jahr mit Jahreszahl ("März 2025:"). */
const heroMonthLabel = (m: StoryMonth) => new Date(m.year, m.month - 1, 1).toLocaleDateString(locale.value,
  m.year === new Date().getFullYear() ? { month: 'long' } : { month: 'long', year: 'numeric' })
const sixMonthLabels = computed(() => Array.from({ length: 6 }, (_, i) =>
  new Date(month.value.year, month.value.month - 1 - (5 - i), 1).toLocaleDateString(locale.value, { month: 'short' })))

// Hero: Live, solange geladen wird, sonst immer der Monat. Neues kommt als Karte von unten.
const heroMode = computed<'live' | 'month'>(() => liveActive.value && live.value ? 'live' : 'month')
const carName = computed(() => selectedCar.value ? carDisplayName(selectedCar.value) : '')
/** Modell ohne Variante für den Vergleich mit allen Fahrern des Modells. */
const modelName = computed<string>(() => selectedCar.value?.modelDisplayName ?? '')
const shareStatus = ref<ShareOutcome | null>(null)
async function share(title: string, text: string) {
  shareStatus.value = await shareOrCopy({ title, text, url: window.location.origin })
  if (shareStatus.value !== 'shared') setTimeout(() => { shareStatus.value = null }, 3000)
}

// Unter lg steht der Feed zwischen "Zuletzt" und den Kacheln, ab lg rechts neben dem Hero. Einmal gerendert, nicht zweimal.
const narrow = useIsMobile('(max-width: 1023px)')
const sheetIds = computed(() => tiles.value.map(x => x.id))
// Steht, sobald ein Monat da ist, und bleibt beim Monatswechsel stehen (der neue kommt mit seinen Daten)
const showFeed = computed(() => !!monthStats.value && !error.value && (hasCharges.value || liveActive.value))
/**
 * Laufender Monat ohne Ladung und ohne Live-Laden: der Rückblick auf den Vormonat steht als Kopf,
 * auch wenn seine Karte schon weggeklickt ist. Im Stapel fehlt er dann.
 */
const recapHero = computed<Moment | null>(() =>
  !(loading.value && !monthStats.value) && !error.value && !hasCharges.value && heroMode.value === 'month' && month.value.offset === 0
    ? story.monthRecap.value : null)
/** Rückblick als Kopf: dann sitzt der Monatswähler in seiner Kopfzeile statt als eigene Zeile darüber. */
const recapOnTop = computed(() => !!recapHero.value)
const newsCards = computed(() => recapHero.value ? story.cards.value.filter(c => c.moment.kind !== 'month_change') : story.cards.value)
/** Winter-Ausblick im leeren laufenden Monat; steht die Frost-Karte offen, sagt sie dasselbe. */
const outlook = computed(() => {
  if (month.value.offset !== 0 || newsCards.value.some(c => c.moment.kind === 'first_frost')) return null
  const o = winterOutlook(story.range.value, month.value.month)
  return o && t('story.outlook_winter', { winter: formatDistance(o.winterKm, { round: true }), summer: formatDistance(o.summerKm, { round: true }) })
})
/** Vormonat aus dem offenen Rückblick (Kopf oder Karte): der Community-Block hängt an ihm und verschwindet mit ihm. */
const communityMonth = computed(() => {
  const m = recapHero.value ?? story.cards.value.find(c => c.moment.kind === 'month_change')?.moment
  return m?.kind === 'month_change' && month.value.offset === 0 ? { year: m.year, month: m.month } : null
})
/** Steht der Monatssatz (StoryHero)? Dann sitzen die Monatspfeile in ihm, sonst als eigene Zeile darüber. */
const monthHero = computed(() => !(loading.value && !monthStats.value) && !error.value && hasCharges.value && heroMode.value === 'month')
/** Steht der Live-Hero? Dann sitzen die Monatspfeile in seiner Kopfzeile. */
const liveHero = computed(() => !(loading.value && !monthStats.value) && !error.value && heroMode.value === 'live')


// Monatswechsel: Segment-Schalter oder horizontaler Wisch auf dem Hero.
function selectMonth(o: number) {
  if (o === offset.value) return
  haptic()
  analytics.track('story_month_changed', { offset: o })
  monthDirection = Math.sign(o - offset.value)
  story.setOffset(o)
}
// Bewegung nur im Inhalt der Box: dieselbe wie beim Seitenwechsel (14 px, Einblenden), ein neuerer Monat von rechts,
// ein älterer von links. Keine View Transition, die griffe das ganze Dokument samt Seitenwechsel-Regeln aus App.vue
// und die Seite spränge. Animiert wird, sobald der neue Monat steht: aus dem Cache sofort, sonst nach dem Laden.
// Noch nicht vorgeladen (langsames Netz): der bisherige Monat bleibt stehen und dimmt erst nach 150 ms, ein schneller
// Wechsel flackert so nicht
const monthWaiting = 'opacity-50 transition-opacity duration-200 delay-150'
const monthMain = ref<HTMLElement | null>(null)
const monthSide = ref<HTMLElement | null>(null)
let monthDirection = 0
watch(monthStats, () => {
  const dir = monthDirection
  monthDirection = 0
  if (!dir || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  for (const el of [monthMain.value, monthSide.value]) {
    // Ab lg ist die Fläche groß und das Auge weiter weg: etwas ruhiger als auf dem Handy
    el?.animate([{ opacity: 0, transform: `translateX(${dir * 14}px)` }, { opacity: 1, transform: 'none' }],
      { duration: narrow.value ? 220 : 340, easing: 'cubic-bezier(0.22, 0.68, 0.24, 1)' })
  }
}, { flush: 'post' })
// Wisch auf der Box: sie geht mit dem Finger mit, daneben steht senkrecht der Zielmonat. Im laufenden Monat
// gehört der Wisch nach links dem Tab-Pager (direkt zu den Logs).
const heroBox = ref<HTMLElement | null>(null)
const neighborLabel = (d: number) => new Date(month.value.year, month.value.month - 1 + d, 1).toLocaleDateString(locale.value, { month: 'long' })
const swipeLabels = computed(() => ({
  prev: offset.value - 1 >= story.minOffset.value ? neighborLabel(-1) : null,
  next: offset.value < 0 ? neighborLabel(1) : null,
}))
// Wort einmal je Geste vermessen: bei 100 px Schrift die Länge messen, dann so skalieren, dass es von der
// Oberkante der Box bis knapp über die untere Navigation reicht.
const swipeText = ref<HTMLElement | null>(null)
const swipeFit = ref({ top: 0, px: 100 })
const BOTTOM_NAV_PX = 96
const swipeLabel = computed(() => dragX.value > 0 ? swipeLabels.value.prev : swipeLabels.value.next)
const { dragX, dragging, threshold: swipeThreshold } = useMonthSwipe(heroBox,
  () => ({ prev: swipeLabels.value.prev != null, next: swipeLabels.value.next != null, passNext: offset.value === 0 }),
  side => selectMonth(offset.value + (side === 'prev' ? -1 : 1)))
watch(() => dragX.value !== 0, async on => {
  if (!on) return
  const top = Math.max(16, heroBox.value?.getBoundingClientRect().top ?? 16)
  swipeFit.value = { top, px: 100 }
  await nextTick()
  const len = swipeText.value?.getBoundingClientRect().height ?? 0
  if (len > 0) swipeFit.value = { top, px: Math.floor(100 * (window.innerHeight - top - BOTTOM_NAV_PX) / len) }
})

const openTile = ref<TileId | null>(null)
const openMap = ref(false)
const openPrices = ref(false)
/** Preise eines Betreibers aus "Diesen Monat" (teurer Betreiber), mit deinem Preis dort. */
const feedPrices = ref<{ benchmark: ChargingBenchmark; pricePerKwh: number } | null>(null)
function openFeedPrices(benchmark: ChargingBenchmark, pricePerKwh: number) { feedPrices.value = { benchmark, pricePerKwh } }
/** Zeitraum der Ladekarte, bleibt beim Schließen und Wiederöffnen erhalten. */
const placesPeriod = ref<PlacePeriod>('12m')
const operatorLog = ref<EvLogResponse | null>(null)
/** Abschnitt, in dem der Editor öffnet: Ort aus der Ladekarte, Kosten bei fehlendem Preis. */
const operatorSection = ref<SummarySection | undefined>('place')
const placesSheet = ref<{ reload: () => void } | null>(null)
/** "Betreiber eintragen": die Ladung öffnet im Ort, über der Karte und leichter abgedunkelt. */
/** Ladung im Sheet öffnen: ohne Abschnitt auf der Übersicht (ansehen), sonst direkt im Abschnitt. */
async function openCharge(logId: string, section?: SummarySection) {
  operatorSection.value = section
  try {
    operatorLog.value = (await api.get<EvLogResponse>(`/logs/${logId}`)).data
  } catch { /* Ladung weg oder offline: die Karte bleibt einfach stehen */ }
}
/** Ladeort-Karte: Betreiber eintragen, also direkt beim Ort. */
function openOperator(logId: string) { openCharge(logId, 'place') }
/** Karte "Zuletzt geladen": Ladung ansehen; der Preis-Knopf und der Chip "Preis nachtragen" führen direkt zu den Kosten. */
function openLatestCharge(logId: string, toPrice: boolean) {
  openCharge(logId, toPrice ? 'cost' : undefined)
}

// Karte "Letzte Fahrt": dasselbe Fahrt-Sheet wie das alte Dashboard, Formular-State im CarContext.
const tripModalId = ref<string | null>(null)
const tripSheet = ref<{ requestClose: () => void } | null>(null)
function openLatestTrip(tripId: string) {
  // Die Karte überspringt Umparken: dann ist ihre Fahrt nicht die neueste, sondern eine der letzten Tage
  const trip = ctx.latestTrip.value?.id === tripId ? ctx.latestTrip.value : story.recentTrips.value.find(t => t.id === tripId)
  if (!trip) return
  ctx.startEditTrip(trip)
  tripModalId.value = tripId
}
async function submitTripEdit() {
  const id = tripModalId.value
  if (!id) return
  await ctx.saveTripEdit(id)
  if (ctx.tripError.value) return
  tripSheet.value?.requestClose()
  ctx.refreshLogsAndGroups()
  story.reload()
}
function onTripSheetClosed() {
  ctx.cancelTripEdit()
  tripModalId.value = null
}

/** Zeile "n Ladungen ohne Preis": dieselbe Liste wie auf /logs, je Ladung den Preis eintragen */
const showPriceless = ref(false)

function closeOperator(changed: boolean) {
  operatorLog.value = null
  if (!changed) return
  placesSheet.value?.reload()
  story.reload()
}

// Teaser der Ladekarte unter dem Feed: Orte des Story-Monats, kommen mit dessen Daten und wechseln mit ihm
const teaserRows = story.placeRows
const placesTeaser = computed(() => teaserParts(teaserRows.value))
/** Der Teaser spricht vom Story-Monat, also öffnet die Karte dort. Ohne Orte im Monat die zwölf Monate. */
function openPlaces() {
  placesPeriod.value = placesTeaser.value ? 'month' : '12m'
  openMap.value = true
}
/** Was der Nutzer bei der Ladung gezahlt hat, die die Karte "zu teuer geladen" ausloest. */
const expensivePrice = computed(() => {
  const m = story.cards.value.find(c => c.moment.kind === 'expensive')?.moment
  return m?.kind === 'expensive' ? m.pricePerKwh : null
})

// Neuigkeiten (mobil von unten, ab lg in der Spalte): Handlungen, Erledigt (nur dieses Gerät) und Zählen.
const { seen, markShown, dismiss } = useMomentSeen()
function onNewsShown(m: Moment) {
  if (seen.value.shown[m.key] != null) return
  markShown(m.key)
  analytics.track('story_moment_shown', { moment: m.kind })
}
function onNewsAct(m: Moment, id: string) {
  haptic()
  analytics.track('story_moment_tapped', { moment: m.kind, action: id })
  if (id === 'share') {
    const v = newsText(m)
    share(v.title, v.text)
    if (m.kind === 'month_change') dismiss(m.key, Date.now(), 365 * DAY)
  }
  else if (id === 'winter') openSheet('range')
  // Rückblick: der Vormonat steht mit allen Kacheln und Insights eine Seite zurück, die Karte bleibt offen.
  else if (id === 'prev_month') selectMonth(offset.value - 1)
  else if (id === 'prices') openPrices.value = true
  else if (id === 'price_unknown' && m.kind === 'gap') story.markPriceUnknown(m.logId)
  else if (id === 'battery') {
    // Ohne Akku-Kachel (kein SoH am Auto) fuehrt der Verlauf zur Fahrzeugseite.
    if (tiles.value.some(x => x.id === 'battery')) openSheet('battery')
    else router.push('/cars')
  }
}
function onNewsDismiss(m: Moment) {
  analytics.track('story_moment_dismissed', { moment: m.kind })
  dismiss(m.key, Date.now(), (m.kind === 'month_change' ? 365 : 30) * DAY)
}
/** Text zum Teilen: Satz und Unterzeile der Karte. */
function newsText(m: Moment): { title: string; text: string } {
  const v = useMomentView({ moment: m, carId: selectedCarId.value, carName: carName.value }).value
  // Der Platz geht mit auf die Reise, der Modellvergleich nicht (zu viel für einen geteilten Satz).
  return { title: v.eyebrow, text: [v.sentence, v.sub, v.details?.find(d => d.id === 'rank')?.text].filter(Boolean).join(' ') }
}
function openSheet(id: TileId) {
  haptic()
  analytics.track('story_tile_opened', { tile: id })
  openTile.value = id
}
const sheetTile = computed(() => tiles.value.find(x => x.id === openTile.value) ?? null)
// Ab lg steht das Detail rechts in der Spalte statt als Sheet: Lesen ohne Abdunkeln, die Kacheln bleiben daneben.
const paneTile = computed(() => narrow.value ? null : sheetTile.value)
const feedLabel = computed(() => month.value.offset === 0 ? t('story.feed_title') : t('story.feed_title_past', { month: monthLabel(month.value) }))
/** Pfeiltasten in der Spalte: nächste oder vorige Kachel, im Kreis. */
function stepTile(dir: 1 | -1) {
  const ids = sheetIds.value
  const i = ids.indexOf(openTile.value as TileId)
  if (i >= 0) openSheet(ids[(i + dir + ids.length) % ids.length])
}
/** Spalte schließen: der Fokus geht zurück auf die Kachel, von der das Detail kam. */
function closePane() {
  const id = openTile.value
  openTile.value = null
  nextTick(() => document.querySelector<HTMLElement>(`[data-testid="story-tile-${id}"]`)?.focus({ preventScroll: true }))
}
const sheetContext = computed(() => ({
  stats: monthStats.value,
  monthLabels: sixMonthLabels.value,
  monthName: monthLabel(month.value),
  runningMonth: month.value.offset === 0,
  modelPeers: story.modelPeers.value,
  modelName: modelName.value,
  temperature: story.temperature.value,
  sohHistory: story.sohHistory.value,
  effectiveCapacityKwh: effectiveCapacity.value,
  hasFixedCosts: story.hasFixedCosts.value,
  loadMonthlyCosts: story.loadMonthlyCosts,
  savings: story.savingsAccess.value,
}))

const noteSeen = ref(false)
try { noteSeen.value = localStorage.getItem('story_note_seen') === 'true' } catch { /* egal */ }
function dismissNote() {
  noteSeen.value = true
  try { localStorage.setItem('story_note_seen', 'true') } catch { /* egal */ }
}

/** Vorschau-Adresse: der Rückweg zur gewohnten App (Hauptadresse), gut sichtbar neben dem alten Dashboard. */
const previewHost = isPreviewHost()
const mainAppUrl = previewHost ? `${mainAppOrigin()}/story` : ''

function backToClassic() {
  setOptIn(false)
  router.push('/dashboard')
}

// Tagesblatt: Tag im Zeitstrahl antippen. "Ladung nachtragen" schliesst das Blatt, oeffnet den
// Erfassen-Wizard mit dem Datum und kehrt danach zum selben Tag zurueck.
const openDay = ref<string | null>(null)
const todayKey = () => dayKeyOf(new Date())
function onOpenDay(d: number) {
  const pad = (n: number) => String(n).padStart(2, '0')
  openDay.value = `${month.value.year}-${pad(month.value.month)}-${pad(d)}`
}
const addCharge = ref<{ day: string; loggedAt: string } | null>(null)
function onAddCharge(loggedAt: string) {
  addCharge.value = { day: openDay.value!, loggedAt }
  openDay.value = null
}
function onAddChargeClosed() {
  openDay.value = addCharge.value?.day ?? null
  addCharge.value = null
}
// Rueckgaengig im Loesch-Toast (Ladung oder Fahrt) aendert die Zahlen des Monats
watch(() => useLogsRefreshStore().version, () => story.reload())

// Nur solange die Uebersicht offen ist (mobil liegt sie auch unter /logs im Pager): Oeffnen zaehlen,
// Pull-to-refresh (native App) laedt dann ihre Daten neu statt der ganzen Seite.
const route = useRoute()
let unregisterReload: (() => void) | null = null
watch(() => route.path === '/story', (open) => {
  unregisterReload?.()
  unregisterReload = open ? registerSoftReload(() => story.reload()) : null
  if (open) analytics.track('story_viewed')
}, { immediate: true })
onUnmounted(() => unregisterReload?.())
</script>

<template>
  <div data-testid="story-view">
    <main class="mx-auto grid max-w-6xl grid-cols-1 gap-4 pb-6 md:px-6 md:pb-12">
      <!-- Eine Box: oben Auto und Monat, darunter ab lg zwei Spalten 7/5, links Hero bis Kacheln, rechts eine stehende Spalte:
           Neuigkeiten und Feed, oder das Detail der gewählten Kachel. -->
      <!-- Wischen blättert die Monate, die Box geht mit, dahinter steht senkrecht der Zielmonat, immer an derselben
           Stelle am Rand und über die volle Höhe; ab der Schwelle grün. Im laufenden Monat führt der Wisch nach links wie überall direkt zu den Logs.
           touch-pan-y: waagerecht gehört der App, sonst bricht der Browser die Geste ab -->
      <div class="relative overflow-hidden md:overflow-visible">
      <!-- Streifen über die ganze Bildschirmhöhe (fixed), der Zielmonat gedreht wie ein Buchrücken: beginnt oben an der Box,
           füllt die Höhe bis zur Navigation und hängt an der Kante der Box, wandert also mit dem Finger mit -->
      <div v-if="dragX !== 0" class="fixed inset-y-0 overflow-hidden bg-gray-100 dark:bg-gray-800" :class="dragX > 0 ? 'left-0' : 'right-0'"
        :style="{ width: `${Math.abs(dragX)}px` }" aria-hidden="true" data-testid="story-swipe-strip">
        <span ref="swipeText" class="absolute whitespace-nowrap font-black uppercase leading-none [writing-mode:vertical-rl] transition-colors"
          :class="[dragX > 0 ? 'right-0' : 'left-0', Math.abs(dragX) >= swipeThreshold ? 'text-green-700 dark:text-green-400' : 'text-gray-400 dark:text-gray-500']"
          :style="{ top: `${swipeFit.top}px`, fontSize: `${swipeFit.px}px` }">{{ swipeLabel }}</span>
      </div>
      <section ref="heroBox" class="relative grid touch-pan-y grid-cols-1 gap-4 bg-white px-4 py-4 md:rounded-3xl md:p-6 md:edge-box lg:grid-cols-12 lg:gap-x-8 lg:gap-y-5 dark:bg-gray-900" data-testid="story-hero-box"
        :class="dragging ? '' : 'transition-transform duration-200 ease-out'" :style="dragX ? { transform: `translateX(${dragX}px)` } : undefined">
        <!-- Auto über beiden Spalten, nur bei mehreren Autos: mit einem gibt es nichts zu wählen -->
        <header v-if="cars.length > 1" class="flex items-center lg:col-span-12">
          <CarSwitch v-model="selectedCarId" :cars="cars" :car-image-urls="carImageUrls" />
        </header>
        <div ref="monthMain" class="grid min-w-0 grid-cols-1 content-start gap-4 lg:col-span-7" :class="switching ? monthWaiting : ''">
          <!-- Monatspfeile neben dem Monatssatz; ohne ihn (leer, Laden, Fehler, Live) als eigene Zeile mit Monat -->
          <div v-if="!monthHero && !liveHero && !recapOnTop" class="flex justify-end">
            <StoryMonthStepper :offset="offset" :label-of="monthLabelShort" :min-offset="story.minOffset.value" @select="selectMonth" />
          </div>

          <p v-if="charging && !liveActive" class="flex items-center gap-2 text-[13px] text-story-accent dark:text-story-accent-dark" data-testid="story-live">
            <span class="h-1.5 w-1.5 rounded-full bg-current motion-safe:animate-pulse" />{{ t('story.live_charging') }}
          </p>

          <div v-if="!noteSeen" class="flex items-start gap-3 rounded-md border border-dashed border-gray-300 px-3 py-2 text-xs text-gray-600 dark:border-gray-600 dark:text-gray-300">
            <span class="flex-1">{{ t('story.experiment_note') }}</span>
            <button type="button" class="-my-2 min-h-[44px] font-semibold underline" @click="dismissNote">OK</button>
          </div>

          <div v-if="loading && !monthStats" class="animate-pulse" aria-busy="true">
            <div class="h-8 w-72 max-w-full rounded bg-gray-200 dark:bg-gray-700" />
            <div class="mt-2 h-4 w-56 max-w-full rounded bg-gray-200 dark:bg-gray-700" />
            <div class="mt-6 grid gap-2 md:grid-cols-2">
              <div v-for="i in 2" :key="i" class="h-12 rounded-xl bg-gray-200 dark:bg-gray-700" />
            </div>
          </div>

          <div v-else-if="error" class="text-sm text-rose-700 dark:text-rose-300">
            {{ t('story.error') }}
            <button type="button" class="ml-2 min-h-[44px] font-semibold underline" @click="story.reload()">{{ t('story.retry') }}</button>
          </div>

          <div v-else-if="!hasCharges && heroMode === 'month'" class="grid gap-4" :class="recapOnTop ? '' : 'py-2'">
            <StoryRecapHero v-if="recapHero" :moment="recapHero" :car-id="selectedCarId" :car-name="carName" @act="id => onNewsAct(recapHero!, id)">
              <StoryMonthStepper :offset="offset" :label-of="monthLabelShort" :min-offset="story.minOffset.value" @select="selectMonth" />
            </StoryRecapHero>
            <p v-else class="text-center text-[17px] font-bold text-balance text-gray-900 dark:text-gray-50">{{ t('story.empty_title', { month: monthLabel(month) }) }}</p>
            <div v-if="outlook" data-testid="story-winter-outlook" class="grid gap-0.5">
              <p class="text-[12px] font-semibold uppercase tracking-[0.06em] text-green-700 dark:text-green-400">{{ t('story.outlook_eyebrow', { month: monthLabel(month) }) }}</p>
              <p class="text-[15px] text-pretty text-gray-900 tabular-nums dark:text-gray-50"><BoldNumbers :text="outlook" /></p>
            </div>
            <!-- Monat ohne Ladung: wo die Marke einen AutoSync-Weg hat, dasselbe Angebot wie der Moment "Alles erfasst?" -->
            <router-link v-if="autoSyncOffer" to="/imports" data-testid="story-empty-autosync"
              class="flex items-start gap-3 rounded-xl bg-green-50 p-3.5 text-left edge-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green-600 dark:bg-green-900/20"
              @click="analytics.track('story_empty_autosync')">
              <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300" aria-hidden="true">
                <ArrowPathIcon class="h-5 w-5" />
              </span>
              <span class="grid gap-0.5">
                <span class="text-[15px] font-semibold text-gray-900 dark:text-gray-50">{{ t('story.empty_autosync_title') }}</span>
                <span class="text-sm text-pretty text-gray-700 dark:text-gray-200">{{ t('story.empty_autosync', { car: carName }) }}</span>
                <span class="mt-1 inline-flex items-center gap-1 text-sm font-semibold text-green-700 dark:text-green-300">
                  {{ t('story.empty_autosync_cta') }}<ArrowRightIcon class="h-4 w-4" aria-hidden="true" />
                </span>
              </span>
            </router-link>
          </div>

          <template v-else>
            <div>
              <Transition name="story-hero" mode="out-in">
                <StoryLiveHero v-if="heroMode === 'live' && live" key="live" :live="live">
                  <template #nav>
                    <StoryMonthStepper :offset="offset" :label-of="monthLabelShort" :min-offset="story.minOffset.value" @select="selectMonth" />
                  </template>
                </StoryLiveHero>
                <StoryHero
                  v-else
                  key="month"
                  :month-label="heroMonthLabel(month)"
                  :distance-km="monthStats?.totalDistanceKm ?? null"
                  :energy-cost-eur="monthStats?.energyCostEur ?? null"
                  :charges="monthStats?.totalCharges ?? 0"
                  :layers="layers"
                  @layers="(open) => open && analytics.track('story_layers_opened')">
                  <template #nav>
                    <StoryMonthStepper :offset="offset" :label-of="monthLabelShort" :min-offset="story.minOffset.value" :labelled="false" @select="selectMonth" />
                  </template>
                </StoryHero>
              </Transition>
            </div>
            <p v-if="shareStatus === 'copied' || shareStatus === 'failed'" class="text-sm text-gray-600 dark:text-gray-300" role="status">
              {{ shareStatus === 'copied' ? t('story.moment_copied') : t('story.moment_share_failed') }}
            </p>
            <DataQualityLine v-if="hasCharges && heroMode !== 'live'" :key="`${month.year}-${month.month}`" :days="days" @open-day="onOpenDay" />
            <StoryLatestCards v-if="story.latest.value" :latest="story.latest.value" @open-charge="openLatestCharge" @open-trip="openLatestTrip" />
            <StoryFeed
              v-if="narrow && showFeed"
              :feed="feed" :model-name="modelName" :sheets="sheetIds"
              :places-teaser="placesTeaser" :places-rows="teaserRows" :month-name="monthLabel(month)" :running-month="month.offset === 0"
              @open="openSheet" @map="openPlaces" @charge="openCharge" @priceless="showPriceless = true" @prices="openFeedPrices" />
            <StoryTileRow v-if="hasCharges" :tiles="tiles" :selected="paneTile?.id ?? null" :model-name="modelName" @open="openSheet" />
          </template>
          <StoryCommunityMonth
            v-if="communityMonth && !error"
            :year="communityMonth.year" :month="communityMonth.month"
            @rank="analytics.track('story_community_month_rank')" />
        </div>
        <!-- Steht beim Scrollen unter dem Kopf; eigene Scrollhöhe, Rand für Kante und Fokusring -->
        <div
          v-if="!narrow && (showFeed || paneTile || newsCards.length)" ref="monthSide"
          class="min-w-0 lg:sticky lg:top-[calc(var(--content-top,4rem)+1rem)] lg:col-span-5 lg:-mx-2 lg:max-h-[calc(100dvh-var(--content-top,4rem)-2rem)] lg:self-start lg:overflow-y-auto lg:overscroll-contain lg:px-2 lg:pb-2"
          :class="switching ? monthWaiting : ''" data-testid="story-side">
          <StoryTilePane v-if="paneTile" :tile="paneTile" :context="sheetContext" :back-label="feedLabel" @close="closePane" @step="stepTile" />
          <div v-else class="grid content-start gap-6">
            <StoryNewsList
              v-if="selectedCarId"
              :cards="newsCards" :car-id="selectedCarId" :car-name="carName" :seen="seen"
              @shown="onNewsShown" @act="onNewsAct" @dismiss="onNewsDismiss" />
            <StoryFeed
              v-if="showFeed"
              :feed="feed" :model-name="modelName" :sheets="sheetIds"
              :places-teaser="placesTeaser" :places-rows="teaserRows" :month-name="monthLabel(month)" :running-month="month.offset === 0" map-preview
              @open="openSheet" @map="openPlaces" @charge="openCharge" @priceless="showPriceless = true" @prices="openFeedPrices" />
          </div>
        </div>
      </section>
      </div>

      <div class="flex flex-wrap items-center gap-x-6 px-4 md:px-0">
        <a v-if="previewHost" :href="mainAppUrl" class="inline-flex min-h-[44px] items-center gap-2 text-sm font-semibold text-gray-900 underline underline-offset-2 dark:text-gray-100" data-testid="story-main-app">
          <ArrowUturnLeftIcon class="h-4 w-4" />{{ t('story.back_to_main_app') }}
        </a>
        <button type="button" class="inline-flex min-h-[44px] items-center gap-2 text-sm text-gray-500 underline underline-offset-2 dark:text-gray-400" data-testid="story-back" @click="backToClassic">
          <ArrowUturnLeftIcon class="h-4 w-4" />{{ t('story.back_to_dashboard') }}
        </button>
      </div>
    </main>

    <StorySheet v-if="sheetTile && narrow" :tile="sheetTile" :context="sheetContext" @close="openTile = null" />

    <StoryPriceSheet v-if="openPrices && story.benchmark.value" :benchmark="story.benchmark.value" :price-per-kwh="expensivePrice" @close="openPrices = false" />
    <StoryPriceSheet v-if="feedPrices" :benchmark="feedPrices.benchmark" :price-per-kwh="feedPrices.pricePerKwh" @close="feedPrices = null" />

    <PlacesSheet
      v-if="openMap && selectedCarId"
      ref="placesSheet"
      v-model:period="placesPeriod"
      :car-id="selectedCarId"
      :month="month"
      :month-name="monthLabel(month)"
      :can-view-live-analytics="authStore.canViewLiveAnalytics"
      @close="openMap = false"
      @operator="openOperator" />
    <PricelessLogsModal v-if="showPriceless" :car-id="selectedCarId" open @close="showPriceless = false" @updated="story.reload()" />
    <EditLogModal
      v-if="operatorLog"
      :log="operatorLog"
      :start-section="operatorSection"
      stacked
      @close="closeOperator(false)"
      @saved="closeOperator(true)"
      @deleted="closeOperator(true)" />
    <EditTripModal
      v-if="tripModalId"
      ref="tripSheet"
      v-model="ctx.tripForm.value"
      :error="ctx.tripError.value"
      :saving="ctx.tripSaving.value"
      @save="submitTripEdit"
      @close="onTripSheetClosed" />
    <StoryDaySheet
      v-if="openDay && selectedCarId"
      v-model:day="openDay"
      :car-id="selectedCarId"
      :today="todayKey()"
      @close="openDay = null"
      @changed="story.reload()"
      @add-charge="onAddCharge" />
    <!-- Teleport: mobil liegt die Übersicht im verschobenen Pager-Track, fixed wäre dort relativ zum Track.
         Der Stapel von unten nur unter lg, darüber stehen die Neuigkeiten in der rechten Spalte. -->
    <Teleport to="body">
      <StoryNewsStack
        v-if="narrow && route.path === '/story' && selectedCarId"
        :cards="newsCards" :car-id="selectedCarId" :car-name="carName" :seen="seen"
        @shown="onNewsShown" @act="onNewsAct" @dismiss="onNewsDismiss"
        @collapsed="analytics.track('story_news_collapsed')" @expanded="analytics.track('story_news_expanded')" @list="analytics.track('story_news_list_opened')" />
      <LogFormModal
        v-if="addCharge"
        :car-id="selectedCarId"
        :initial-logged-at="addCharge.loggedAt"
        @saved="story.reload()"
        @close="onAddChargeClosed" />
    </Teleport>
  </div>
</template>

<style scoped>
.story-hero-enter-active, .story-hero-leave-active { transition: opacity 260ms ease, transform 260ms ease; }
.story-hero-enter-from { opacity: 0; transform: translateY(8px); }
.story-hero-leave-to { opacity: 0; transform: translateY(-8px); }
@media (prefers-reduced-motion: reduce) {
  .story-hero-enter-active, .story-hero-leave-active { transition: none; }
}
</style>
