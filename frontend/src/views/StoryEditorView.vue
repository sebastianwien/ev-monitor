<template>
  <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-10 pb-28">
    <div class="bg-white dark:bg-gray-900 border-y md:border border-gray-200 dark:border-gray-800 md:rounded-xl md:shadow-sm px-4 md:px-6 py-6 md:py-8">
    <div v-if="loading" class="text-center py-16 text-gray-500 dark:text-gray-400">
      {{ t('common.loading') }}
    </div>

    <template v-else-if="story">
      <nav class="mb-4 text-sm">
        <RouterLink to="/stories" class="text-emerald-600 dark:text-emerald-400 hover:underline inline-flex items-center gap-1">
          <ArrowLeftIcon class="w-4 h-4" aria-hidden="true" />
          {{ t('stories.back_to_stories') }}
        </RouterLink>
      </nav>

      <div class="flex items-center gap-2 mb-6">
        <span class="text-xs font-semibold px-2 py-1 rounded-full"
              :class="story.status === 'PUBLISHED'
                ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300'
                : 'bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-300'">
          {{ story.status === 'PUBLISHED' ? t('stories.status_published') : t('stories.status_draft') }}
        </span>
        <RouterLink v-if="story.status === 'PUBLISHED'" :to="`/stories/${story.slug}`"
                    class="text-sm text-emerald-600 dark:text-emerald-400 hover:underline">
          {{ t('stories.view_public') }}
        </RouterLink>
      </div>

      <!-- Meta -->
      <div class="space-y-4 mb-8">
        <div>
          <label for="story-title" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {{ t('stories.title_label') }}
          </label>
          <input id="story-title" v-model="form.title" type="text" maxlength="160"
                 :placeholder="t('stories.title_placeholder')"
                 class="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-gray-900 dark:text-gray-100 focus:ring-emerald-500 focus:border-emerald-500" />
        </div>
        <div>
          <label for="story-summary" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {{ t('stories.summary_label') }}
          </label>
          <textarea id="story-summary" v-model="form.summary" rows="2" maxlength="300"
                    :placeholder="t('stories.summary_placeholder')"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-gray-900 dark:text-gray-100 focus:ring-emerald-500 focus:border-emerald-500" />
        </div>
        <div>
          <label for="story-language" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            {{ t('stories.language_label') }}
          </label>
          <select id="story-language" v-model="form.language"
                  class="rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-gray-900 dark:text-gray-100 focus:ring-emerald-500 focus:border-emerald-500">
            <option value="de">Deutsch</option>
            <option value="en">English</option>
            <option value="nb">Norsk</option>
            <option value="sv">Svenska</option>
          </select>
        </div>
      </div>

      <!-- Blocks -->
      <div class="space-y-4">
        <div v-for="(block, index) in blocks" :key="block.key"
             class="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 p-4">
          <div class="flex items-center gap-1 mb-3">
            <component :is="block.type === 'text' ? DocumentTextIcon : ChartBarIcon"
                       class="w-5 h-5 text-gray-400 shrink-0" aria-hidden="true" />
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ block.type === 'text' ? t('stories.block_text') : t('stories.block_trip') }}
            </span>
            <div class="ml-auto flex items-center">
              <button type="button" class="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 disabled:opacity-30"
                      :disabled="index === 0" :aria-label="t('stories.move_up')" @click="moveBlock(index, -1)">
                <ChevronUpIcon class="w-5 h-5" aria-hidden="true" />
              </button>
              <button type="button" class="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 disabled:opacity-30"
                      :disabled="index === blocks.length - 1" :aria-label="t('stories.move_down')" @click="moveBlock(index, 1)">
                <ChevronDownIcon class="w-5 h-5" aria-hidden="true" />
              </button>
              <button type="button" class="p-1.5 text-gray-400 hover:text-red-600"
                      :aria-label="t('stories.remove_block')" @click="removeBlock(index)">
                <TrashIcon class="w-5 h-5" aria-hidden="true" />
              </button>
            </div>
          </div>

          <!-- Text block -->
          <template v-if="block.type === 'text'">
            <textarea v-model="block.markdown" rows="6"
                      :placeholder="t('stories.text_placeholder')"
                      class="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-gray-900 dark:text-gray-100 font-mono text-sm focus:ring-emerald-500 focus:border-emerald-500" />
            <p class="text-xs text-gray-400 mt-1">{{ t('stories.markdown_hint') }}</p>
          </template>

          <!-- Trip block -->
          <template v-else>
            <div class="grid gap-3 sm:grid-cols-2 mb-3">
              <div v-if="cars.length > 1">
                <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('stories.select_car') }}</label>
                <select v-model="block.carId" class="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100"
                        @change="loadTrips(block.carId)">
                  <option v-for="car in cars" :key="car.id" :value="car.id">{{ carLabel(car) }}</option>
                </select>
              </div>
              <div>
                <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('stories.select_trip') }}</label>
                <select v-model="block.tripId" class="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100">
                  <option disabled value="">{{ tripsFor(block.carId).length ? t('stories.select_trip') : t('stories.no_trips') }}</option>
                  <option v-for="trip in tripsFor(block.carId)" :key="trip.id" :value="trip.id">{{ tripLabel(trip) }}</option>
                </select>
              </div>
            </div>
            <div class="mb-3">
              <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">{{ t('stories.trip_label_label') }}</label>
              <input v-model="block.label" type="text" maxlength="120"
                     :placeholder="t('stories.trip_label_placeholder')"
                     class="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100" />
            </div>
            <TripStatsWidget v-if="block.stats" :block="block" />
            <p v-else class="text-xs text-gray-400">{{ t('stories.stats_after_save') }}</p>
          </template>
        </div>
      </div>

      <!-- Add block -->
      <div class="flex gap-2 mt-4">
        <button type="button"
                class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 dark:border-gray-700 px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:border-emerald-400"
                @click="addTextBlock">
          <DocumentTextIcon class="w-4 h-4" aria-hidden="true" />
          {{ t('stories.add_text_block') }}
        </button>
        <button type="button"
                class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 dark:border-gray-700 px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:border-emerald-400"
                @click="addTripBlock">
          <ChartBarIcon class="w-4 h-4" aria-hidden="true" />
          {{ t('stories.add_trip_block') }}
        </button>
      </div>

      <p v-if="errorMessage" class="mt-4 text-sm text-red-600 dark:text-red-400" role="alert">{{ errorMessage }}</p>

      <!-- Sticky action bar -->
      <div class="fixed bottom-0 inset-x-0 border-t border-gray-200 dark:border-gray-800 bg-white/95 dark:bg-gray-950/95 backdrop-blur px-4 py-3">
        <div class="max-w-3xl mx-auto flex items-center gap-2">
          <button type="button"
                  class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold px-4 py-2 disabled:opacity-50"
                  :disabled="saving" @click="save">
            {{ saving ? t('stories.saving') : t('stories.save') }}
          </button>
          <span v-if="savedRecently" class="text-sm text-emerald-600 dark:text-emerald-400">{{ t('stories.saved') }}</span>
          <div class="ml-auto flex items-center gap-2">
            <button v-if="story.status === 'DRAFT'" type="button"
                    class="rounded-lg border border-emerald-600 text-emerald-700 dark:text-emerald-400 text-sm font-semibold px-3 py-2 hover:bg-emerald-50 dark:hover:bg-emerald-950 disabled:opacity-50"
                    :disabled="saving || blocks.length === 0" @click="publish">
              {{ t('stories.publish') }}
            </button>
            <button v-else type="button"
                    class="rounded-lg border border-gray-300 dark:border-gray-700 text-sm font-medium text-gray-700 dark:text-gray-300 px-3 py-2"
                    :disabled="saving" @click="unpublish">
              {{ t('stories.unpublish') }}
            </button>
            <button type="button" class="p-2 text-gray-400 hover:text-red-600" :aria-label="t('stories.delete')"
                    @click="deleteStory">
              <TrashIcon class="w-5 h-5" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowLeftIcon, ChartBarIcon, ChevronDownIcon, ChevronUpIcon, DocumentTextIcon, TrashIcon,
} from '@heroicons/vue/24/outline'
import api from '../api/axios'
import { storyService, type TripStory, type TripStatsSnapshot } from '../api/storyService'
import { carService, type Car } from '../api/carService'
import TripStatsWidget from '../components/stories/TripStatsWidget.vue'

