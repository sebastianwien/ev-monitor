import axiosInstance from './axios';

export interface CarShare {
  token: string;
  /** Fertige absolute URL - kommt vom Server, damit Web und App dieselbe teilen. */
  url: string;
}

export interface PublicCarMonth {
  /** ISO-Datum, erster Tag des Monats. */
  month: string;
  kwhCharged?: number | null;
  costEur?: number | null;
  consumptionKwhPer100km?: number | null;
}

export interface PublicCarCharge {
  /** ISO-Datum ohne Uhrzeit. */
  chargedOn: string;
  kwhCharged?: number | null;
  costEur?: number | null;
  durationMinutes?: number | null;
  chargingType?: string | null;
  maxChargingPowerKw?: number | null;
  consumptionKwhPer100km?: number | null;
  publicCharging?: boolean | null;
}

/**
 * Eine oeffentlich geteilte Fahrzeugseite. Spiegelt PublicCarResponse im Backend -
 * bewusst ohne Kennzeichen, Kilometerstand, Ort, Betreiber und Besitzer.
 */
export interface PublicCar {
  carModel?: string | null;
  year?: number | null;
  hasImage: boolean;
  totalCharges?: number | null;
  totalKwhCharged?: number | null;
  totalDistanceKm?: number | null;
  avgConsumptionKwhPer100km?: number | null;
  avgCostPerKwh?: number | null;
  costPer100km?: number | null;
  publicChargingSharePercent?: number | null;
  summerConsumptionKwhPer100km?: number | null;
  winterConsumptionKwhPer100km?: number | null;
  months: PublicCarMonth[];
  recentCharges: PublicCarCharge[];
}

export const carShareService = {
  /** Gibt das Fahrzeug frei. Mehrfaches Aufrufen liefert denselben Link. */
  async create(carId: string): Promise<CarShare> {
    const { data } = await axiosInstance.post<CarShare>(`/cars/${carId}/share`);
    return data;
  },

  /** Aktueller Freigabe-Status, null wenn nicht geteilt (204). */
  async get(carId: string): Promise<CarShare | null> {
    const res = await axiosInstance.get<CarShare | ''>(`/cars/${carId}/share`);
    return res.status === 204 || !res.data ? null : (res.data as CarShare);
  },

  async revoke(carId: string): Promise<void> {
    await axiosInstance.delete(`/cars/${carId}/share`);
  },

  /** Oeffentlicher Abruf - braucht keine Anmeldung. */
  async getPublic(token: string): Promise<PublicCar> {
    const { data } = await axiosInstance.get<PublicCar>(`/public/car/${token}`);
    return data;
  },
};
