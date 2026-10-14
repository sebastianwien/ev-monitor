<template>
  <!-- Phone only: a slim bar at the bottom of the screen; it slides up from below and back down -->
  <Teleport to="body">
    <Transition name="bar">
      <div v-if="shown" class="fixed inset-x-0 z-40 px-3 md:hidden" :class="signedIn ? 'bottom-[calc(env(safe-area-inset-bottom)+68px)]' : 'bottom-[calc(env(safe-area-inset-bottom)+12px)]'" data-testid="unlock-bar">
        <button type="button" class="flex min-h-12 w-full items-center justify-center gap-2 rounded-2xl bg-gray-900 px-4 text-[15px] font-bold text-white shadow-lg shadow-gray-900/25 dark:bg-gray-100 dark:text-gray-900"
                @click="emit('press')">
          <LockOpenIcon class="h-5 w-5 text-green-400 dark:text-green-600" aria-hidden="true" />
          {{ label }}
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LockOpenIcon } from '@heroicons/vue/24/outline'

/**
 * The unlock bar of the result box. It waits until the reader is on the way to the answers (the
 * entry heading stands above it, the box was seen, or a slider moved), so it never covers the page
 * on arrival; then it shows while the box's own button is out of view, as long as `active`.
 */
const props = defineProps<{
  label: string
  /** signed in, the bar rides above the footer bar */
  signedIn: boolean
  /** false once the answers are open */
  active: boolean
  /** the result box; seeing it counts as being on the way */
  box: HTMLElement | null
  /** the box's own button (or sign-in); while it is in view the bar gives way */
  action: HTMLElement | null
  /** the heading the reader reaches first on the way to the answers */
  entry?: HTMLElement | null
  /** a slider moved */
  nudged: boolean
}>()
const emit = defineEmits<{ press: [] }>()

const engaged = ref(false)
const actionVisible = ref(true)
const shown = computed(() => props.active && engaged.value && !actionVisible.value)

watch(() => props.nudged, n => { if (n) engaged.value = true }, { immediate: true })

const supported = typeof IntersectionObserver !== 'undefined'
const observer = supported ? new IntersectionObserver(entries => {
  for (const e of entries) {
    if (e.target === props.box && e.isIntersecting) engaged.value = true
    if (e.target === props.action) actionVisible.value = e.isIntersecting
  }
}) : null
/** Where the bar's top edge sits above the bottom of the screen: 12 px margin plus the 48 px bar, signed in 56 px more.
    The heading counts once it stands wholly above that edge, or has already passed out above. */
const entryObserver = supported ? new IntersectionObserver(entries => {
  for (const e of entries) if (e.isIntersecting || e.boundingClientRect.bottom <= 0) engaged.value = true
}, { rootMargin: `0px 0px -${props.signedIn ? 116 : 60}px 0px`, threshold: 1 }) : null

/** The elements arrive after mount and change with the box's state: follow whichever is there */
function follow(o: IntersectionObserver | null, el: HTMLElement | null | undefined, old: HTMLElement | null | undefined) {
  if (old) o?.unobserve(old)
  if (el) o?.observe(el)
}
watch(() => props.box, (el, old) => follow(observer, el, old), { immediate: true })
watch(() => props.entry, (el, old) => follow(entryObserver, el, old), { immediate: true })
watch(() => props.action, (el, old) => {
  follow(observer, el, old)
  if (!el && old) actionVisible.value = false
}, { immediate: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  entryObserver?.disconnect()
})
</script>

<style scoped>
/* Up from below the screen edge with a soft landing, down at a similar, calm pace.
   100% of its height plus the widest bottom offset (signed in it rides above the footer bar) */
.bar-enter-active { transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1); }
.bar-leave-active { transition: transform 0.35s cubic-bezier(0.4, 0, 0.6, 1); }
.bar-enter-from, .bar-leave-to { transform: translateY(calc(100% + 80px + env(safe-area-inset-bottom))); }
@media (prefers-reduced-motion: reduce) {
  .bar-enter-active, .bar-leave-active { transition: none; }
}
</style>
