import { ref } from 'vue'

// Module-level singleton - shared across all component instances
const isOnboardingVisible = ref(false)

/**
 * Schluessel, unter dem der Gesehen-Status liegt. Pro User, damit ein zweiter Account auf
 * demselben Geraet die Einfuehrung wieder bekommt. Zentral hier, weil ihn sowohl der Wizard
 * als auch das Neustarten aus den Einstellungen braucht.
 */
export function onboardingSeenKey(userId: string | null | undefined): string {
  return `onboarding-completed-${userId ?? 'anonymous'}`
}

export function useOnboardingState() {
  return { isOnboardingVisible }
}
