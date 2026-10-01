import { ref } from 'vue'

/** Nur ein Rädchen zeigt seinen Maßstab - das zuletzt angetippte. Geteilt über alle RulerInputs. */
export const activeRuler = ref<string | null>(null)
