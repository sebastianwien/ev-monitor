<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import { vehicleRequestService } from '../../api/vehicleRequestService'
import { useAuthStore } from '../../stores/auth'

/**
 * Meldeweg "Fehlt etwas?" beim Auto-Anlegen: statt eine freie Kapazität einzutragen, meldet der Nutzer
 * die fehlende Variante, das Modell oder den Hersteller. Marke und Modell aus der laufenden Auswahl
 * gehen mit, die Kontakt-Mail ist aus dem Konto vorbelegt und änderbar.
 */
const props = defineProps<{ brand: string | null; model: string | null }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const authStore = useAuthStore()

const MAX_TEXT = 150
const freeText = ref('')
const contactEmail = ref(authStore.user?.sub ?? '')
const netKwh = ref<number | null>(null)
const buildYear = ref<number | null>(null)
const busy = ref(false)
const done = ref(false)
const error = ref('')

const remaining = computed(() => MAX_TEXT - freeText.value.length)

async function submit() {
    const text = freeText.value.trim()
    if (!text || busy.value) return
    busy.value = true
    error.value = ''
    try {
        await vehicleRequestService.submit({
            freeText: text,
            contactEmail: contactEmail.value.trim(),
            brand: props.brand || null,
            model: props.model || null,
            netKwh: netKwh.value || null,
            buildYear: buildYear.value || null,
        })
        done.value = true
    } catch (e: any) {
        error.value = e?.response?.status === 429
            ? t('cars.missing_dialog_rate_limited')
            : (e?.response?.data?.message || t('cars.missing_dialog_error'))
    } finally {
        busy.value = false
    }
}
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm sm:p-4" @click.self="emit('close')">
            <div role="dialog" aria-modal="true" aria-labelledby="missing-vehicle-title"
                 class="w-full sm:max-w-md max-h-[92vh] overflow-y-auto bg-white dark:bg-gray-800 rounded-t-xl sm:rounded-sm p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] space-y-4"
                 data-testid="missing-vehicle-dialog">
                <div class="flex items-start justify-between gap-3">
                    <h2 id="missing-vehicle-title" class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('cars.missing_dialog_title') }}</h2>
                    <button type="button" class="p-1 -m-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-200" :aria-label="t('cars.missing_dialog_cancel')" @click="emit('close')">
                        <XMarkIcon class="w-6 h-6" aria-hidden="true" />
                    </button>
                </div>

                <div v-if="done" class="space-y-4">
                    <p class="text-sm text-gray-700 dark:text-gray-200">{{ t('cars.missing_dialog_thanks') }}</p>
                    <button type="button" class="w-full min-h-[44px] rounded-sm bg-indigo-600 text-white font-medium hover:bg-indigo-700" @click="emit('close')">
                        {{ t('cars.missing_dialog_close') }}
                    </button>
                </div>

                <form v-else class="space-y-4" @submit.prevent="submit">
                    <div>
                        <label for="missing-text" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.missing_dialog_what_label') }}</label>
                        <textarea id="missing-text" v-model="freeText" required :maxlength="MAX_TEXT" rows="2"
                                  :placeholder="t('cars.missing_dialog_what_placeholder')"
                                  class="w-full rounded-sm border border-gray-300 dark:border-gray-600 p-2 text-base dark:bg-gray-700 dark:text-gray-100 focus:border-indigo-500 focus:ring-indigo-500"></textarea>
                        <p class="text-xs text-gray-500 dark:text-gray-400 text-right">{{ remaining }}</p>
                    </div>

                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <label for="missing-kwh" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.missing_dialog_kwh_label') }}</label>
                            <input id="missing-kwh" v-model.number="netKwh" name="netKwh" type="number" inputmode="decimal" min="1" max="300" step="0.1"
                                   class="w-full rounded-sm border border-gray-300 dark:border-gray-600 p-2 text-base dark:bg-gray-700 dark:text-gray-100" />
                        </div>
                        <div>
                            <label for="missing-year" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.missing_dialog_year_label') }}</label>
                            <input id="missing-year" v-model.number="buildYear" name="buildYear" type="number" inputmode="numeric" min="1990" max="2035"
                                   class="w-full rounded-sm border border-gray-300 dark:border-gray-600 p-2 text-base dark:bg-gray-700 dark:text-gray-100" />
                        </div>
                    </div>

                    <div>
                        <label for="missing-email" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('cars.missing_dialog_email_label') }}</label>
                        <input id="missing-email" v-model="contactEmail" type="email" required maxlength="254" autocomplete="email"
                               class="w-full rounded-sm border border-gray-300 dark:border-gray-600 p-2 text-base dark:bg-gray-700 dark:text-gray-100" />
                        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('cars.missing_dialog_privacy') }}</p>
                    </div>

                    <p v-if="error" class="text-sm text-red-600 dark:text-red-400" role="alert">{{ error }}</p>

                    <div class="flex flex-col-reverse sm:flex-row sm:justify-end gap-2">
                        <button type="button" class="min-h-[44px] px-4 rounded-sm text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700" @click="emit('close')">
                            {{ t('cars.missing_dialog_cancel') }}
                        </button>
                        <button type="submit" :disabled="busy" class="min-h-[44px] px-4 rounded-sm bg-indigo-600 text-white font-medium hover:bg-indigo-700 disabled:opacity-60">
                            {{ t('cars.missing_dialog_submit') }}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </Teleport>
</template>
