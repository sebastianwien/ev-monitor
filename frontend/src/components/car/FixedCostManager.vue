<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon, PencilIcon, TrashIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { fixedCostService, type FixedCost } from '../../api/fixedCostService'
import FixedCostForm from './FixedCostForm.vue'

const props = defineProps<{ carId: string }>()
const { t } = useI18n()

const items = ref<FixedCost[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const creating = ref(false)
const editingId = ref<string | null>(null)
const confirmDeleteId = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    items.value = await fixedCostService.list(props.carId)
  } catch {
    error.value = t('fixed_costs.err_load')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  creating.value = true
}

function openEdit(item: FixedCost) {
  creating.value = false
  editingId.value = item.id
}

function closeForm() {
  creating.value = false
  editingId.value = null
}

async function onSaved() {
  closeForm()
  await load()
}

function requestDelete(id: string) {
  confirmDeleteId.value = id
}

function cancelDelete() {
  confirmDeleteId.value = null
}

async function confirmDelete() {
  if (!confirmDeleteId.value) return
  error.value = null
  const id = confirmDeleteId.value
  confirmDeleteId.value = null
  try {
    await fixedCostService.remove(id)
    await load()
  } catch {
    error.value = t('fixed_costs.err_delete')
  }
}

function formatAmount(amount: number) {
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(amount)
}

onMounted(load)
</script>

<template>
  <div class="mt-4">
    <div class="flex items-center justify-between mb-3">
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ t('fixed_costs.title') }}</h3>
      <button
        v-haptic
        @click="openCreate"
        class="flex items-center gap-1 text-xs bg-indigo-100 dark:bg-indigo-700 text-indigo-800 dark:text-white px-2.5 py-1.5 rounded-sm font-medium hover:bg-indigo-200 dark:hover:bg-indigo-600 transition btn-3d"
      >
        <PlusIcon class="h-3.5 w-3.5" />
        {{ t('fixed_costs.add') }}
      </button>
    </div>

    <p v-if="error" class="text-xs text-red-600 dark:text-red-400 mb-2">{{ error }}</p>

    <!-- Inline Delete Confirm -->
    <div v-if="confirmDeleteId" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-sm px-3 py-2.5 mb-3 flex items-center justify-between gap-3">
      <div class="flex items-center gap-2 text-xs text-red-700 dark:text-red-300">
        <ExclamationTriangleIcon class="h-4 w-4 shrink-0" />
        {{ t('fixed_costs.delete_confirm') }}
      </div>
      <div class="flex gap-2 shrink-0">
        <button
          v-haptic
          @click="confirmDelete"
          class="text-xs bg-red-600 text-white px-2.5 py-1 rounded-sm font-medium hover:bg-red-700 transition btn-3d"
        >
          {{ t('fixed_costs.delete') }}
        </button>
        <button
          v-haptic
          @click="cancelDelete"
          class="text-xs bg-gray-100 dark:bg-gray-600 text-gray-700 dark:text-gray-200 px-2.5 py-1 rounded-sm font-medium hover:bg-gray-200 dark:hover:bg-gray-500 transition btn-3d"
        >
          {{ t('fixed_costs.cancel') }}
        </button>
      </div>
    </div>

    <!-- Create Form (top) -->
    <FixedCostForm
      v-if="creating"
      :car-id="carId"
      class="mb-3"
      @saved="onSaved"
      @cancel="closeForm"
    />

    <!-- List -->
    <div v-if="loading" class="text-xs text-gray-400 py-2">...</div>
    <p v-else-if="items.length === 0 && !creating" class="text-xs text-gray-400 dark:text-gray-500 py-1">
      {{ t('fixed_costs.empty') }}
    </p>
    <ul v-else class="space-y-1.5">
      <li v-for="item in items" :key="item.id">
        <!-- Edit form renders in-place of the row so it appears exactly where the user tapped -->
        <FixedCostForm
          v-if="editingId === item.id"
          :car-id="carId"
          :edit="item"
          @saved="onSaved"
          @cancel="closeForm"
        />
        <div
          v-else
          class="flex items-start justify-between gap-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-sm px-3 py-2 text-xs"
        >
          <div class="flex-1 min-w-0">
            <p class="font-medium text-gray-800 dark:text-gray-200 truncate">{{ item.description }}</p>
            <p class="text-gray-500 dark:text-gray-400 mt-0.5">
              {{ t(`fixed_costs.category_${item.category}`) }} &middot;
              {{ t(`fixed_costs.recurrence_${item.recurrence}`) }} &middot;
              <span
                class="font-medium"
                :class="item.amount < 0 ? 'text-green-600 dark:text-green-400' : 'text-gray-700 dark:text-gray-300'"
              >{{ formatAmount(item.amount) }}</span>
            </p>
            <p v-if="item.date" class="text-gray-400 dark:text-gray-500 mt-0.5">{{ item.date }}</p>
            <p v-else-if="item.startDate" class="text-gray-400 dark:text-gray-500 mt-0.5">
              {{ item.startDate }}<template v-if="item.endDate"> - {{ item.endDate }}</template>
            </p>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <button
              v-haptic
              @click="openEdit(item)"
              class="p-1 text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition"
              :aria-label="t('fixed_costs.edit')"
            >
              <PencilIcon class="h-3.5 w-3.5" />
            </button>
            <button
              v-haptic
              @click="requestDelete(item.id)"
              class="p-1 text-gray-400 hover:text-red-600 dark:hover:text-red-400 transition"
              :aria-label="t('fixed_costs.delete')"
            >
              <TrashIcon class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
