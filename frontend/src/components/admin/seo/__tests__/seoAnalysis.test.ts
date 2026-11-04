import { describe, it, expect } from 'vitest'
import { caseVariants, channelOf, keywordTopic, movers, summarizeChannels, summarizeTopics } from '../seoAnalysis'
import type { SeoCampaignRow } from '../../../../api/seoService'

const campaign = (source: string, utmMedium: string, visitors: number, registrations: number, attributed: boolean): SeoCampaignRow =>
    ({ source, utmMedium, utmCampaign: '', visitors, visits: visitors, ctaVisitors: 0, ctaRate: 0, registrations, trials: 0, attributed })

describe('keywordTopic', () => {
    it('ordnet Keywords einem Thema zu, Brand zuerst', () => {
        expect(keywordTopic('ev monitor', true)).toBe('BRAND')
        expect(keywordTopic('xpeng g6 reichweite', false)).toBe('RANGE')
        expect(keywordTopic('id4 verbrauch 100km', false)).toBe('CONSUMPTION')
        expect(keywordTopic('elektroauto kosten pro monat', false)).toBe('COST')
        expect(keywordTopic('ioniq 5 ladezeit', false)).toBe('CHARGING')
        expect(keywordTopic('model 3 akku degradation', false)).toBe('BATTERY')
        expect(keywordTopic('enyaq erfahrung', false)).toBe('REVIEW')
        expect(keywordTopic('vw id 3', false)).toBe('OTHER')
    })

    it('summiert je Thema, meiste Impressionen zuerst', () => {
        const topics = summarizeTopics([
            { query: 'a reichweite', brand: false, impressions: 100, clicks: 2 },
            { query: 'b reichweite', brand: false, impressions: 50, clicks: 1 },
            { query: 'ev monitor', brand: true, impressions: 400, clicks: 30 },
        ])
        expect(topics.map(t => t.topic)).toEqual(['BRAND', 'RANGE'])
        expect(topics[1]).toMatchObject({ impressions: 150, clicks: 3, count: 2 })
    })
})

describe('channelOf', () => {
    it('gruppiert Quellen wie die Default Channel Group', () => {
        expect(channelOf('Google', '')).toBe('ORGANIC_SEARCH')
        expect(channelOf('DuckDuckGo', '')).toBe('ORGANIC_SEARCH')
        expect(channelOf('startpage.com', '')).toBe('ORGANIC_SEARCH')
        expect(channelOf('Google', 'cpc')).toBe('PAID_SEARCH')
        expect(channelOf('Direct / None', '')).toBe('DIRECT')
        expect(channelOf('email', 'lifecycle')).toBe('EMAIL')
        expect(channelOf('vw-forum', '')).toBe('FORUM')
        expect(channelOf('tff', 'forum')).toBe('FORUM')
        expect(channelOf('Reddit', '')).toBe('SOCIAL')
        expect(channelOf('ChatGPT', '')).toBe('AI')
        expect(channelOf('pwa', '')).toBe('APP')
        expect(channelOf('GitHub', '')).toBe('REFERRAL')
    })

    it('rechnet Sign-ups je 1 000 Besucher nur über zuordenbare Zeilen', () => {
        const channels = summarizeChannels([
            campaign('Google', '', 680, 9, true),
            campaign('Bing', '', 160, 0, false),
            campaign('vw-forum', '', 52, 5, true),
            campaign('xpeng-forum', '', 14, 4, true),
            campaign('Direct / None', '', 1500, 0, false),
        ])
        expect(channels.map(c => c.channel)).toEqual(['DIRECT', 'ORGANIC_SEARCH', 'FORUM'])
        const organic = channels.find(c => c.channel === 'ORGANIC_SEARCH')!
        expect(organic.visitors).toBe(840)
        expect(organic.registrations).toBe(9)
        // nur Google ist zuordenbar: 9 / 680 * 1000
        expect(organic.perThousand).toBeCloseTo(13.24, 1)
        expect(channels.find(c => c.channel === 'FORUM')!.perThousand).toBeCloseTo(136.4, 1)
        expect(channels.find(c => c.channel === 'DIRECT')!.perThousand).toBeNull()
    })
})

describe('movers', () => {
    it('liefert Gewinner und Verlierer nach Klick-Veränderung', () => {
        const { winners, losers } = movers([
            { key: '/a', clicks: 30, clicksPrevious: 10 },
            { key: '/b', clicks: 5, clicksPrevious: 25 },
            { key: '/c', clicks: 8, clicksPrevious: 8 },
            { key: '/d', clicks: 12, clicksPrevious: 2 },
            { key: '/e', clicks: 3 },
        ], 5)
        expect(winners.map(w => w.key)).toEqual(['/a', '/d'])
        expect(winners[0].delta).toBe(20)
        expect(losers.map(l => l.key)).toEqual(['/b'])
        expect(losers[0].delta).toBe(-20)
    })
})

describe('caseVariants', () => {
    it('findet Pfade, die sich nur in Groß- und Kleinschreibung unterscheiden', () => {
        const groups = caseVariants([
            { path: '/modelle/Tesla/Model_3', impressions: 887 },
            { path: '/modelle/TESLA/MODEL_3', impressions: 12 },
            { path: '/modelle/Tesla/MODEL_Y', impressions: 122 },
            { path: '/modelle/Tesla/Model_Y', impressions: 916 },
            { path: '/modelle/BMW/i4', impressions: 370 },
        ])
        expect(groups).toHaveLength(2)
        expect(groups[0].paths.map(p => p.path)).toEqual(['/modelle/Tesla/Model_Y', '/modelle/Tesla/MODEL_Y'])
        expect(groups[0].impressions).toBe(1038)
    })
})