interface TripOption {
  id: string
  tripStartedAt: string | null
  distanceKm: number | null
}

interface EditorBlock {
  key: number
  type: 'text' | 'tripStats'
  markdown?: string
  tripId?: string
  label?: string
  carId?: string
  stats?: TripStatsSnapshot
}

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const story = ref<TripStory | null>(null)
const loading = ref(true)
const saving = ref(false)
const savedRecently = ref(false)
const errorMessage = ref('')
const blocks = ref<EditorBlock[]>([])
const cars = ref<Car[]>([])
const tripsByCar = reactive<Record<string, TripOption[]>>({})
const form = reactive({ title: '', summary: '', language: 'de' })

let blockKey = 0

onMounted(async () => {
  try {
    const [loadedStory, loadedCars] = await Promise.all([
      storyService.getStory(String(route.params.id)),
      carService.getCars(),
    ])
    story.value = loadedStory
    cars.value = loadedCars
    form.title = loadedStory.title
    form.summary = loadedStory.summary ?? ''
    form.language = loadedStory.language
    blocks.value = loadedStory.blocks.map(b => ({ key: blockKey++, ...b }))
    if (loadedCars.length > 0) {
      await loadTrips(loadedCars[0].id)
      blocks.value.forEach(b => { if (b.type === 'tripStats' && !b.carId) b.carId = loadedCars[0].id })
    }
  } catch {
    story.value = null
    router.replace('/stories')
  } finally {
    loading.value = false
  }
})

