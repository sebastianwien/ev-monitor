#!/usr/bin/env node
/**
 * Translates en.yaml → nb.yaml + sv.yaml using the DeepL API.
 *
 * Usage:
 *   DEEPL_API_KEY=<key> node scripts/translate-locales.mjs
 *
 * The API key must be a DeepL Free or Pro key.
 * Free keys end with ":fx" and use api-free.deepl.com.
 */
import { readFileSync, writeFileSync } from 'fs'
import { createRequire } from 'module'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)
const yaml = require(join(__dirname, '../node_modules/yaml'))

const API_KEY = process.env.DEEPL_API_KEY
if (!API_KEY) {
  console.error('Error: DEEPL_API_KEY environment variable is not set.')
  console.error('Usage: DEEPL_API_KEY=<key> node scripts/translate-locales.mjs')
  process.exit(1)
}

const API_BASE = API_KEY.endsWith(':fx')
  ? 'https://api-free.deepl.com/v2/translate'
  : 'https://api.deepl.com/v2/translate'

const SOURCE = join(__dirname, '../src/locales/en.yaml')
const BATCH_SIZE = 40 // DeepL max is 50, stay safe

// Protect Vue i18n placeholders {foo} from translation using DeepL XML mode.
// Strategy:
//  1. Split string on {placeholder} boundaries
//  2. XML-escape the raw text parts (& < >)
//  3. Wrap placeholders in <k>N</k> tags (ignored by DeepL)
//  4. After translation: restore <k>N</k> → original placeholder, unescape entities
function xmlEsc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function protect(str) {
  const slots = []
  // Split on {placeholder} boundaries, leave {'...'} escape-patterns alone
  const parts = str.split(/(\{(?!')[^}]+\})/g)
  const text = parts.map((part, i) => {
    if (i % 2 === 1) {           // odd index = captured placeholder
      slots.push(part)
      return `<k>${slots.length - 1}</k>`
    }
    return xmlEsc(part)          // even index = literal text, escape XML specials
  }).join('')
  return { text, slots }
}

function restore(str, slots) {
  return str
    .replace(/<k>(\d+)<\/k>/g, (_, i) => slots[Number(i)] ?? `{unknown${i}}`)
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
}

async function deeplTranslate(texts, targetLang) {
  const protected_ = texts.map(protect)
  const res = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Authorization': `DeepL-Auth-Key ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      text: protected_.map(p => p.text),
      source_lang: 'EN',
      target_lang: targetLang,
      tag_handling: 'xml',
      ignore_tags: ['k'],
    }),
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(`DeepL ${res.status}: ${err}`)
  }
  const data = await res.json()
  return data.translations.map((t, i) => restore(t.text, protected_[i].slots))
}

// Collect all leaf strings with their key-paths, translate, rebuild
function collectStrings(obj, path = []) {
  const entries = []
  for (const [k, v] of Object.entries(obj)) {
    const p = [...path, k]
    if (typeof v === 'string') {
      entries.push({ path: p, value: v })
    } else if (typeof v === 'object' && v !== null) {
      entries.push(...collectStrings(v, p))
    }
  }
  return entries
}

function setPath(obj, path, value) {
  let cur = obj
  for (let i = 0; i < path.length - 1; i++) {
    if (!(path[i] in cur)) cur[path[i]] = {}
    cur = cur[path[i]]
  }
  cur[path[path.length - 1]] = value
}

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj))
}

async function translateLocale(source, targetLang, outputPath) {
  console.log(`\n→ Translating to ${targetLang}...`)
  const entries = collectStrings(source)
  const strings = entries.map(e => e.value)

  const translated = []
  for (let i = 0; i < strings.length; i += BATCH_SIZE) {
    const batch = strings.slice(i, i + BATCH_SIZE)
    process.stdout.write(`  batch ${Math.floor(i/BATCH_SIZE)+1}/${Math.ceil(strings.length/BATCH_SIZE)} (${i+1}-${Math.min(i+BATCH_SIZE, strings.length)}/${strings.length})... `)
    const results = await deeplTranslate(batch, targetLang)
    translated.push(...results)
    console.log('✓')
    // Small delay to be nice to the API
    if (i + BATCH_SIZE < strings.length) await new Promise(r => setTimeout(r, 300))
  }

  const result = deepClone(source)
  entries.forEach((entry, idx) => setPath(result, entry.path, translated[idx]))

  writeFileSync(outputPath, yaml.stringify(result, { lineWidth: 0 }))
  console.log(`  ✓ Written: ${outputPath}`)
  return translated.length
}

// Main
const raw = readFileSync(SOURCE, 'utf8')
const source = yaml.parse(raw)

const totalStrings = collectStrings(source).length
console.log(`Source: ${totalStrings} strings in en.yaml`)

const nb = await translateLocale(source, 'NB', join(__dirname, '../src/locales/nb.yaml'))
const sv = await translateLocale(source, 'SV', join(__dirname, '../src/locales/sv.yaml'))

console.log(`\nDone. Translated ${nb + sv} strings total (${nb} NB + ${sv} SV).`)
