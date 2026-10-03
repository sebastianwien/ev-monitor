import { describe, it, expect } from 'vitest'
import de from '../locales/de.yaml'
import en from '../locales/en.yaml'
import nb from '../locales/nb.yaml'
import sv from '../locales/sv.yaml'

/**
 * Alle vier Sprachdateien muessen dieselben Schluessel haben, sonst faellt vue-i18n
 * zur Laufzeit auf Deutsch zurueck und niemand merkt es vor dem Handy-Test.
 *
 * Namespaces mit bekannten Luecken stehen in KNOWN_GAPS. Neue Luecken ausserhalb
 * davon lassen den Test fehlschlagen. Wer eine Luecke schliesst, nimmt den
 * Namespace hier heraus.
 */
const KNOWN_GAPS = new Set(['landing', 'imports', 'insights', 'announcements', 'cm', 'eu_data_act'])

type Tree = Record<string, unknown>

function flatten(tree: Tree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([k, v]) =>
    v != null && typeof v === 'object' && !Array.isArray(v) ? flatten(v as Tree, `${prefix}${k}.`) : [`${prefix}${k}`])
}

const reference = new Set(flatten(de as Tree))
const others: Record<string, Set<string>> = {
  en: new Set(flatten(en as Tree)),
  nb: new Set(flatten(nb as Tree)),
  sv: new Set(flatten(sv as Tree)),
}

const ns = (key: string) => key.split('.')[0]

describe('Locale-Paritaet gegen de.yaml', () => {
  for (const [lang, keys] of Object.entries(others)) {
    it(`${lang}: keine fehlenden Schluessel ausserhalb bekannter Luecken`, () => {
      const missing = [...reference].filter(k => !keys.has(k) && !KNOWN_GAPS.has(ns(k)))
      expect(missing, `in ${lang}.yaml fehlen: ${missing.slice(0, 10).join(', ')}`).toEqual([])
    })

    it(`${lang}: keine ueberzaehligen Schluessel ausserhalb bekannter Luecken`, () => {
      const extra = [...keys].filter(k => !reference.has(k) && !KNOWN_GAPS.has(ns(k)))
      expect(extra, `in ${lang}.yaml zu viel: ${extra.slice(0, 10).join(', ')}`).toEqual([])
    })
  }

  it('story-Namespace ist in allen Sprachen vollstaendig', () => {
    const storyKeys = [...reference].filter(k => ns(k) === 'story')
    expect(storyKeys.length).toBeGreaterThan(100)
    for (const [lang, keys] of Object.entries(others)) {
      expect(storyKeys.filter(k => !keys.has(k)), lang).toEqual([])
    }
  })
})
