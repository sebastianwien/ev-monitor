/** Seitengroessen der klassischen Listen-Pagination (LogsPaginationBar). */
export const PAGE_SIZE_OPTIONS = [10, 25, 50] as const
export type PageSize = typeof PAGE_SIZE_OPTIONS[number]
