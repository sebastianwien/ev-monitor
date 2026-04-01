import { request } from '@playwright/test';

const API_URL = process.env.API_URL || 'http://localhost:8080';
const MAILPIT_URL = process.env.MAILPIT_URL || 'http://localhost:8025';

export const TEST_USER = {
  email: 'e2e-test@playwright.local',
  username: 'e2e_playwright',
  password: 'PlaywrightTest123!',
};

async function pollMailpit(email: string, timeoutMs = 10_000): Promise<string | null> {
  const api = await request.newContext();
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const resp = await api.get(`${MAILPIT_URL}/api/v1/messages`);
    const data = await resp.json();

    for (const msg of data.messages || []) {
      if (msg.To?.some((t: any) => t.Address === email)) {
        const msgResp = await api.get(`${MAILPIT_URL}/api/v1/message/${msg.ID}`);
        const msgData = await msgResp.json();
        const text = msgData.Text || msgData.HTML || '';
        const match = text.match(/\/verify-email\?token=([a-zA-Z0-9_-]+)/);
        if (match) return match[1];
      }
    }

    await new Promise(r => setTimeout(r, 500));
  }

  return null;
}

export default async function globalSetup() {
  const api = await request.newContext({ baseURL: API_URL });

  // Prüfen ob User bereits verifiziert existiert
  const loginCheck = await api.post('/api/auth/login', {
    data: { email: TEST_USER.email, password: TEST_USER.password },
  });

  if (loginCheck.ok()) {
    console.log('[E2E Setup] Test user already exists and is verified.');
    return;
  }

  // Registrieren - 409 = User existiert aber ist evtl. nicht verifiziert, weitermachen
  const registerResp = await api.post('/api/auth/register', {
    data: {
      email: TEST_USER.email,
      username: TEST_USER.username,
      password: TEST_USER.password,
    },
  });

  const registered = registerResp.ok() || registerResp.status() === 409;
  if (!registered) {
    throw new Error(`[E2E Setup] Registration failed: ${registerResp.status()} ${await registerResp.text()}`);
  }

  // Wenn 409: Resend-Verification triggern damit eine frische Mail kommt
  if (registerResp.status() === 409) {
    await api.post('/api/auth/resend-verification', {
      data: { email: TEST_USER.email },
    });
  }

  // Auf Verification-Mail warten (polling, max 10s)
  const token = await pollMailpit(TEST_USER.email);
  if (!token) {
    throw new Error('[E2E Setup] No verification email received within 10s. Is Mailpit running?');
  }

  // Email verifizieren
  const verifyResp = await api.get(`/api/auth/verify-email?token=${token}`);
  if (!verifyResp.ok()) {
    throw new Error(`[E2E Setup] Email verification failed: ${verifyResp.status()}`);
  }

  console.log('[E2E Setup] Test user created and verified.');
}
