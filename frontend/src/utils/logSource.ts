import type { Component } from 'vue'
import { BoltIcon, ArrowDownTrayIcon, HomeIcon } from '@heroicons/vue/24/outline'

export interface LogSourceInfo { label: string; icon: Component; classes: string }

/** Badge fuer die Herkunft einer automatisch erfassten Ladung; null bei manueller Eingabe. */
export function sourceInfo(ds?: string | null): LogSourceInfo | null {
  switch (ds) {
    case 'TESLA_FLEET_IMPORT':  return { label: 'Supercharger',    icon: BoltIcon,          classes: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-700' }
    case 'TESLA_LIVE':          return { label: 'Tesla',            icon: BoltIcon,          classes: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-700' }
    case 'TESLA_IMPORT':        return { label: 'Tesla',            icon: ArrowDownTrayIcon, classes: 'bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border border-purple-200 dark:border-purple-700' }
    case 'TESLA_MANUAL_IMPORT': return { label: 'Tesla',            icon: ArrowDownTrayIcon, classes: 'bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border border-purple-200 dark:border-purple-700' }
    case 'SPRITMONITOR_IMPORT': return { label: 'SpritMonitor',     icon: ArrowDownTrayIcon, classes: 'bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400 border border-purple-200 dark:border-purple-700' }
    case 'WALLBOX_OCPP':
    case 'WALLBOX_GOE':         return { label: 'Wallbox',          icon: HomeIcon,          classes: 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-700' }
    case 'SMARTCAR_LIVE':       return { label: 'AutoSync',         icon: BoltIcon,          classes: 'bg-teal-50 dark:bg-teal-900/30 text-teal-700 dark:text-teal-400 border border-teal-200 dark:border-teal-700' }
    default:                    return null
  }
}
