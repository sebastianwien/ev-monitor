import { describe, it, expect } from 'vitest'
import { featuredImportSection, importSectionOrder, IMPORT_SECTION_ORDER } from '../useImportsTab'

const ctx = {
    returningFromSmartcar: false,
    activeCarIsTesla: false,
    activeCarIsVwEudaBrand: false,
    activeCarIsXpeng: false,
    hasAutoSync: false,
}

describe('featuredImportSection', () => {
    it('hebt nichts hervor, wenn kein Signal zutrifft', () => {
        expect(featuredImportSection(ctx)).toBeNull()
    })

    it('hebt Tesla hervor, wenn das aktive Auto ein Tesla ist', () => {
        expect(featuredImportSection({ ...ctx, activeCarIsTesla: true })).toBe('tesla')
    })

    it('hebt den EU-Data-Act-Weg fuer VW-Group-Fahrer hervor', () => {
        expect(featuredImportSection({ ...ctx, activeCarIsVwEudaBrand: true })).toBe('eu_data_act')
    })

    it('hebt den XPeng-Weg hervor, Smartcar deckt diese Marke nicht ab', () => {
        expect(featuredImportSection({ ...ctx, activeCarIsXpeng: true })).toBe('xpeng')
    })

    it('schickt XPeng-Fahrer mit AutoSync nicht in den Smartcar-Tab', () => {
        expect(featuredImportSection({ ...ctx, activeCarIsXpeng: true, hasAutoSync: true })).toBe('xpeng')
    })

    it('hebt Smartcar fuer AutoSync-Kunden ohne Tesla oder VW hervor', () => {
        expect(featuredImportSection({ ...ctx, hasAutoSync: true })).toBe('smartcar')
    })

    it('gewinnt die Rueckkehr aus dem Smartcar-OAuth gegen jedes andere Signal', () => {
        expect(featuredImportSection({
            ...ctx, returningFromSmartcar: true, activeCarIsTesla: true, activeCarIsVwEudaBrand: true,
        })).toBe('smartcar')
    })

    it('Tesla schlaegt die VW-Group-Marke, ein Auto kann nur eines von beiden sein', () => {
        expect(featuredImportSection({ ...ctx, activeCarIsTesla: true, activeCarIsVwEudaBrand: true })).toBe('tesla')
    })
})

describe('importSectionOrder', () => {
    it('laesst die Reihenfolge unveraendert, wenn nichts hervorgehoben ist', () => {
        expect(importSectionOrder(null)).toEqual([...IMPORT_SECTION_ORDER])
    })

    it('zieht die hervorgehobene Sektion nach vorn und behaelt den Rest in Reihenfolge', () => {
        expect(importSectionOrder('eu_data_act')).toEqual([
            'eu_data_act', ...IMPORT_SECTION_ORDER.filter(s => s !== 'eu_data_act'),
        ])
    })

    it('enthaelt jede Sektion genau einmal', () => {
        const order = importSectionOrder('tesla')
        expect(new Set(order).size).toBe(IMPORT_SECTION_ORDER.length)
    })
})

/**
 * ImportAccordion ueberspringt Schluessel ohne passenden Slot bewusst - ein Tippfehler in der
 * Reihenfolge oder ein umbenannter Slot wuerde eine Import-Sektion also lautlos verschwinden
 * lassen. Deshalb hier gegen die Quelle des Views geprueft statt gegen das gerenderte DOM: im
 * DOM haengen die Sektionen an v-if (Tesla, XPeng, AutoSync), es waeren nie alle elf sichtbar.
 */
describe('Sektionen im View und IMPORT_SECTION_ORDER', () => {
    it('deckt sich mit den benannten Slots in ImportsView, in beide Richtungen', async () => {
        const { readFileSync } = await import('node:fs')
        const view = readFileSync(new URL('../../views/ImportsView.vue', import.meta.url), 'utf-8')
        const slots = [...view.matchAll(/<template #(\w+)>/g)].map(m => m[1])

        expect([...slots].sort()).toEqual([...IMPORT_SECTION_ORDER].sort())
        expect(slots).toHaveLength(IMPORT_SECTION_ORDER.length)
    })

    it('rendert die Sektionen genau einmal, kein Slot doppelt', async () => {
        const { readFileSync } = await import('node:fs')
        const view = readFileSync(new URL('../../views/ImportsView.vue', import.meta.url), 'utf-8')
        const slots = [...view.matchAll(/<template #(\w+)>/g)].map(m => m[1])

        expect(new Set(slots).size).toBe(slots.length)
    })
})
