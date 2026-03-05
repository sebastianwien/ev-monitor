import apiClient from './axios';

export interface GoeConnectionStatus {
  id: string;
  serial: string;
  displayName: string;
  active: boolean;
  carState: number;
  lastPollError: string | null;
  carStateLabel?: string;
}

export interface GoeConnectRequest {
  serial: string;
  apiKey: string;
  carId: string;
  displayName: string;
}

const CAR_STATE_LABELS: Record<number, string> = {
  1: 'Bereit',
  2: 'Ladevorgang aktiv',
  3: 'Wartet auf Fahrzeug',
  4: 'Ladevorgang abgeschlossen',
  5: 'Fehler',
};

const goeService = {
  async getConnections(): Promise<GoeConnectionStatus[]> {
    const r = await apiClient.get<GoeConnectionStatus[]>('/goe/connections');
    return r.data.map(c => ({
      ...c,
      carStateLabel: CAR_STATE_LABELS[c.carState] ?? 'Unbekannt',
    }));
  },

  async connect(request: GoeConnectRequest): Promise<GoeConnectionStatus> {
    const r = await apiClient.post<GoeConnectionStatus>('/goe/connect', request);
    return { ...r.data, carStateLabel: CAR_STATE_LABELS[r.data.carState] ?? 'Bereit' };
  },

  async disconnect(id: string): Promise<void> {
    await apiClient.delete(`/goe/connections/${id}`);
  },
};

export default goeService;
