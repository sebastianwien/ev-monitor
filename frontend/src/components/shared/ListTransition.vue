<script setup lang="ts">
/**
 * Liste, deren Einträge sich ändern (Suchtreffer): neue blenden leicht versetzt ein, wegfallende blenden aus, die
 * übrigen rutschen weich an ihren neuen Platz (FLIP über TransitionGroup). Auf- und Zuklappen ganzer Abschnitte
 * bleibt Collapse; dies ist für Einträge, die kommen und gehen. Einträge tragen `data-index` für den Versatz.
 */
const STAGGER_MS = 40
const MAX_STAGGER = 5
const onBeforeEnter = (el: Element) => {
  const i = Number((el as HTMLElement).dataset.index ?? 0)
  ;(el as HTMLElement).style.transitionDelay = `${Math.min(i, MAX_STAGGER) * STAGGER_MS}ms`
}
const onAfterEnter = (el: Element) => { (el as HTMLElement).style.transitionDelay = '' }
</script>

<template>
  <!-- Wegfallende Einträge absolut: sie geben ihren Platz sofort frei, die übrigen rutschen per move-class nach -->
  <TransitionGroup tag="div"
    enter-active-class="transition duration-300 ease-out motion-reduce:transition-none" enter-from-class="opacity-0 -translate-y-1"
    leave-active-class="absolute inset-x-0 transition duration-200 ease-in motion-reduce:transition-none" leave-to-class="opacity-0"
    move-class="transition-transform duration-300 ease-out motion-reduce:transition-none"
    class="relative" @before-enter="onBeforeEnter" @after-enter="onAfterEnter">
    <slot />
  </TransitionGroup>
</template>
