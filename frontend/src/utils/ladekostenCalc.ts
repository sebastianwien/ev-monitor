export function kostenPro100km(strompreis: number, verbrauchKwh: number): number {
  return strompreis * verbrauchKwh;
}

export function kostenProKm(strompreis: number, verbrauchKwh: number): number {
  return kostenPro100km(strompreis, verbrauchKwh) / 100;
}

export function kostenProMonat(strompreis: number, verbrauchKwh: number, kmProMonat: number): number {
  return kostenProKm(strompreis, verbrauchKwh) * kmProMonat;
}

export function benzinKostenProMonat(benzinpreis: number, benzinVerbrauch: number, kmProMonat: number): number {
  return (benzinpreis * benzinVerbrauch / 100) * kmProMonat;
}

export function ersparnisProMonat(
  strompreis: number, verbrauchKwh: number,
  benzinpreis: number, benzinVerbrauch: number,
  kmProMonat: number
): number {
  return benzinKostenProMonat(benzinpreis, benzinVerbrauch, kmProMonat)
    - kostenProMonat(strompreis, verbrauchKwh, kmProMonat);
}
