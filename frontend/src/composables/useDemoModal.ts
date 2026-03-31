import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'

export function useDemoModal(storageKey: string) {
  const authStore = useAuthStore()
  const visible = ref(false)

  onMounted(() => {
    if (authStore.isDemoAccount && !sessionStorage.getItem(storageKey)) {
      visible.value = true
    }
  })

  const dismiss = () => {
    sessionStorage.setItem(storageKey, '1')
    visible.value = false
  }

  return { visible, dismiss }
}
