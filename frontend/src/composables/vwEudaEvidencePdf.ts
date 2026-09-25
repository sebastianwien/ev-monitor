import type { EvidenceDocument } from './useEudaAuthorityComplaint'

/**
 * Rendert das Beleg-Dokument als PDF im Browser und löst den Download aus.
 * jsPDF wird erst beim Klick geladen, damit es nicht im Haupt-Bundle liegt.
 * Kein Server, keine Speicherung: der Beleg entsteht aus dem, was der Nutzer ohnehin sieht.
 */
const PAGE_W = 210
const PAGE_H = 297
const MARGIN = 18
const LINE = 4.6
const BODY_PT = 9
const MONO_SECTIONS = new Set(['identifiers', 'polls', 'deliveries'])

export async function downloadEvidencePdf(doc: EvidenceDocument): Promise<void> {
  const { jsPDF } = await import('jspdf')
  const pdf = new jsPDF({ unit: 'mm', format: 'a4' })
  const width = PAGE_W - 2 * MARGIN
  let y = MARGIN

  const ensureRoom = (needed: number) => {
    if (y + needed > PAGE_H - MARGIN) {
      pdf.addPage()
      y = MARGIN
    }
  }
  const write = (text: string, pt: number, style: 'normal' | 'bold' = 'normal', font: 'helvetica' | 'courier' = 'helvetica') => {
    pdf.setFont(font, style)
    pdf.setFontSize(pt)
    const lines: string[] = pdf.splitTextToSize(text, width)
    for (const line of lines) {
      ensureRoom(LINE)
      pdf.text(line, MARGIN, y)
      y += LINE * (pt / BODY_PT)
    }
  }

  write(doc.title, 15, 'bold')
  y += 1
  write(doc.subtitle, BODY_PT)
  y += 4

  for (const section of doc.sections) {
    ensureRoom(LINE * 3)
    write(section.heading, 11, 'bold')
    y += 1
    const font = MONO_SECTIONS.has(section.key) ? 'courier' : 'helvetica'
    for (const line of section.lines) write(line, font === 'courier' ? 8 : BODY_PT, 'normal', font)
    y += 4
  }

  const pages = pdf.getNumberOfPages()
  for (let p = 1; p <= pages; p++) {
    pdf.setPage(p)
    pdf.setFont('helvetica', 'normal')
    pdf.setFontSize(7)
    pdf.text(`${doc.subtitle} · ${p}/${pages}`, MARGIN, PAGE_H - 8)
  }
  pdf.save(doc.filename)
}
