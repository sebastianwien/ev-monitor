/**
 * CO2-Konstanten, gemeinsam fuer CO2Card und Story-Dashboard.
 *
 * Verbrenner: 7,5 l/100 km Benzin x 2,37 kg CO2/l = 178 g/km.
 * Strommix: deutscher Strommix 2024 (Umweltbundesamt).
 * Oeko: Lifecycle-Wert fuer Solar und Wind.
 */
export const VERBRENNER_G_PER_KM = 178
/** CO2 je Liter Benzin; die Story rechnet damit den Benziner der eigenen Fahrzeugklasse. */
export const PETROL_KG_CO2_PER_L = 2.37
export const STROMMIX_G_PER_KWH = 380
export const OEKO_G_PER_KWH = 30
