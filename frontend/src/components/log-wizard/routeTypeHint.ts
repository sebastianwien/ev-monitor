import { datetimeLocalToUtcIso } from '../../utils/datetime'

/**
 * Nachfrage "War das eine Autobahnfahrt?" im Wizard. Dieselbe Regel wie RouteTypeRules.meanPowerKw im Backend
 * (SoC-Hub seit dem Ende der Vorladung x effektive Kapazität / Stunden, Lücke 0,5 bis 12 h), hier mit der
 * niedrigeren Frage-Schwelle 15 kW (Backend schreibt erst ab 25 kW von sich aus HIGHWAY). Bewusst im Frontend
 * gedoppelt, damit die Frage ohne Roundtrip erscheint, sobald SoC-Start und Zeit stehen.
 */
export const ASK_HIGHWAY_MIN_KW = 15
const GAP_MIN_H = 0.5
const GAP_MAX_H = 12

export type PreviousChargeRef = { loggedAt: string; chargeDurationMinutes?: number | null; socAfterChargePercent?: number | null }
export type CurrentChargeRef = { loggedAt: string | null; socBeforeChargePercent: number | null }

/** Vorladung: jüngste Ladung vor dem aktuellen Zeitpunkt mit SoC-Ende. Liste ist absteigend nach Zeit. */
export function previousCharge(logs: PreviousChargeRef[], currentAt: Date): PreviousChargeRef | null {
  return logs.find(l => l.socAfterChargePercent != null && new Date(l.loggedAt).getTime() < currentAt.getTime()) ?? null
}

export function currentChargeAt(loggedAt: string | null, now = new Date()): Date {
  return loggedAt ? new Date(datetimeLocalToUtcIso(loggedAt)) : now
}

export function meanPowerKw(previous: PreviousChargeRef | null, current: CurrentChargeRef, capacityKwh: number | null | undefined, now = new Date()): number | null {
  if (!previous || previous.socAfterChargePercent == null || current.socBeforeChargePercent == null || !capacityKwh) return null
  const end = new Date(previous.loggedAt).getTime() + Math.max(0, previous.chargeDurationMinutes ?? 0) * 60_000
  const hours = (currentChargeAt(current.loggedAt, now).getTime() - end) / 3_600_000
  if (hours < GAP_MIN_H || hours > GAP_MAX_H) return null
  const hub = previous.socAfterChargePercent - current.socBeforeChargePercent
  if (hub <= 0) return null
  return (hub / 100) * capacityKwh / hours
}

export function shouldAskHighway(logs: PreviousChargeRef[], current: CurrentChargeRef, capacityKwh: number | null | undefined, now = new Date()): boolean {
  const at = currentChargeAt(current.loggedAt, now)
  const kw = meanPowerKw(previousCharge(logs, at), current, capacityKwh, now)
  return kw != null && kw >= ASK_HIGHWAY_MIN_KW
}
