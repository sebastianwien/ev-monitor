<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <PublicNav />

    <main class="max-w-3xl mx-auto md:px-4 py-6 md:py-8">
      <!-- Breadcrumb -->
      <nav class="px-4 md:px-0 text-sm text-gray-500 dark:text-gray-400 mb-4">
        <a href="/modelle" class="hover:text-gray-700 dark:hover:text-gray-200">{{ t('rankings.breadcrumb_models') }}</a>
        <span class="mx-2">›</span>
        <span class="text-gray-900 dark:text-gray-100">{{ t('rankings.breadcrumb_current') }}</span>
      </nav>

      <!-- Header -->
      <div class="px-4 md:px-0 mb-6">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">{{ t('rankings.header') }}</h1>
        <p class="text-gray-500 dark:text-gray-400">{{ t('rankings.subtitle') }}</p>
      </div>

      <!-- Efficiency Section -->
      <section class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 overflow-hidden mb-6">
        <!-- Section header -->
        <div class="px-4 py-4 border-b border-gray-100 dark:border-gray-700">
          <div class="flex items-center gap-2 mb-3">
            <BoltIcon class="h-5 w-5 text-yellow-500" />
            <h2 class="font-semibold text-gray-900 dark:text-gray-100">{{ t('rankings.section_title') }}</h2>
          </div>
          <!-- Kategorie-Tabs -->
          <div v-if="categories.length > 0" class="flex flex-wrap gap-2">
            <button
              @click="selectedCategory = null"
              class="text-xs px-3 py-1.5 rounded-full font-medium transition-colors"
              :class="selectedCategory === null
                ? 'bg-green-600 text-white'
                : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'"
            >
              {{ t('rankings.filter_all') }}
            </button>
            <button
              v-for="cat in categories"
              :key="cat.key"
              @click="selectedCategory = cat.key"
              class="text-xs px-3 py-1.5 rounded-full font-medium transition-colors"
              :class="selectedCategory === cat.key
                ? 'bg-green-600 text-white'
                : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'"
            >
              {{ cat.displayName }}
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-green-600"></div>
        </div>

        <!-- Error -->
        <div v-else-if="error" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
          {{ t('rankings.error') }}
        </div>

        <!-- Empty -->
        <div v-else-if="models.length === 0" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
          {{ t('rankings.empty') }}
        </div>

        <!-- Model list -->
        <ul v-else>
          <li
            v-for="(model, index) in models"
            :key="model.model"
            class="border-b last:border-b-0 border-gray-100 dark:border-gray-700"
          >
            <a
              :href="`/modelle/${model.brandDisplayName}/${model.modelUrlSlug}`"
              class="flex items-center gap-3 px-4 py-4 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            >
              <!-- Rank badge -->
              <span
                class="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold"
                :class="rankBadgeClass(index)"
              >
                {{ index + 1 }}
              </span>

              <!-- Name + log count -->
              <div class="flex-1 min-w-0">
                <div class="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {{ model.modelDisplayName }}
                </div>
                <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">
                  {{ t('rankings.log_count', { n: model.logCount.toLocaleString() }) }}
                </div>
              </div>

              <!-- Consumption + WLTP -->
              <div class="flex-shrink-0 text-right">
                <div class="font-semibold text-green-700 dark:text-green-400">
                  {{ formatConsumption(model.avgConsumptionKwhPer100km!) }}
                </div>
                <div v-if="model.minWltpConsumptionKwhPer100km" class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">
                  WLTP {{ formatConsumption(model.minWltpConsumptionKwhPer100km) }}
                </div>
              </div>

              <ChevronRightIcon class="flex-shrink-0 h-4 w-4 text-gray-400 dark:text-gray-500" />
            </a>
          </li>
        </ul>
      </section>

      <!-- Weitere Rankings - Platzhalter -->
      <div class="bg-white dark:bg-gray-800 md:rounded-2xl md:border-x border-t md:border-b border-gray-200 dark:border-gray-700 px-4 py-5 text-center">
        <p class="text-sm text-gray-400 dark:text-gray-500">{{ t('rankings.coming_soon') }}</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import { BoltIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import PublicNav from '../components/shared/PublicNav.vue'
import { getMostEfficientModels, getCategories, type TopModelPreview, type VehicleCategoryItem } from '../api/publicModelService'
import { useLocaleFormat } from '../composables/useLocaleFormat'

const { t } = useI18n()
const { formatConsumption } = useLocaleFormat()

useHead({
    title: 'EV Rankings - EV Monitor',
    meta: [
        { name: 'robots', content: 'noindex, nofollow' }
    ]
})

const allModels = ref<TopModelPreview[]>([])
const loading = ref(true)
const error = ref(false)
const categories = ref<VehicleCategoryItem[]>([])
const selectedCategory = ref<string | null>(null)

const models = computed(() => {
    if (selectedCategory.value === null) return allModels.value
    return allModels.value.filter(m => m.category === selectedCategory.value)
})

function rankBadgeClass(index: number): string {
    if (index === 0) return 'bg-yellow-100 dark:bg-yellow-900/40 text-yellow-700 dark:text-yellow-300'
    if (index === 1) return 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300'
    if (index === 2) return 'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300'
    return 'bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
}

onMounted(async () => {
    try {
        const [modelData, cats] = await Promise.all([getMostEfficientModels(10), getCategories()])
        allModels.value = modelData
        categories.value = cats
    } catch {
        error.value = true
    } finally {
        loading.value = false
    }
})
</script>
