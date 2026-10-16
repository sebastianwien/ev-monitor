/** Die zwölf Monatsnamen in der Sprache des Nutzers, Januar zuerst. */
export function monthNames(locale: string): string[] {
  const fmt = new Intl.DateTimeFormat(locale, { month: 'long' })
  return Array.from({ length: 12 }, (_, i) => fmt.format(new Date(2000, i, 1)))
}
