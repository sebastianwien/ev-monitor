import { ref } from 'vue'

// Module-level singleton — shared across all component instances
const isOnboardingVisible = ref(false)

export function useOnboardingState() {
  return { isOnboardingVisible }
}
