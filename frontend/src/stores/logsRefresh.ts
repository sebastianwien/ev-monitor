import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLogsRefreshStore = defineStore('logsRefresh', () => {
  const version = ref(0)

  const notifyLogSaved = () => {
    version.value++
  }

  return { version, notifyLogSaved }
})
