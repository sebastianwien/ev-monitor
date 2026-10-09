<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
    adminRatingService, isRedactionOf, highlightSegments, REDACTION,
    type AdminRatingDetail, type AdminRatingPage, type AdminReviewVersion, type RejectionReason, type TextType,
} from '../../api/adminRatingService'
import type { ReviewStatus } from '../../api/ratingService'

/** Moderation der Erfahrungstexte. Freigabe immer durch einen Menschen, Texte werden nur als Text gerendert. */
const emit = defineEmits<{ openCount: [n: number] }>()

const REASONS: { value: RejectionReason; label: string }[] = [
    { value: 'PERSONAL_DATA', label: 'Persönliche Daten' },
    { value: 'INSULT', label: 'Beleidigung, Schmähkritik' },
    { value: 'ADVERTISING', label: 'Werbung, Referral-Code' },
    { value: 'WRONG_MODEL', label: 'Anderes Modell' },
    { value: 'NO_EXPERIENCE', label: 'Keine eigene Erfahrung' },
    { value: 'OTHER', label: 'Sonstiges (Notiz Pflicht)' },
]

const status = ref<ReviewStatus | ''>('PENDING')
const type = ref<TextType | ''>('')
const page = ref(0)
const data = ref<AdminRatingPage | null>(null)
const detail = ref<AdminRatingDetail | null>(null)
const loading = ref(false)
const error = ref('')
const busy = ref(false)

const redacted = ref('')
const rejectReason = ref<RejectionReason>('PERSONAL_DATA')
const rejectNote = ref('')

const pendingVersion = computed<AdminReviewVersion | null>(() =>
    detail.value?.versions.find(v => v.status === 'PENDING') ?? null)
const redactionValid = computed(() => !pendingVersion.value || isRedactionOf(pendingVersion.value.body, redacted.value))
const totalPages = computed(() => (data.value ? Math.max(1, Math.ceil(data.value.total / data.value.size)) : 1))

async function load() {
    loading.value = true
    error.value = ''
    try {
        data.value = await adminRatingService.list({ status: status.value, type: type.value, page: page.value })
        emit('openCount', data.value.openTexts)
    } catch {
        error.value = 'Bewertungen konnten nicht geladen werden.'
    } finally {
        loading.value = false
    }
}

async function open(ratingId: string) {
    error.value = ''
    try {
        showDetail(await adminRatingService.detail(ratingId))
    } catch {
        error.value = 'Detail konnte nicht geladen werden.'
    }
}

function showDetail(d: AdminRatingDetail) {
    detail.value = d
    redacted.value = d.versions.find(v => v.status === 'PENDING')?.body ?? ''
    rejectNote.value = ''
}

async function decide(action: () => Promise<AdminRatingDetail>) {
    busy.value = true
    error.value = ''
    try {
        showDetail(await action())
        await load()
    } catch (e: any) {
        error.value = e.response?.data?.message || 'Aktion fehlgeschlagen.'
    } finally {
        busy.value = false
    }
}

const approve = (v: AdminReviewVersion) => decide(() => adminRatingService.approve(v.reviewId, null))
const approveRedacted = (v: AdminReviewVersion) => decide(() => adminRatingService.approve(v.reviewId, redacted.value))
const llmCheck = (v: AdminReviewVersion) => decide(() => adminRatingService.llmCheck(v.reviewId))
const takeRedaction = (text: string) => { redacted.value = text }
const segments = (v: AdminReviewVersion) => highlightSegments(v.body, v.llmCheck?.findings.map(f => f.excerpt) ?? [])
const REC_CLASS: Record<string, string> = {
    APPROVE: 'bg-green-900/50 text-green-300',
    APPROVE_WITH_REDACTION: 'bg-indigo-900/50 text-indigo-300',
    REJECT: 'bg-red-900/50 text-red-300',
    NEEDS_HUMAN: 'bg-amber-900/50 text-amber-300',
}
const reject = (v: AdminReviewVersion) => decide(() => adminRatingService.reject(v.reviewId, rejectReason.value, rejectNote.value))