async function loadTrips(carId?: string) {
  if (!carId || tripsByCar[carId]) return
  try {
    const response = await api.get(`/trips?carId=${carId}`)
    tripsByCar[carId] = response.data
  } catch {
    tripsByCar[carId] = []
  }
}

function tripsFor(carId?: string): TripOption[] {
  return carId ? tripsByCar[carId] ?? [] : []
}

function carLabel(car: Car): string {
  return `${car.brand} ${car.model}`.replace(/_/g, ' ')
}

function tripLabel(trip: TripOption): string {
  const date = trip.tripStartedAt
      ? new Date(trip.tripStartedAt).toLocaleDateString(locale.value, { day: '2-digit', month: '2-digit', year: 'numeric' })
      : '?'
  const km = trip.distanceKm != null ? `${trip.distanceKm.toLocaleString(locale.value, { maximumFractionDigits: 1 })} km` : ''
  return `${date} · ${km}`
}

function addTextBlock() {
  blocks.value.push({ key: blockKey++, type: 'text', markdown: '' })
}

function addTripBlock() {
  const carId = cars.value[0]?.id
  blocks.value.push({ key: blockKey++, type: 'tripStats', tripId: '', label: '', carId })
  if (carId) loadTrips(carId)
}

function moveBlock(index: number, direction: number) {
  const target = index + direction
  const [moved] = blocks.value.splice(index, 1)
  blocks.value.splice(target, 0, moved)
}

function removeBlock(index: number) {
  blocks.value.splice(index, 1)
}

async function save(): Promise<boolean> {
  if (!story.value) return false
  errorMessage.value = ''
  saving.value = true
  try {
    const updated = await storyService.updateStory(story.value.id, {
      title: form.title,
      summary: form.summary || null,
      language: form.language,
      blocks: blocks.value
          .filter(b => b.type === 'text' ? (b.markdown ?? '').trim().length > 0 : !!b.tripId)
          .map(b => b.type === 'text'
              ? { type: 'text', markdown: b.markdown }
              : { type: 'tripStats', tripId: b.tripId, label: b.label || undefined }),
    })
    story.value = updated
    blocks.value = updated.blocks.map(b => ({ key: blockKey++, carId: findCarIdForBlock(), ...b }))
    savedRecently.value = true
    setTimeout(() => { savedRecently.value = false }, 3000)
    return true
  } catch {
    errorMessage.value = t('stories.error_save')
    return false
  } finally {
    saving.value = false
  }
}

function findCarIdForBlock(): string | undefined {
  return cars.value[0]?.id
}

async function publish() {
  if (!story.value || !(await save())) return
  try {
    story.value = await storyService.publishStory(story.value.id)
  } catch {
    errorMessage.value = t('stories.error_save')
  }
}

async function unpublish() {
  if (!story.value) return
  try {
    story.value = await storyService.unpublishStory(story.value.id)
  } catch {
    errorMessage.value = t('stories.error_save')
  }
}

async function deleteStory() {
  if (!story.value) return
  if (!window.confirm(t('stories.confirm_delete'))) return
  await storyService.deleteStory(story.value.id)
  router.push('/stories')
}
</script>
