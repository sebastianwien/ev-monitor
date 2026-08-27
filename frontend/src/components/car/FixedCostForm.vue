<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, CheckIcon } from '@heroicons/vue/24/outline'
import { INCOME_CATEGORIES, isIncomeCategory, toInputAmount, toSignedAmount } from '../../utils/fixedCostAmount'
import {
  fixedCostService,
  type FixedCost,
  type FixedCostRequest,
  type FixedCostCategory,
  type FixedCostRecurrence,
} from '../../api/fixedCostService'

const props = defineProps<{
  carId: string
  /** Existing item to edit; null/undefined -> create mode. */
  edit?: FixedCost | null
}>()

const emit = defineEmits<{
  saved: []
  cancel: []
}>()

const { t } = useI18n()

const COST_CATEGORIES: FixedCostCategory[] = [
  'INSURANCE', 'TAX', 'TOLL', 'CLEANING', 'MAINTENANCE',
  'LEASING', 'FINANCING', 'TIRES', 'TUNING', 'OTHER',
]
const RECURRENCES: FixedCostRecurrence[] = ['ONE_TIME', 'MONTHLY', 'QUARTERLY', 'YEARLY']

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

const saving = ref(false)
const submitted = ref(false)
const error = ref<string | null>(null)

const form = ref<FixedCostRequest>(props.edit
  ? {
      description: props.edit.description,
      amount: toInputAmount(props.edit.amount, props.edit.category),
      category: props.edit.category,
      recurrence: props.edit.recurrence,
      date: props.edit.date,
      startDate: props.edit.startDate,
      endDate: props.edit.endDate,
    }
  : { description: '', amount: 0, category: 'OTHER', recurrence: 'ONE_TIME', date: today(), startDate: today(), endDate: null })

const isOneTime = computed(() => form.value.recurrence === 'ONE_TIME')
const isIncome = computed(() => isIncomeCategory(form.value.category))

const descriptionInvalid = computed(() => submitted.value && !form.value.description.trim())
const dateInvalid = computed(() => submitted.value && isOneTime.value && !form.value.date)
const startDateInvalid = computed(() => submitted.value && !isOneTime.value && !form.value.startDate)

const formValid = computed(() => {
  if (!form.value.description.trim()) return false
  if (isOneTime.value && !form.value.date) return false
  if (!isOneTime.value && !form.value.startDate) return false
  return true
})

async function save() {
  submitted.value = true
  if (!formValid.value) return
  saving.value = true
  error.value = null
  try {
    const payload: FixedCostRequest = {
      ...form.value,
      amount: toSignedAmount(form.value.amount, form.value.category),
      date: isOneTime.value ? form.value.date : null,
      startDate: isOneTime.value ? null : form.value.startDate,
      endDate: isOneTime.value ? null : form.value.endDate,
    }
    if (props.edit) {
      await fixedCostService.update(props.edit.id, payload)
    } else {
      await fixedCostService.create(props.carId, payload)
    }
    emit('saved')
  } catch {
    error.value = t('fixed_costs.err_save')
  } finally {
    saving.value = false
  }
}

const inputBase = 'w-full text-sm border rounded-sm px-2.5 py-1.5 bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-1'
const inputNormal = `${inputBase} border-gray-300 dark:border-gray-600 focus:ring-indigo-400`
const inputError = `${inputBase} border-red-400 dark:border-red-500 focus:ring-red-400`
</script>

<template>
  <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-sm p-4 shadow-sm">
    <p v-if="error" class="text-xs text-red-600 dark:text-red-400 mb-2">{{ error }}</p>
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
          step="0.01"
          aria-describedby="fixed-cost-amount-hint"
          :class="inputNormal"
        />
        <p
          id="fixed-cost-amount-hint"
          class="text-xs mt-0.5"
          :class="isIncome ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'"
        >
          {{ isIncome ? t('fixed_costs.hint_income_active') : t('fixed_costs.hint_income') }}
        </p>
      </div>

      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">{{ t('fixed_costs.label_category') }}</label>
        <select v-model="form.category" :class="inputNormal">
          <optgroup :label="t('fixed_costs.group_costs')">
            <option v-for="cat in COST_CATEGORIES" :key="cat" :value="cat">
              {{ t(`fixed_costs.category_${cat}`) }}
            </option>
          </optgroup>
          <optgroup :label="t('fixed_costs.group_income')">
            <option v-for="cat in INCOME_CATEGORIES" :key="cat" :value="cat">
              {{ t(`fixed_costs.category_${cat}`) }}
            </option>
          </optgroup>
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
        @click="emit('cancel')"
        class="flex items-center gap-1 text-xs bg-gray-100 dark:bg-gray-600 text-gray-700 dark:text-gray-200 px-3 py-1.5 rounded-sm font-medium hover:bg-gray-200 dark:hover:bg-gray-500 transition btn-3d"
      >
        <XMarkIcon class="h-3.5 w-3.5" />
        {{ t('fixed_costs.cancel') }}
      </button>
    </div>
  </div>
</template>
