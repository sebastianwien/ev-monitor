import { describe, it, expect } from 'vitest'
import { previewFeedbackMailto } from '../previewFeedback'

const texts = { subject: 'Preview-Feedback', prompt: 'Was ist dir aufgefallen?', screenshot: 'Screenshot bitte', keep: 'nicht löschen' }

describe('previewFeedbackMailto', () => {
  const href = previewFeedbackMailto({ to: 'support@ev-monitor.net', path: '/logs?car=1', width: 390, dark: true, userAgent: 'Mozilla/5.0 (iPhone)', texts })
  const params = new URLSearchParams(href.split('?').slice(1).join('?'))

  it('adressiert die Mail und nennt die Seite im Betreff', () => {
    expect(href.startsWith('mailto:support@ev-monitor.net?')).toBe(true)
    expect(params.get('subject')).toBe('Preview-Feedback: /logs')
  })

  it('füllt Kontext und Screenshot-Hinweis in den Text', () => {
    const body = params.get('body')!
    expect(body).toContain('Was ist dir aufgefallen?')
    expect(body).toContain('Screenshot bitte')
    expect(body).toContain('/logs?car=1')
    expect(body).toContain('390 px')
    expect(body).toContain('dark')
    expect(body).toContain('Mozilla/5.0 (iPhone)')
  })

  it('lässt Hash aus dem Betreff', () => {
    const h = previewFeedbackMailto({ to: 'a@b.c', path: '/logs#x', width: 1, dark: false, userAgent: 'u', texts })
    expect(new URLSearchParams(h.split('?')[1]).get('subject')).toBe('Preview-Feedback: /logs')
  })

  it('kodiert Leerzeichen als %20, nicht als +', () => {
    expect(href).not.toContain('+')
    expect(href).toContain('%20')
  })
})
