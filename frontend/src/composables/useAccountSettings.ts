import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api/axios'
import { subscriptionService } from '../api/subscriptionService'

export function useAccountSettings(
  loading: Ref<boolean>,
  message: Ref<{ type: 'success' | 'error'; text: string } | null>,
) {
  const { t } = useI18n()
  const router = useRouter()
  const authStore = useAuthStore()

  // Account Data
  const email = ref('')
  const username = ref('')
  const registeredSince = ref('')
  const totalLogs = ref(0)
  const totalKwh = ref(0)
  const totalCostEur = ref(0)
  const coinBalance = ref(0)

  // Referral
  const referralCode = ref('')
  const referralCopied = ref(false)

  // Community
  const leaderboardVisible = ref(true)

  // Subscription
  const subscriptionPeriodEnd = ref<string | null>(null)
  const portalLoading = ref(false)

  // Forms
  const showEmailForm = ref(false)
  const showUsernameForm = ref(false)
  const showPasswordForm = ref(false)
  const newEmail = ref('')
  const emailCurrentPassword = ref('')
  const newUsername = ref('')
  const currentPassword = ref('')
  const newPassword = ref('')
  const confirmPassword = ref('')

  // Delete Account
  const showDeleteConfirm = ref(false)
  const deletePassword = ref('')

  const referralLink = () => `${window.location.origin}/register?ref=${referralCode.value}`

  const copyReferralLink = async () => {
    try {
      await navigator.clipboard.writeText(referralLink())
      referralCopied.value = true
      setTimeout(() => { referralCopied.value = false }, 2000)
    } catch { /* fallback: select the input text */ }
  }

  const openPortal = async () => {
    portalLoading.value = true
    try {
      const { portalUrl } = await subscriptionService.createPortalSession()
      window.location.href = portalUrl
    } catch { /* portal not available */ }
    finally { portalLoading.value = false }
  }

  const fetchUserData = async () => {
    try {
      const user = authStore.user
      if (user) {
        email.value = user.sub || ''
        username.value = user.username || user.sub.split('@')[0] || ''
      }
      const [statsRes, coinsRes] = await Promise.all([
        api.get('/users/me/stats'),
        api.get('/coins/balance')
      ])
      const stats = statsRes.data
      registeredSince.value = new Date(stats.registeredSince).toLocaleDateString()
      totalLogs.value = stats.totalLogs
      totalKwh.value = stats.totalKwh
      totalCostEur.value = stats.totalCostEur ?? 0
      referralCode.value = stats.referralCode || ''
      leaderboardVisible.value = stats.leaderboardVisible ?? true
      coinBalance.value = coinsRes.data.totalCoins || 0
    } catch { /* non-critical */ }
  }

  const changeEmail = async () => {
    if (!newEmail.value || !emailCurrentPassword.value) return
    loading.value = true
    message.value = null
    try {
      await api.put('/users/me/email', { newEmail: newEmail.value, currentPassword: emailCurrentPassword.value })
      authStore.logout()
      router.push('/login?reason=email-changed')
    } catch (err: any) {
      message.value = { type: 'error', text: err.response?.data?.message || t('settings.err_email_change') }
      loading.value = false
    }
  }

  const changeUsername = async () => {
    if (!newUsername.value) return
    loading.value = true
    message.value = null
    try {
      const response = await api.put('/users/me/username', { newUsername: newUsername.value })
      authStore.setToken(response.data.token)
      username.value = authStore.user?.username || newUsername.value
      message.value = { type: 'success', text: t('settings.ok_username') }
      showUsernameForm.value = false
      newUsername.value = ''
    } catch (err: any) {
      message.value = { type: 'error', text: err.response?.data?.message || t('settings.err_username_change') }
    } finally {
      loading.value = false
    }
  }

  const changePassword = async () => {
    if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
      message.value = { type: 'error', text: t('settings.err_fill_all') }; return
    }
    if (newPassword.value !== confirmPassword.value) {
      message.value = { type: 'error', text: t('settings.err_passwords_mismatch') }; return
    }
    if (newPassword.value.length < 8) {
      message.value = { type: 'error', text: t('settings.err_password_short') }; return
    }
    loading.value = true
    message.value = null
    try {
      await api.put('/users/me/password', { currentPassword: currentPassword.value, newPassword: newPassword.value })
      message.value = { type: 'success', text: t('settings.ok_password') }
      showPasswordForm.value = false
      currentPassword.value = ''
      newPassword.value = ''
      confirmPassword.value = ''
    } catch (err: any) {
      message.value = { type: 'error', text: err.response?.data?.message || t('settings.err_password_change') }
    } finally {
      loading.value = false
    }
  }

  const exportData = async () => {
    loading.value = true
    message.value = null
    try {
      const response = await api.get('/users/me/export', { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `ev-monitor-export-${Date.now()}.json`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      message.value = { type: 'success', text: t('settings.ok_export') }
    } catch (err: any) {
      message.value = { type: 'error', text: err.response?.data?.message || t('settings.err_export') }
    } finally {
      loading.value = false
    }
  }

  const deleteAccount = async () => {
    if (!deletePassword.value) { message.value = { type: 'error', text: t('settings.err_password_enter') }; return }
    loading.value = true
    message.value = null
    try {
      await api.delete('/users/me', { data: { password: deletePassword.value } })
      authStore.logout()
      router.push('/login')
    } catch (err: any) {
      message.value = { type: 'error', text: err.response?.data?.message || t('settings.err_delete') }
      loading.value = false
    }
  }

  const toggleLeaderboardVisible = async () => {
    const newVal = !leaderboardVisible.value
    try {
      await api.put(`/users/me/leaderboard-visible?visible=${newVal}`)
      leaderboardVisible.value = newVal
    } catch {
      message.value = { type: 'error', text: t('settings.err_leaderboard') }
    }
  }

  const restartOnboarding = () => {
    localStorage.removeItem('onboarding-completed')
    localStorage.setItem('onboarding-force', 'true')
    message.value = { type: 'success', text: t('settings.tutorial_restarting') }
    setTimeout(() => { window.location.reload() }, 1000)
  }

  const initSubscription = async () => {
    authStore.refreshPremiumStatus()
    try {
      const status = await subscriptionService.getStatus()
      subscriptionPeriodEnd.value = status.subscriptionPeriodEnd
    } catch { /* non-critical */ }
  }

  return {
    email, username, registeredSince, totalLogs, totalKwh, totalCostEur,
    coinBalance, referralCode, referralCopied, leaderboardVisible,
    subscriptionPeriodEnd, portalLoading,
    showEmailForm, showUsernameForm, showPasswordForm,
    newEmail, emailCurrentPassword, newUsername,
    currentPassword, newPassword, confirmPassword,
    showDeleteConfirm, deletePassword,
    referralLink, copyReferralLink, openPortal,
    fetchUserData, changeEmail, changeUsername, changePassword,
    exportData, deleteAccount, toggleLeaderboardVisible, restartOnboarding,
    initSubscription,
    authStore,
  }
}
