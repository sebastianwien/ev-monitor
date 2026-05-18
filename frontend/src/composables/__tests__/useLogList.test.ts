import { describe, it, expect, beforeEach } from 'vitest'
import {
  PAGE_SIZE_OPTIONS,
  DEFAULT_PAGE_SIZE,
  PAGE_SIZE_STORAGE_KEY,
  readStoredPageSize,
  writeStoredPageSize,
} from '../useLogList'

describe('useLogList page size persistence', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('exposes 10, 25, 50 as the allowed options', () => {
    expect(PAGE_SIZE_OPTIONS).toEqual([10, 25, 50])
  })

  it('uses 25 as default page size', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(25)
    expect(PAGE_SIZE_OPTIONS).toContain(DEFAULT_PAGE_SIZE)
  })

  it('returns default when nothing is stored', () => {
    expect(readStoredPageSize()).toBe(DEFAULT_PAGE_SIZE)
  })

  it('returns stored value when valid', () => {
    localStorage.setItem(PAGE_SIZE_STORAGE_KEY, '50')
    expect(readStoredPageSize()).toBe(50)
  })

  it('rejects values not in the allowed options', () => {
    localStorage.setItem(PAGE_SIZE_STORAGE_KEY, '20')
    expect(readStoredPageSize()).toBe(DEFAULT_PAGE_SIZE)
  })

  it('rejects non-numeric values', () => {
    localStorage.setItem(PAGE_SIZE_STORAGE_KEY, 'foo')
    expect(readStoredPageSize()).toBe(DEFAULT_PAGE_SIZE)
  })

  it('persists a valid page size to localStorage', () => {
    writeStoredPageSize(50)
    expect(localStorage.getItem(PAGE_SIZE_STORAGE_KEY)).toBe('50')
    expect(readStoredPageSize()).toBe(50)
  })

  it('does not persist invalid page sizes', () => {
    writeStoredPageSize(999 as unknown as 10)
    expect(localStorage.getItem(PAGE_SIZE_STORAGE_KEY)).toBeNull()
  })
})
