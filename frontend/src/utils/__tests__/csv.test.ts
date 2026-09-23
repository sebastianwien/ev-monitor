import { describe, it, expect } from 'vitest'
import { toCsv } from '../csv'

describe('toCsv', () => {
  it('writes header and rows separated by semicolon (German Excel default)', () => {
    const csv = toCsv(['a', 'b'], [['1', '2'], ['3', '4']])
    expect(csv).toBe('a;b\r\n1;2\r\n3;4')
  })

  it('quotes fields containing separator, quotes or newlines and escapes quotes', () => {
    const csv = toCsv(['name'], [['Müller; GmbH'], ['say "hi"'], ['multi\nline']])
    expect(csv).toBe('name\r\n"Müller; GmbH"\r\n"say ""hi"""\r\n"multi\nline"')
  })

  it('renders null and undefined as empty, numbers with comma decimal', () => {
    const csv = toCsv(['x', 'y', 'z'], [[null, undefined, 3.9]])
    expect(csv).toBe('x;y;z\r\n;;3,90')
  })
})
