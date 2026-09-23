export type CsvCell = string | number | null | undefined

const SEP = ';'

const escape = (cell: CsvCell): string => {
  if (cell === null || cell === undefined) return ''
  const text = typeof cell === 'number' ? cell.toFixed(2).replace('.', ',') : String(cell)
  return /[";\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** Semicolon-separated CSV with CRLF line ends, the format German Excel/Numbers open without an import dialog. */
export function toCsv(header: string[], rows: CsvCell[][]): string {
  return [header, ...rows].map((r) => r.map(escape).join(SEP)).join('\r\n')
}

export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
