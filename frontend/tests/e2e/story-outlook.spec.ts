import { test, expect, type Page } from '@playwright/test';
import { calmMonth, openStory, log, stats, DAY, iso } from './fixtures/story';

/**
 * Winter-Ausblick im Rückblick-Kopf von 320 px bis Desktop, hell und dunkel: Reichweiten-Balken und
 * 7-Tage-Leiste bleiben im Bild, keine Spalte schneidet ihren Text ab, keine überlappt die nächste.
 * Mit SHOTS=<ordner> legt jede Breite ein Bild ab.
 */
const SHOTS = process.env.SHOTS;
const WIDTHS = [320, 360, 390, 430, 768, 1280];

async function openWinterRecap(page: Page, theme: 'light' | 'dark') {
  const now = new Date(2026, 10, 2, 12).getTime();
  const d = calmMonth(now);
  d.monthStats = stats({ totalCharges: 0, totalKwhCharged: 0, energyCostEur: 0, totalCostEur: 0, totalDistanceKm: 0, chargesOverTime: [] });
  const season = (km: number, consumption: number) => ({ rangeKm: km, consumptionKwhPer100km: consumption, source: 'OWN_DRIVE', scope: null });
  // Breiteste Zahlen: vierstellige Reichweite, zweistellige Minusgrade
  d.recentStats = stats({ range: { current: season(1012, 19.4), summer: season(1234, 15.8), winter: season(1012, 19.4) } });
  d.logs = [log(now, { loggedAt: iso(now - 10 * DAY), createdAt: iso(now - 10 * DAY) })];
  const days = [[-23.4, -12.2, 71], [-18.6, -10.9, 73], [-12.1, -2.4, 3], [-8.2, 1.1, 61], [-1.4, 6.4, 0], [0.2, 12.1, 2], [-15.5, -11.8, 75]]
    .map(([minC, maxC, code], i) => ({ date: `2026-11-0${2 + i}`, minC, maxC, code, gustKmh: 10 }));
  await page.route(/\/api\/weather\/outlook/, route => route.fulfill({ json: { recentMeanC: 8.6, aheadMeanC: 4.3, deltaC: -14.3, recentDays: 7, aheadDays: 7, days } }));
  await openStory(page, d, { theme });
}

for (const theme of ['light', 'dark'] as const) {
  for (const width of WIDTHS) {
    test(`Ausblick passt auf ${width} px (${theme})`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await openWinterRecap(page, theme);
      const outlook = page.getByTestId('story-recap-hero').getByTestId('story-winter-outlook');
      await expect(outlook).toBeVisible();
      await expect(page.getByTestId('outlook-day')).toHaveCount(7);

      // Nichts ragt aus Seite oder Ausblick, keine Spalte schneidet ab oder überlappt
      const m = await outlook.evaluate(el => {
        const box = el.getBoundingClientRect();
        const cells = [...el.querySelectorAll('[data-testid="outlook-day"]')].map(c => {
          const r = c.getBoundingClientRect();
          const inner = [...c.querySelectorAll('[aria-hidden="true"] > span')].map(s => s.getBoundingClientRect());
          return { left: r.left, right: r.right, innerLeft: Math.min(...inner.map(i => i.left)), innerRight: Math.max(...inner.map(i => i.right)) };
        });
        const rangeRight = Math.max(...[...el.querySelectorAll('[data-testid="outlook-range"] *')].map(n => n.getBoundingClientRect().right));
        return { left: box.left, right: box.right, cells, rangeRight, pageWidth: document.documentElement.scrollWidth };
      });
      expect(m.pageWidth).toBeLessThanOrEqual(width);
      expect(m.left).toBeGreaterThanOrEqual(0);
      expect(m.right).toBeLessThanOrEqual(width);
      expect(m.rangeRight).toBeLessThanOrEqual(m.right + 0.5);
      m.cells.forEach((c, i) => {
        expect(c.innerLeft, `Tag ${i} links`).toBeGreaterThanOrEqual(c.left - 0.5);
        expect(c.innerRight, `Tag ${i} rechts`).toBeLessThanOrEqual(c.right + 0.5);
        if (i > 0) expect(c.left, `Tag ${i} überlappt`).toBeGreaterThanOrEqual(m.cells[i - 1].right - 0.5);
      });
      if (SHOTS) await page.getByTestId('story-recap-hero').screenshot({ path: `${SHOTS}/outlook-${theme}-${width}.png` });
    });
  }
}
