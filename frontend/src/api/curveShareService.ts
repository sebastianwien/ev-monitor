import axiosInstance from './axios';
import type { CurvePoint } from '../components/charging/powerCurveSeries';

export interface CurveShare {
  token: string;
  /** Fertige absolute URL - kommt vom Server, damit Web und App dieselbe teilen. */
  url: string;
}

/**
 * Eine oeffentlich geteilte Ladekurve. Spiegelt PublicCurveResponse im Backend -
 * bewusst ohne Kilometerstand, Ort, Kosten und Besitzer.
 */
export interface PublicCurve {
  points: CurvePoint[];
  carModel?: string | null;
  kwhCharged?: number | null;
  durationMinutes?: number | null;
  socBefore?: number | null;
  socAfter?: number | null;
  peakKw?: number | null;
  cpoName?: string | null;
  chargingType?: string | null;
  /** ISO-Datum ohne Uhrzeit. */
  chargedOn?: string | null;
}

export const curveShareService = {
  /** Gibt die Ladung frei. Mehrfaches Aufrufen liefert denselben Link. */
  async create(logId: string): Promise<CurveShare> {
    const { data } = await axiosInstance.post<CurveShare>(`/logs/${logId}/share`);
    return data;
  },

  /** Aktueller Freigabe-Status, null wenn nicht geteilt (204). */
  async get(logId: string): Promise<CurveShare | null> {
    const res = await axiosInstance.get<CurveShare | ''>(`/logs/${logId}/share`);
    return res.status === 204 || !res.data ? null : (res.data as CurveShare);
  },

  async revoke(logId: string): Promise<void> {
    await axiosInstance.delete(`/logs/${logId}/share`);
  },

  /** Oeffentlicher Abruf - braucht keine Anmeldung. */
  async getPublic(token: string): Promise<PublicCurve> {
    const { data } = await axiosInstance.get<PublicCurve>(`/public/curve/${token}`);
    return data;
  },
};
