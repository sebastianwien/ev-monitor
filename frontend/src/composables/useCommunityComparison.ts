import { useI18n } from 'vue-i18n'
import { comparisonDeltaPercent, comparisonLevel } from '../utils/communityComparison'

/** Wogegen verglichen wird: Community-Schnitt der Modellgruppe oder der eigene Schnitt. */
export type ComparisonBasis = 'community' | 'self'

/**
 * Uebersetzte Einordnung eines Wertes gegen einen Schnitt - eine Quelle fuer die
 * Tooltip-Texte aller Vergleichs-Chips (Gruppenkopf, Fahrt- und Ladezeilen), damit
 * ueberall dieselbe Formulierung steht. Gruppenkoepfe vergleichen gegen die Community
 * (dort mitteln sich Kontexte), einzelne Zeilen gegen den eigenen Schnitt - der enthaelt
 * das eigene Ladeprofil und bestraft nicht den Kontext einer Langstreckenwoche.
 */
export function useCommunityComparison() {
  const { t } = useI18n()

  /** Tooltip-Text zur Stufe, oder null wenn es keinen Schnitt gibt. */
  function comparisonTooltip(
    value: number | null | undefined,
    avg: number | null | undefined,
    formattedAvg: string,
    basis: ComparisonBasis = 'community',
  ): string | null {
    const level = comparisonLevel(value, avg)
    if (!level) return null
    const prefix = basis === 'self' ? 'dashboard.self_cmp_' : 'dashboard.community_cmp_'
    if (level === 'similar') return t(`${prefix}similar`, { avg: formattedAvg })
    const delta = Math.abs(comparisonDeltaPercent(value, avg) ?? 0)
    return t(`${prefix}${level}`, { delta, avg: formattedAvg })
  }

  return { comparisonLevel, comparisonDeltaPercent, comparisonTooltip }
}
