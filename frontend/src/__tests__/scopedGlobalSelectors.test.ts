import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

// Vue's scoped-CSS-Transform ersetzt bei ":global(.dark) .x" den ganzen Selektor durch ".dark".
// Die Regel trifft dann <html> und damit die ganze Seite (z. B. filter: grayscale).
// Richtig: ".dark .x" (scoped) oder der ganze Selektor in :global(...).
const GLOBAL_WITH_TAIL = /:global\([^)]*\)\s*[^\s,{]/

function vueFiles(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap(e =>
        e.isDirectory() ? vueFiles(join(dir, e.name)) : e.name.endsWith('.vue') ? [join(dir, e.name)] : [])
}

// Nur <style scoped>-Blöcke, ohne Kommentare. Kommentare bleiben zeilentreu stehen (Inhalt geleert).
function scopedCss(sfc: string): string {
    return sfc.replace(/<style\b[^>]*>[\s\S]*?<\/style>|[^\n]/g, m =>
        m.length > 1 && /^<style\b[^>]*\bscoped\b/.test(m)
            ? m.replace(/\/\*[\s\S]*?\*\//g, c => c.replace(/[^\n]/g, ' '))
            : m.replace(/[^\n]/g, ' '))
}

describe('scoped styles', () => {
    it('kein :global(...) mit nachfolgendem Selektor', () => {
        const offenders = vueFiles(join(__dirname, '..')).flatMap(file =>
            scopedCss(readFileSync(file, 'utf8')).split('\n')
                .map((line, i) => ({ line, i }))
                .filter(({ line }) => GLOBAL_WITH_TAIL.test(line))
                .map(({ line, i }) => `${file.split('/src/')[1]}:${i + 1}: ${line.trim()}`))
        expect(offenders).toEqual([])
    })
})
