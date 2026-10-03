/**
 * Error text after a failed save of a charging log. Known error codes get a translated text -
 * the backend message is German only. Anything else keeps the backend message, then the generic one.
 */
const TRANSLATED_CODES: Record<string, string> = {
  CHARGING_PROVIDER_INVALID: 'logform.error_card_invalid',
}

export function logSaveErrorMessage(err: unknown, t: (key: string) => string): string {
  const data = (err as { response?: { data?: { code?: string; message?: string } } })?.response?.data
  const key = data?.code ? TRANSLATED_CODES[data.code] : undefined
  if (key) return t(key)
  return data?.message ?? t('logform.error_save')
}
