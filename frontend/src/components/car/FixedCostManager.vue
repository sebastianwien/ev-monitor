<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusIcon, PencilIcon, TrashIcon, XMarkIcon, CheckIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import {
  fixedCostService,
  type FixedCost,
  type FixedCostRequest,
  type FixedCostCategory,
  type FixedCostRecurrence,
} from '../../api/fixedCostService'

const props = defineProps<{ carId: string }>()
const { t } = useI18n()

const CATEGORIES: FixedCostCategory[] = ['INSURANCE', 'TAX', 'TOLL', 'CLEANING', 'MAINTENANCE', 'OTHER']
const RECURRENCES: FixedCostRecurrence[] = ['ONE_TIME', 'MONTHLY', 'QUARTERLY', 'YEARLY']

const items = ref<FixedCost[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const showForm = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const confirmDeleteId = ref<string | null>(null)
const submitted = ref(false)

const form = ref<FixedCostRequest>({
  description: '',
  amount: 0,
  category: 'OTHER',
  recurrence: 'ONE_TIME',
  date: null,
  startDate: null,
  endDate: null,
})

const isOneTime = computed(() => form.value.recurrence === 'ONE_TIME')

const descriptionInvalid = computed(() => submitted.value && !form.value.description.trim())
const dateInvalid = computed(() => submitted.value && isOneTime.value && !form.value.date)
const startDateInvalid = computed(() => submitted.value && !isOneTime.value && !form.value.startDate)

const formValid = computed(() => {
  if (!form.value.description.trim()) return false
  if (isOneTime.value && !form.value.date) return false
  if (!isOneTime.value && !form.value.startDate) return false
  return true
})

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
  submitted.value = false
  form.value = { description: '', amount: 0, category: 'OTHER', recurrence: 'ONE_TIME', date: null, startDate: null, endDate: null }
  showForm.value = true
}

function openEdit(item: FixedCost) {
  editingId.value = item.id
  submitted.value = false
  form.value = {
    description: item.description,
    amount: item.amount,
    category: item.category,
    recurrence: item.recurrence,
    date: item.date,
    startDate: item.startDate,
    endDate: item.endDate,
  }
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
  submitted.value = false
}

async function save() {
  submitted.value = true
  if (!formValid.value) return
  saving.value = true
  error.value = null
  try {
    const payload: FixedCostRequest = {
      ...form.value,
      date: isOneTime.value ? form.value.date : null,
      startDate: isOneTime.value ? null : form.value.startDate,
      endDate: isOneTime.value ? null : form.value.endDate,
    }
    if (editingId.value) {
      await fixedCostService.update(editingId.value, payload)
    } else {
      await fixedCostService.create(props.carId, payload)
    }
    showForm.value = false
    editingId.value = null
    submitted.value = false
    await load()
  } catch {
    error.value = t('fixed_costs.err_save')
  } finally {
    saving.value = false
  }
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

const inputBase = 'w-full text-sm border rounded-sm px-2.5 py-1.5 bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1'
const inputNormal = `${inputBase} border-gray-300 dark:border-gray-600 focus:ring-indigo-400`
const inputError = `${inputBase} border-red-400 dark:border-red-500 focus:ring-red-400`

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

    <!-- Form -->
    <div v-if="showForm" class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-sm p-4 mb-3 shadow-sm">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div class="sm:col-span-2">
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
            {{ t('fixed_costs.label_description') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.description"
            type="text"
            maxlength="255"
            required
            :class="descriptionInvalid ? inputError : inputNormal"
          />
          <p v-if="descriptionInvalid" class="text-xs text-red-500 mt-0.5">{{ t('fixed_costs.err_required') }}</p>
        </div>

        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('fixed_costs.label_amount') }}</label>
          <input
            v-model.number="form.amount"
            type="number"
            min="0"
            step="0.01"
            :class="inputNormal"
          />
        </div>

        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('fixed_costs.label_category') }}</label>
          <select v-model="form.category" :class="inputNormal">
            <option v-for="cat in CATEGORIES" :key="cat" :value="cat">
              {{ t(`fixed_costs.category_${cat}`) }}
            </option>
          </select>
        </div>

        <div>
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('fixed_costs.label_recurrence') }}</label>
          <select v-model="form.recurrence" :class="inputNormal">
            <option v-for="rec in RECURRENCES" :key="rec" :value="rec">
              {{ t(`fixed_costs.recurrence_${rec}`) }}
            </option>
          </select>
        </div>

        <!-- ONE_TIME: single date -->
        <div v-if="isOneTime">
          <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
            {{ t('fixed_costs.label_date') }} <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.date"
            type="date"
            required
            :class="dateInvalid ? inputError : inputNormal"
          />
          <p v-if="dateInvalid" class="text-xs text-red-500 mt-0.5">{{ t('fixed_costs.err_required') }}</p>
        </div>

        <!-- Recurring: start + end date -->
        <template v-else>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
              {{ t('fixed_costs.label_start_date') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="form.startDate"
              type="date"
              required
              :class="startDateInvalid ? inputError : inputNormal"
            />
            <p v-if="startDateInvalid" class="text-xs text-red-500 mt-0.5">{{ t('fixed_costs.err_required') }}</p>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('fixed_costs.label_end_date') }}</label>
            <input
              v-model="form.endDate"
              type="date"
              :class="inputNormal"
            />
          </div>
        </template>
      </div>

      <div class="flex gap-2 mt-3">
        <button
          v-haptic
          @click="save"
          :disabled="saving"
          class="flex items-center gap-1 text-xs bg-green-100 dark:bg-green-700 text-green-800 dark:text-white px-3 py-1.5 rounded-sm font-medium hover:bg-green-200 dark:hover:bg-green-600 transition btn-3d disabled:opacity-50"
        >
          <CheckIcon class="h-3.5 w-3.5" />
          {{ t('fixed_costs.save') }}
        </button>
        <button
          v-haptic
          @click="cancelForm"
          class="flex items-center gap-1 text-xs bg-gray-100 dark:bg-gray-600 text-gray-700 dark:text-gray-200 px-3 py-1.5 rounded-sm font-medium hover:bg-gray-200 dark:hover:bg-gray-500 transition btn-3d"
        >
          <XMarkIcon class="h-3.5 w-3.5" />
          {{ t('fixed_costs.cancel') }}
        </button>
      </div>
    </div>

    <!-- List -->
    <div v-if="loading" class="text-xs text-gray-400 py-2">...</div>
    <p v-else-if="items.length === 0 && !showForm" class="text-xs text-gray-400 dark:text-gray-500 py-1">
      {{ t('fixed_costs.empty') }}
    </p>
    <ul v-else class="space-y-1.5">
      <li
        v-for="item in items"
        :key="item.id"
        class="flex items-start justify-between gap-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-sm px-3 py-2 text-xs"
      >
        <div class="flex-1 min-w-0">
          <p class="font-medium text-gray-800 dark:text-gray-200 truncate">{{ item.description }}</p>
          <p class="text-gray-500 dark:text-gray-400 mt-0.5">
            {{ t(`fixed_costs.category_${item.category}`) }} &middot;
            {{ t(`fixed_costs.recurrence_${item.recurrence}`) }} &middot;
            <span class="font-medium text-gray-700 dark:text-gray-300">{{ formatAmount(item.amount) }}</span>
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
      </li>
    </ul>
  </div>
</template>
