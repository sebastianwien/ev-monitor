import type { CountryCode } from './unitSystems'

export interface CountryOption {
  code: CountryCode
  flag: string
  name: Record<string, string>
}

export const COUNTRY_OPTIONS: CountryOption[] = [
  { code: 'DE', flag: '🇩🇪', name: { de: 'Deutschland', en: 'Germany' } },
  { code: 'AT', flag: '🇦🇹', name: { de: 'Österreich', en: 'Austria' } },
  { code: 'CH', flag: '🇨🇭', name: { de: 'Schweiz', en: 'Switzerland' } },
  { code: 'GB', flag: '🇬🇧', name: { de: 'Großbritannien', en: 'United Kingdom' } },
  { code: 'NL', flag: '🇳🇱', name: { de: 'Niederlande', en: 'Netherlands' } },
  { code: 'BE', flag: '🇧🇪', name: { de: 'Belgien', en: 'Belgium' } },
  { code: 'DK', flag: '🇩🇰', name: { de: 'Dänemark', en: 'Denmark' } },
  { code: 'NO', flag: '🇳🇴', name: { de: 'Norwegen', en: 'Norway' } },
  { code: 'SE', flag: '🇸🇪', name: { de: 'Schweden', en: 'Sweden' } },
  { code: 'FI', flag: '🇫🇮', name: { de: 'Finnland', en: 'Finland' } },
  { code: 'US', flag: '🇺🇸', name: { de: 'USA', en: 'United States' } },
]