watch([status, type], () => { page.value = 0; load() })
watch(page, load)
onMounted(load)

const date = (iso: string | null) => (iso ? new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '-')
const STATUS_CLASS: Record<string, string> = {
    PENDING: 'bg-amber-900/50 text-amber-300',
    APPROVED: 'bg-green-900/50 text-green-300',
    REJECTED: 'bg-red-900/50 text-red-300',
    SUPERSEDED: 'bg-gray-700 text-gray-400',
}
</script>

<template>
  <div class="space-y-4 text-gray-200">
    <details class="bg-gray-800 rounded-sm p-3 text-sm">
      <summary class="cursor-pointer font-medium">Regeln für die Freigabe</summary>
      <ol class="list-decimal pl-5 mt-2 space-y-1 text-gray-400">
        <li>Lob und Kritik nach denselben Regeln. Harte, sachliche Kritik bleibt stehen, auch an Hersteller und Werkstatt.</li>
        <li>Nur schwärzen, nie umformulieren: Namen, Kennzeichen, Kontaktdaten, Referral-Codes durch {{ REDACTION }} ersetzen.</li>
        <li>Ablehnen nur mit Grund: Schmähkritik, Werbung, anderes Modell, keine eigene Erfahrung. Der Fahrer bekommt den Grund per Mail.</li>
      </ol>
    </details>

    <div class="flex flex-wrap gap-2 items-center">
      <select v-model="status" class="bg-gray-800 rounded-sm px-2 py-1.5 text-sm" aria-label="Status">
        <option value="">Alle Status</option>
        <option value="PENDING">Offen</option>
        <option value="APPROVED">Freigegeben</option>
        <option value="REJECTED">Abgelehnt</option>
      </select>
      <select v-model="type" class="bg-gray-800 rounded-sm px-2 py-1.5 text-sm" aria-label="Typ">
        <option value="">Alle Typen</option>
        <option value="STARS_ONLY">Nur Sterne</option>
        <option value="SHORT">Zitat</option>
        <option value="LONG">Bericht</option>
      </select>
      <span v-if="data" class="text-sm text-gray-400">{{ data.total }} Bewertungen, {{ data.openTexts }} offene Texte</span>
    </div>

    <p v-if="error" class="text-sm text-red-400" role="alert">{{ error }}</p>
    <p v-if="loading" class="text-sm text-gray-500">Lädt …</p>

    <div class="grid gap-4 lg:grid-cols-2">
      <ul v-if="data" class="space-y-2">
        <li v-for="item in data.items" :key="item.ratingId">
          <button type="button" class="w-full text-left bg-gray-800 hover:bg-gray-700 rounded-sm p-3 space-y-1"
                  :class="{ 'ring-2 ring-indigo-500': detail?.ratingId === item.ratingId }" @click="open(item.ratingId)">
            <div class="flex items-center justify-between gap-2 text-sm">
              <span class="font-medium">{{ item.model }}</span>
              <span v-if="item.reviewStatus" class="px-2 py-0.5 rounded-full text-xs" :class="STATUS_CLASS[item.reviewStatus]">{{ item.reviewStatus }}</span>
            </div>
            <p class="text-xs text-gray-400">
              {{ item.rebuy }} · Ø {{ item.overall }} · {{ item.kmDriven.toLocaleString('de-DE') }} km · {{ item.logCount }} Logs · {{ item.type }} · Nutzer {{ item.userShort ?? 'anonym' }}
            </p>
            <p v-if="item.preview" class="text-sm text-gray-300 line-clamp-2">{{ item.preview }}</p>
          </button>
        </li>
        <li v-if="data.items.length === 0" class="text-sm text-gray-500">Nichts gefunden.</li>
        <li v-if="totalPages > 1" class="flex items-center gap-3 text-sm">
          <button type="button" class="px-3 py-1 bg-gray-800 rounded-sm disabled:opacity-40" :disabled="page === 0" @click="page--">Zurück</button>
          <span>{{ page + 1 }} / {{ totalPages }}</span>
          <button type="button" class="px-3 py-1 bg-gray-800 rounded-sm disabled:opacity-40" :disabled="page + 1 >= totalPages" @click="page++">Weiter</button>
        </li>
      </ul>

      <section v-if="detail" class="bg-gray-800 rounded-sm p-4 space-y-4 lg:sticky lg:top-4 self-start">
        <header class="space-y-1">
          <h3 class="font-semibold">{{ detail.model }}</h3>
          <p class="text-xs text-gray-400">
            {{ detail.kmDriven.toLocaleString('de-DE') }} km · {{ detail.logCount }} Logs · Nutzer {{ detail.userShort ?? 'anonym' }}
            <template v-if="detail.businessCar"> · Dienstwagen</template><template v-if="detail.anonymized"> · anonymisiert</template>
          </p>
          <p class="text-xs text-gray-400">
            Wiederkauf {{ detail.answers.rebuy }} · Alltag {{ detail.answers.dailyFit }} · Langstrecke {{ detail.answers.longDistance ?? '-' }} ·
            Software {{ detail.answers.software }} · Verarbeitung {{ detail.answers.buildQuality }} · Werkstatt {{ detail.answers.service ?? '-' }} ·
            Kosten {{ detail.answers.totalCost }}
          </p>
        </header>

        <article v-for="v in [...detail.versions].reverse()" :key="v.reviewId" class="border-t border-gray-700 pt-3 space-y-2">
          <div class="flex items-center justify-between text-xs">
            <span class="px-2 py-0.5 rounded-full" :class="STATUS_CLASS[v.status]">{{ v.status }}</span>
            <span class="text-gray-500">eingereicht {{ date(v.createdAt) }}<template v-if="v.reviewedAt">, entschieden {{ date(v.reviewedAt) }}</template></span>
          </div>
          <p class="whitespace-pre-line text-sm"><template v-for="(seg, i) in segments(v)" :key="i"><mark v-if="seg.hit" class="bg-amber-500/40 text-inherit rounded-sm">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template></template></p>
          <p v-if="v.publishedBody && v.publishedBody !== v.body" class="whitespace-pre-line text-sm text-green-300">Veröffentlicht: {{ v.publishedBody }}</p>
          <div v-if="v.pros.length || v.cons.length" class="text-xs text-gray-400">
            <span v-for="p in v.pros" :key="`p${p}`" class="mr-2">+ {{ p }}</span>
            <span v-for="c in v.cons" :key="`c${c}`" class="mr-2">- {{ c }}</span>
          </div>
          <p class="text-xs text-gray-500">
            Beleg: {{ v.proofKmDriven?.toLocaleString('de-DE') ?? '-' }} km, {{ v.proofLogCount ?? '-' }} Logs<template v-if="v.proofConsumption">, {{ v.proofConsumption }} kWh/100 km</template>
            · {{ v.body.length }} Zeichen<template v-if="v.deleteWithCar"> · löscht mit dem Auto</template>
          </p>
          <p v-if="v.rejectionReason" class="text-xs text-red-300">Abgelehnt: {{ v.rejectionReason }}<template v-if="v.rejectionNote"> - {{ v.rejectionNote }}</template></p>

          <!-- Mistral: nur Vorschlag, die Entscheidung bleibt beim Admin -->
          <div v-if="v.llmCheck" class="rounded-sm bg-gray-900 p-3 space-y-2 text-sm">
            <div class="flex flex-wrap items-center gap-2">
              <span class="px-2 py-0.5 rounded-full text-xs font-semibold" :class="REC_CLASS[v.llmCheck.recommendation]">{{ v.llmCheck.recommendation }}</span>
              <span class="text-xs text-gray-400">Sicherheit {{ v.llmCheck.confidence }}</span>
              <span v-if="v.llmCheck.meta" class="text-xs text-gray-500">{{ v.llmCheck.meta.model }}, {{ date(v.llmCheck.meta.checkedAt) }}, ${{ Number(v.llmCheck.meta.costUsd).toFixed(5) }}</span>
            </div>
            <p>{{ v.llmCheck.summary }}</p>
            <ul v-if="v.llmCheck.findings.length" class="space-y-1">
              <li v-for="(f, i) in v.llmCheck.findings" :key="i" class="text-xs">
                <span class="font-semibold">{{ f.category }} ({{ f.severity }})</span>: „{{ f.excerpt }}“ - {{ f.explanation }}
              </li>
            </ul>
            <p v-if="v.llmCheck.topics.length" class="text-xs text-gray-500">Themen: {{ v.llmCheck.topics.join(', ') }}</p>
            <div v-if="v.llmCheck.redacted_text && v.status === 'PENDING'" class="space-y-1">
              <p class="text-xs text-gray-400">Schwärzungsvorschlag:</p>
              <p class="whitespace-pre-line text-xs bg-gray-800 rounded-sm p-2">{{ v.llmCheck.redacted_text }}</p>
              <button type="button" class="px-3 py-1 rounded-sm bg-indigo-700 text-white text-xs" @click="takeRedaction(v.llmCheck.redacted_text)">Übernehmen</button>
            </div>
          </div>

          <div v-if="v.status === 'PENDING'" class="space-y-3 pt-2">
            <button type="button" class="px-4 py-2 rounded-sm bg-gray-700 hover:bg-gray-600 text-white text-sm disabled:opacity-50" :disabled="busy" @click="llmCheck(v)">
              {{ v.llmCheck ? 'Erneut von Mistral moderieren lassen' : 'Von Mistral moderieren lassen' }}
            </button>
            <button type="button" class="px-4 py-2 rounded-sm bg-green-600 hover:bg-green-700 text-white text-sm font-semibold disabled:opacity-50" :disabled="busy" @click="approve(v)">
              Freigeben
            </button>
            <div class="space-y-1">
              <label :for="`redact-${v.reviewId}`" class="text-xs text-gray-400">Mit Schwärzung freigeben (nur Stellen durch {{ REDACTION }} ersetzen)</label>
              <textarea :id="`redact-${v.reviewId}`" v-model="redacted" rows="4" class="w-full bg-gray-900 rounded-sm p-2 text-sm" />
              <p v-if="!redactionValid" class="text-xs text-red-400">Nur Schwärzungen erlaubt, sonst lehnt der Server ab.</p>
              <button type="button" class="px-4 py-2 rounded-sm bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold disabled:opacity-50"
                      :disabled="busy || !redactionValid || redacted === v.body" @click="approveRedacted(v)">
                Mit Schwärzung freigeben
              </button>
            </div>
            <div class="space-y-1">
              <div class="flex flex-wrap gap-2">
                <select v-model="rejectReason" class="bg-gray-900 rounded-sm px-2 py-1.5 text-sm" aria-label="Ablehnungsgrund">
                  <option v-for="r in REASONS" :key="r.value" :value="r.value">{{ r.label }}</option>
                </select>
                <input v-model="rejectNote" type="text" maxlength="1000" placeholder="Notiz an den Fahrer" class="flex-1 min-w-0 bg-gray-900 rounded-sm px-2 py-1.5 text-sm" />
              </div>
              <button type="button" class="px-4 py-2 rounded-sm bg-red-700 hover:bg-red-800 text-white text-sm font-semibold disabled:opacity-50"
                      :disabled="busy || (rejectReason === 'OTHER' && !rejectNote.trim())" @click="reject(v)">
                Ablehnen
              </button>
            </div>
          </div>
        </article>
        <p v-if="detail.versions.length === 0" class="text-sm text-gray-500">Nur Sterne, kein Text.</p>
      </section>
    </div>
  </div>
</template>
