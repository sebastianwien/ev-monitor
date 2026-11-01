/**
 * Feedback der Vorschau-Tester als vorausgefüllte Mail: kein Speicher, kein Backend.
 * Kontext (Seite, Breite, Modus, Gerät) steht im Text, damit keine Rückfrage nötig ist.
 */
export interface PreviewFeedbackInput {
  to: string
  path: string
  width: number
  dark: boolean
  userAgent: string
  texts: { subject: string; prompt: string; screenshot: string; keep: string }
}

export function previewFeedbackMailto(input: PreviewFeedbackInput): string {
  const { to, path, width, dark, userAgent, texts } = input
  const subject = `${texts.subject}: ${path.split(/[?#]/)[0]}`
  const body = [
    texts.prompt,
    '',
    '',
    '',
    texts.screenshot,
    '',
    `--- ${texts.keep} ---`,
    `Seite: ${path}`,
    `Breite: ${width} px, ${dark ? 'dark' : 'light'}`,
    `Gerät: ${userAgent}`,
  ].join('\n')
  // encodeURIComponent statt URLSearchParams: Mail-Apps zeigen "+" sonst wörtlich
  return `mailto:${to}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`
}
