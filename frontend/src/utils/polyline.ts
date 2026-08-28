/**
 * Dekodiert das Encoded-Polyline-Format (Google, 5 Nachkommastellen), in dem
 * openrouteservice die berechnete Strecke liefert.
 *
 * Eigene ~20 Zeilen statt einer Abhaengigkeit: das Format ist stabil seit 2010 und der
 * Dekoder ist der einzige Teil davon, den wir brauchen.
 */
export function decodePolyline(encoded: string | null | undefined): [number, number][] {
  if (!encoded) return []
  const points: [number, number][] = []
  let index = 0
  let lat = 0
  let lon = 0
  while (index < encoded.length) {
    for (const isLat of [true, false]) {
      let result = 0
      let shift = 0
      let byte: number
      do {
        byte = encoded.charCodeAt(index++) - 63
        // Zeichen ausserhalb des Alphabets ergeben keine Koordinate, sondern eine Zahl
        // irgendwo auf der Welt. Wie im Backend endet die Linie hier, statt ins Leere zu laufen.
        if (byte < 0 || byte > 0x3f) return points
        result |= (byte & 0x1f) << shift
        shift += 5
      } while (byte >= 0x20 && index < encoded.length)
      const delta = result & 1 ? ~(result >> 1) : result >> 1
      if (isLat) lat += delta
      else lon += delta
    }
    points.push([Number((lat / 1e5).toFixed(5)), Number((lon / 1e5).toFixed(5))])
  }
  return points
}
