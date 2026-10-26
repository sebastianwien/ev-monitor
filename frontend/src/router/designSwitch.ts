import type { RouteLocationNormalized } from 'vue-router'
import { isModelsRoute } from './pageTransitions'

/**
 * Story-Opt-in ist die Weiche zwischen altem und neuem Design: Übersicht statt Dashboard, und die Seiten des
 * eingeloggten Bereichs (meta.workspaceFrame) stehen im Kopf des neuen Designs (meta.publicNav) statt im App-Chrome.
 * Gibt das Umleitungsziel zurück, sonst undefined. Die Modellseiten tragen publicNav fest (öffentliche Seiten).
 */
export function applyDesignSwitch(to: Pick<RouteLocationNormalized, 'path' | 'meta'>, storyDesign: boolean): string | undefined {
  if (to.path === '/dashboard' && storyDesign) return '/story'
  if (to.path === '/story' && !storyDesign) return '/dashboard'
  if (to.meta.workspaceFrame && to.meta.requiresAuth) to.meta.publicNav = storyDesign
  return undefined
}

/**
 * Liegt das Gerüst WorkspaceFrame (Kopf mit Reitern und Ticker) über der Route? Eingeloggt und im Kopf des neuen
 * Designs: die Bereiche der App und die Modellseiten (dort für alle Eingeloggten, damit der Kopf beim Wechsel stehen bleibt).
 */
export function showsWorkspaceFrame(route: Pick<RouteLocationNormalized, 'name' | 'meta'>, authenticated: boolean): boolean {
  return authenticated && !!route.meta.publicNav && (!!route.meta.workspaceFrame || isModelsRoute(route.name))
}
