/**
 * Launch-verankerte Gratis-Fenster im Frontend - spiegelt das Backend-`FeatureTrial`.
 *
 * Das Fenster startet am spaeteren von Registrierung ({@link registeredAt}, aus dem JWT)
 * und Launch und laeuft `days` Tage. Tages-granular und ohne Zeitzonen-Falle: ISO-Datum
 * wird als lokale Mitternacht geparst, verglichen wird auf Tagesebene. Kein `registeredAt`
 * (alte Tokens) = kein Trial. Das Backend-Gate bleibt die Sicherheitsgrenze; dieser Wert
 * steuert nur, ob das Frontend die Kachel oder den Teaser zeigt.
 */
function toLocalDate(iso: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return null
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
}

/** Letzter Tag (lokale Mitternacht), an dem das Trial fuer diese Registrierung traegt - null ohne Datum. */
export function launchTrialEnd(registeredAt: string | null | undefined, launchIso: string, days: number): Date | null {
  if (!registeredAt) return null
  const reg = toLocalDate(registeredAt)
  const launch = toLocalDate(launchIso)
  if (!reg || !launch) return null
  const anchor = reg.getTime() > launch.getTime() ? reg : launch
  return new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() + days)
}

export function isWithinLaunchTrial(
  registeredAt: string | null | undefined,
  launchIso: string,
  days: number,
  today: Date = new Date(),
): boolean {
  const end = launchTrialEnd(registeredAt, launchIso, days)
  if (!end) return false
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return todayStart.getTime() <= end.getTime()
}

/** Energie-Split & die uebrige Live-Analytics-Schicht: 30 Tage ab Launch 2026-09-04. */
export const ENERGY_SPLIT_TRIAL_LAUNCH = '2026-09-04'
export const ENERGY_SPLIT_TRIAL_DAYS = 30

export function isEnergySplitTrialActive(registeredAt: string | null | undefined, today?: Date): boolean {
  return isWithinLaunchTrial(registeredAt, ENERGY_SPLIT_TRIAL_LAUNCH, ENERGY_SPLIT_TRIAL_DAYS, today)
}

export function energySplitTrialEnd(registeredAt: string | null | undefined): Date | null {
  return launchTrialEnd(registeredAt, ENERGY_SPLIT_TRIAL_LAUNCH, ENERGY_SPLIT_TRIAL_DAYS)
}
