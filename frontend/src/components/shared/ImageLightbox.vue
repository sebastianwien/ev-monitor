<template>
  <Teleport to="body">
    <Transition name="lightbox-fade">
      <div
        v-if="isOpen && current"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 sm:p-8"
        role="dialog"
        aria-modal="true"
        :aria-label="current.alt"
        @click.self="close"
        @touchstart.passive="onTouchStart"
        @touchend.passive="onTouchEnd"
      >
        <!-- Close -->
        <button
          ref="closeBtn"
          type="button"
          @click="close"
          :aria-label="t('common.close')"
          class="absolute top-4 right-4 sm:top-6 sm:right-6 p-2 rounded-full bg-white/10 hover:bg-white/20 text-white transition"
        >
          <XMarkIcon class="h-7 w-7" />
        </button>

        <!-- Prev -->
        <button
          v-if="hasMultiple"
          type="button"
          @click.stop="prev"
          :aria-label="t('lightbox.prev')"
          class="absolute left-2 sm:left-4 top-1/2 -translate-y-1/2 p-2 sm:p-3 rounded-full bg-white/10 hover:bg-white/20 text-white transition"
        >
          <ChevronLeftIcon class="h-7 w-7 sm:h-8 sm:w-8" />
        </button>

        <!-- Image -->
        <img
          :src="current.src"
          :alt="current.alt"
          class="max-h-[88vh] max-w-[92vw] object-contain rounded-lg shadow-2xl select-none"
          draggable="false"
          @click.stop
        />

        <!-- Next -->
        <button
          v-if="hasMultiple"
          type="button"
          @click.stop="next"
          :aria-label="t('lightbox.next')"
          class="absolute right-2 sm:right-4 top-1/2 -translate-y-1/2 p-2 sm:p-3 rounded-full bg-white/10 hover:bg-white/20 text-white transition"
        >
          <ChevronRightIcon class="h-7 w-7 sm:h-8 sm:w-8" />
        </button>

        <!-- Counter -->
        <div
          v-if="hasMultiple"
          class="absolute bottom-5 left-1/2 -translate-x-1/2 text-white/90 text-sm font-medium tabular-nums bg-black/40 rounded-full px-3 py-1"
        >
          {{ index + 1 }} / {{ images.length }}
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { XMarkIcon, ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLightbox } from '../../composables/useLightbox'

const { t } = useI18n()
const { isOpen, images, index, current, hasMultiple, close, next, prev } = useLightbox()

const closeBtn = ref<HTMLButtonElement | null>(null)
let prevOverflow = ''
let touchStartX = 0

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowRight') next()
  else if (e.key === 'ArrowLeft') prev()
}

function onTouchStart(e: TouchEvent) {
  touchStartX = e.changedTouches[0].clientX
}
function onTouchEnd(e: TouchEvent) {
  const dx = e.changedTouches[0].clientX - touchStartX
  if (Math.abs(dx) > 40) dx < 0 ? next() : prev()
}

// Body-Scroll sperren + globale Tasten nur solange offen, damit im geschlossenen
// Zustand keine Listener/Locks haengen bleiben (kein Leak).
watch(isOpen, (open) => {
  if (open) {
    prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown)
    nextTick(() => closeBtn.value?.focus())
  } else {
    document.body.style.overflow = prevOverflow
    window.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = prevOverflow
})
</script>

<style scoped>
.lightbox-fade-enter-active,
.lightbox-fade-leave-active {
  transition: opacity 200ms ease;
}
.lightbox-fade-enter-from,
.lightbox-fade-leave-to {
  opacity: 0;
}
</style>
