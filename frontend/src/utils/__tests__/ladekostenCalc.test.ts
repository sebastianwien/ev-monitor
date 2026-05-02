import { describe, it, expect } from 'vitest';
import { kostenPro100km, kostenProKm, kostenProMonat, benzinKostenProMonat, ersparnisProMonat } from '../ladekostenCalc';

describe('ladekostenCalc', () => {
  it('kostenPro100km = strompreis × verbrauch', () => {
    expect(kostenPro100km(0.33, 18)).toBeCloseTo(5.94);
    expect(kostenPro100km(0.40, 20)).toBeCloseTo(8.00);
  });

  it('kostenProKm = kostenPro100km / 100', () => {
    expect(kostenProKm(0.33, 18)).toBeCloseTo(0.0594);
  });

  it('kostenProMonat = kostenProKm × km', () => {
    expect(kostenProMonat(0.33, 18, 1000)).toBeCloseTo(59.4);
  });

  it('benzinKostenProMonat = (preis × verbrauch / 100) × km', () => {
    expect(benzinKostenProMonat(1.80, 7.5, 1000)).toBeCloseTo(135.0);
  });

  it('ersparnisProMonat = benzin - strom', () => {
    expect(ersparnisProMonat(0.33, 18, 1.80, 7.5, 1000)).toBeCloseTo(75.6);
  });

  it('negative Ersparnis wenn Strom teurer', () => {
    expect(ersparnisProMonat(0.80, 24, 1.80, 7.5, 1000)).toBeLessThan(0);
  });
});
