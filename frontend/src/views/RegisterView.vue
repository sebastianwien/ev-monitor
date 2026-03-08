<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import api from '../api/axios';
import { analytics } from '../services/analytics';

const route = useRoute();

const email = ref('');
const username = ref('');
const password = ref('');
const confirmPassword = ref('');
const referralCode = ref('');
const utmSource = ref('');
const utmMedium = ref('');
const utmCampaign = ref('');
const error = ref('');

onMounted(() => {
  if (route.query.ref) {
    referralCode.value = String(route.query.ref);
  }
  // Capture UTM parameters for campaign tracking
  if (route.query.utm_source) {
    utmSource.value = String(route.query.utm_source);
  }
  if (route.query.utm_medium) {
    utmMedium.value = String(route.query.utm_medium);
  }
  if (route.query.utm_campaign) {
    utmCampaign.value = String(route.query.utm_campaign);
  }
});
const pendingEmail = ref('');
const resendSent = ref(false);
const resendError = ref('');

const EMAIL_REGEX = /^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$/

const handleRegister = async () => {
  if (!EMAIL_REGEX.test(email.value)) {
    error.value = 'Bitte gib eine gültige E-Mail-Adresse ein (z.B. name@example.com)';
    return;
  }
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(username.value)) {
    error.value = 'Username muss 3-20 Zeichen lang sein und darf nur Buchstaben, Zahlen und Unterstriche enthalten';
    return;
  }
  if (password.value.length < 8) {
    error.value = 'Passwort muss mindestens 8 Zeichen lang sein';
    return;
  }
  if (password.value !== confirmPassword.value) {
    error.value = 'Passwörter stimmen nicht überein';
    return;
  }
  try {
    error.value = '';
    const response = await api.post('/auth/register', {
      email: email.value,
      username: username.value,
      password: password.value,
      referralCode: referralCode.value || undefined,
      utmSource: utmSource.value || undefined,
      utmMedium: utmMedium.value || undefined,
      utmCampaign: utmCampaign.value || undefined,
    });
    if (response.data.status === 'PENDING_VERIFICATION') {
      pendingEmail.value = response.data.email;

      // Track successful registration
      analytics.trackRegistrationCompleted();
    }
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Registrierung fehlgeschlagen';
  }
};

const handleResend = async () => {
  resendError.value = '';
  resendSent.value = false;
  try {
    await api.post('/auth/resend-verification', { email: pendingEmail.value });
    resendSent.value = true;
  } catch (err: any) {
    const code = err.response?.data?.code;
    resendError.value = code === 'RATE_LIMITED'
      ? 'Kurz warten – du hast gerade erst eine E-Mail angefordert.'
      : 'Fehler beim Senden. Bitte versuche es später erneut.';
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-[80vh] bg-gray-100">
    <div class="w-full max-w-md p-8 bg-white rounded-xl shadow-lg">

      <!-- Pending Verification Screen -->
      <div v-if="pendingEmail" class="text-center">
        <div class="text-6xl mb-4">📬</div>
        <h2 class="text-2xl font-bold text-gray-800 mb-2">Check deine E-Mails!</h2>
        <p class="text-gray-500 mb-2">
          Wir haben einen Bestätigungs-Link an
        </p>
        <p class="font-semibold text-indigo-600 mb-4">{{ pendingEmail }}</p>
        <p class="text-gray-500 text-sm mb-6">
          Klick auf den Link in der E-Mail, um dein Konto zu aktivieren.<br>
          Der Link ist 24 Stunden gültig.
        </p>

        <div v-if="!resendSent">
          <div v-if="resendError" class="text-sm text-red-600 bg-red-50 p-2 rounded mb-3">{{ resendError }}</div>
          <button
            @click="handleResend"
            class="text-sm text-indigo-600 hover:underline"
          >
            Keine E-Mail erhalten? Erneut senden
          </button>
        </div>
        <p v-else class="text-sm text-green-600 font-medium">✅ E-Mail wurde erneut verschickt!</p>

        <div class="mt-6 text-sm text-gray-400">
          <router-link to="/login" class="hover:text-gray-600">Zurück zum Login</router-link>
        </div>
      </div>

      <!-- Registration Form -->
      <div v-else>
        <h2 class="text-3xl font-bold text-center text-gray-800 mb-8">Registrieren</h2>
        <form @submit.prevent="handleRegister" class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700">E-Mail</label>
            <input v-model="email" type="email" required autocomplete="email" class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Username</label>
            <input v-model="username" type="text" required autocomplete="username" pattern="[a-zA-Z0-9_]{3,20}" minlength="3" maxlength="20" class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" placeholder="z.B. max_mustermann" />
            <p class="text-xs text-gray-500 mt-1">3-20 Zeichen, nur Buchstaben, Zahlen und Unterstriche</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Passwort</label>
            <input v-model="password" type="password" required autocomplete="new-password" class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Passwort bestätigen</label>
            <input v-model="confirmPassword" type="password" required autocomplete="new-password" class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Einladungscode <span class="text-gray-400 font-normal">(optional)</span></label>
            <input v-model="referralCode" type="text" autocomplete="off" maxlength="8" class="block w-full px-4 py-3 mt-1 border border-gray-300 rounded-lg shadow-sm focus:ring-indigo-500 focus:border-indigo-500 uppercase" placeholder="z.B. AB12CD34" />
          </div>
          <div v-if="error" class="text-sm font-medium text-red-600 bg-red-50 p-3 rounded-lg">{{ error }}</div>
          <button type="submit" class="w-full px-4 py-3 font-semibold text-white bg-indigo-600 rounded-lg shadow hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition">Konto erstellen</button>
        </form>

        <div class="mt-6 text-center text-sm text-gray-500">
          Bereits ein Konto? <router-link to="/login" class="font-semibold text-indigo-600 hover:text-indigo-500">Hier anmelden</router-link>
        </div>
      </div>

    </div>
  </div>
</template>
