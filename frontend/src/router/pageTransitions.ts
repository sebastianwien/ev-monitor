import type { RouteLocationNormalized, RouteRecordNameGeneric, Router } from 'vue-router'

/**
 * Seitenwechsel zwischen /modelle, Markenseite, Vergleich und Modellseite (alle Märkte).
 *
 * Die Links dorthin sind RouterLinks, die App lädt also nicht neu. Der Wechsel läuft als
 * View Transition: die alte Seite bleibt stehen, bis die neue fertig ist, dann blendet sie
 * über. Tiefer (Liste -> Modellseite) kommt die neue Seite ein Stück von rechts, zurück von
 * links. Ohne API oder mit prefers-reduced-motion wechselt die Seite sofort.
 */

const MODELS_ROUTE = /^public-(models-list|models-compare|brand|model)(-[a-z]{2})?$/

/** Liste oben, Marke und Vergleich darunter, die Modellseite am tiefsten */
const DEPTH: Record<string, number> = { 'models-list': 0, brand: 1, 'models-compare': 1, model: 2 }

export function isModelsRoute(name: RouteRecordNameGeneric | null | undefined): boolean {
  return typeof name === 'string' && MODELS_ROUTE.test(name)
}

function depth(name: string): number {
  return DEPTH[name.match(MODELS_ROUTE)![1]]
}

export type NavDirection = 'forward' | 'back'

export function navDirection(from: string, to: string): NavDirection | undefined {
  const d = depth(to) - depth(from)
  return d > 0 ? 'forward' : d < 0 ? 'back' : undefined
}

/** Spinner und Skelette der Modellseiten tragen data-page-loading */
const LOADING_SELECTOR = '[data-page-loading]'

/**
 * Die neue Seite steht: ihre View ist gemountet (mode="out-in" hängt sie erst nach der alten
 * ein) und zeigt keinen Spinner mehr.
 */
function pageReady(to: RouteLocationNormalized, from: RouteLocationNormalized): () => boolean {
  const record = to.matched[to.matched.length - 1]
  const previous = from.matched[from.matched.length - 1]?.instances.default
  return () => {
    const view = record?.instances.default
    return !!view && view !== previous && !document.querySelector(LOADING_SELECTOR)
  }
}

/**
 * Wartet, bis die neue Seite steht, höchstens maxMs. Kommen die Daten aus dem Cache, sind
 * das zwei, drei Frames; sonst bleibt die alte Seite kurz stehen, statt einen Spinner
 * aufblitzen zu lassen.
 */
async function waitUntil(ready: () => boolean, maxMs: number): Promise<void> {
  const until = performance.now() + maxMs
  while (!ready() && performance.now() < until) {
    // kein requestAnimationFrame: das ruht während der View Transition
    await new Promise(resolve => setTimeout(resolve, 16))
  }
}

/** Zurück an die alte Stelle, Anker unter den Kopf, sonst nach oben; Liste und Anker erst, wenn die Seite steht */
export async function modelsScrollPosition(to: RouteLocationNormalized, from: RouteLocationNormalized, savedPosition: { left: number, top: number } | null) {
  if (!savedPosition && !to.hash) return { top: 0 }
  await waitUntil(pageReady(to, from), 1500)
  if (savedPosition) return savedPosition
  // scroll-mt-24 der Anker, unter dem sticky Kopf
  return document.querySelector(to.hash) ? { el: to.hash, top: 96 } : { top: 0 }
}

function canAnimate(): boolean {
  return typeof document.startViewTransition === 'function'
    && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Transition der router-view auf eine Modellseite: ohne CSS, die Bewegung macht die View
 * Transition. Das Leave hängt nicht an requestAnimationFrame (ruht, solange die View
 * Transition auf die neue Seite wartet), done kommt im nächsten Task: synchron bricht
 * Vue mitten im Patch ab.
 */
export const modelsPageSwap = {
  css: false,
  mode: 'out-in',
  onLeave: (_el: Element, done: () => void) => { setTimeout(done) },
} as const

export function installPageTransitions(router: Router): void {
  let finish: (() => void) | null = null

  router.beforeResolve((to, from) => {
    if (!isModelsRoute(to.name) || !isModelsRoute(from.name) || to.path === from.path || !canAnimate()) return
    const root = document.documentElement
    const direction = navDirection(from.name as string, to.name as string)
    if (direction) root.dataset.navDirection = direction
    return new Promise<void>(proceed => {
      const transition = document.startViewTransition(() => new Promise<void>(done => {
        finish = done
        // falls die Navigation hängt: nie länger als eine Sekunde einfrieren
        setTimeout(done, 1000)
        proceed()
      }))
      transition.finished.finally(() => { delete root.dataset.navDirection })
    })
  })

  router.afterEach(async (to, from, failure) => {
    const done = finish
    if (!done) return
    finish = null
    if (!failure) await waitUntil(pageReady(to, from), 400)
    done()
  })
}
