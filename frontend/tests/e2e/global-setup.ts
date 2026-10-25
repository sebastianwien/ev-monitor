import { request, type APIRequestContext } from '@playwright/test';

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

export interface E2eUser { email: string; username: string; password: string }

/**
 * Meldet den Nutzer an; gibt es ihn noch nicht (oder ist er unbestätigt), wird er registriert
 * und über die Mail in Mailpit bestätigt. Liefert das JWT.
 */
export async function ensureVerifiedUser(api: APIRequestContext, user: E2eUser): Promise<string> {
  const loginCheck = await api.post('/api/auth/login', {
    data: { email: user.email, password: user.password },
  });

  if (!loginCheck.ok()) {
    // Registrieren - 409 = User existiert aber ist evtl. nicht verifiziert, weitermachen
    const registerResp = await api.post('/api/auth/register', {
      data: {
        email: user.email,
        username: user.username,
        password: user.password,
      },
    });

    const registered = registerResp.ok() || registerResp.status() === 409;
    if (!registered) {
      throw new Error(`[E2E Setup] Registration failed: ${registerResp.status()} ${await registerResp.text()}`);
    }

    if (registerResp.status() === 409) {
      await api.post('/api/auth/resend-verification', {
        data: { email: user.email },
      });
    }

    const token = await pollMailpit(user.email);
    if (!token) {
      throw new Error('[E2E Setup] No verification email received within 10s. Is Mailpit running?');
    }

    const verifyResp = await api.get(`/api/auth/verify-email?token=${token}`);
    if (!verifyResp.ok()) {
      throw new Error(`[E2E Setup] Email verification failed: ${verifyResp.status()}`);
    }

    console.log(`[E2E Setup] ${user.email} created and verified.`);
  }

  const authResp = await api.post('/api/auth/login', {
    data: { email: user.email, password: user.password },
  });
  if (!authResp.ok()) throw new Error(`[E2E Setup] Login ${user.email} failed: ${authResp.status()}`);
  return (await authResp.json()).token;
}

/** Legt ein Model 3 an, falls der Nutzer noch kein Auto hat, und liefert die Id des ersten Autos. */
export async function ensureTestCar(api: APIRequestContext, jwt: string): Promise<string> {
  const carsResp = await api.get('/api/cars', {
    headers: { Authorization: `Bearer ${jwt}` },
  });
  const cars = await carsResp.json();

  if (!Array.isArray(cars) || cars.length === 0) {
    // Seit A2 braucht jedes neue Auto eine Spec-Zeile: erste Variante des Model 3 aus der Akku-Auswahl.
    const models = await (await api.get('/api/cars/brands/TESLA/models')).json();
    const model3 = Array.isArray(models) ? models.find((m: any) => m.value === 'MODEL_3') : null;
    const spec = model3?.capacities?.find((c: any) => c.vehicleSpecificationId);
    if (!spec) throw new Error('[E2E Setup] Keine Spec-Zeile für MODEL_3 gefunden, Testauto kann nicht angelegt werden.');
    const carResp = await api.post('/api/cars', {
      headers: { Authorization: `Bearer ${jwt}` },
      data: { model: 'MODEL_3', year: 2022, registrationMonth: 3, hasHeatPump: false, vehicleSpecificationId: spec.vehicleSpecificationId },
    });
    if (!carResp.ok()) throw new Error(`[E2E Setup] Car creation failed: ${carResp.status()} ${await carResp.text()}`);
    console.log('[E2E Setup] Test car (Model 3) created.');
  }
  const carsAfter = await (await api.get('/api/cars', { headers: { Authorization: `Bearer ${jwt}` } })).json();
  return carsAfter[0].id;
}

export default async function globalSetup() {
  const api = await request.newContext({ baseURL: API_URL });
  const jwt = await ensureVerifiedUser(api, TEST_USER);
  // Sicherstellen dass ein Testfahrzeug existiert (wird für Log-E2E-Tests benötigt)
  const carId = await ensureTestCar(api, jwt);

  // Mindestens ein Ladevorgang muss existieren: Dashboard-Filterleiste und
  // Landing-Page-Model-Preview brauchen Logs (CI startet mit leerer DB)
  const logsResp = await api.get('/api/logs?limit=1', {
    headers: { Authorization: `Bearer ${jwt}` },
  });
  const logs = await logsResp.json();

  if (!Array.isArray(logs) || logs.length === 0) {
    const logResp = await api.post('/api/logs', {
      headers: { Authorization: `Bearer ${jwt}` },
      data: {
        carId,
        kwhCharged: 45.5,
        costEur: 18.2,
        odometerKm: 12000,
        socAfterChargePercent: 80,
        socBeforeChargePercent: 20,
        chargingType: 'AC',
      },
    });
    if (!logResp.ok()) {
      throw new Error(`[E2E Setup] Log creation failed: ${logResp.status()} ${await logResp.text()}`);
    }
    console.log('[E2E Setup] Test charging log created.');
  }
}
